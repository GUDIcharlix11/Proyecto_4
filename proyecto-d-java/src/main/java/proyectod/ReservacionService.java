package proyectod;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Reservaciones de equipo de laboratorio (recurso interno "reservaciones" del módulo D). */
final class ReservacionService {
    private static final String COLS = "folio_reservacion, laboratorio, clave_equipo, fecha, hora_inicio, hora_fin, matricula";

    private ReservacionService() {}

    private static Reservacion desde(Map<String, Object> f) {
        return new Reservacion(Db.s(f.get("folio_reservacion")), Db.s(f.get("laboratorio")), Db.s(f.get("clave_equipo")),
                Db.s(f.get("fecha")), Db.s(f.get("hora_inicio")), Db.s(f.get("hora_fin")), Db.s(f.get("matricula")));
    }

    private static Reservacion desde(JsonNode n) {
        return new Reservacion(n.path("folio_reservacion").asText(""), n.path("laboratorio").asText(""),
                n.path("clave_equipo").asText(""), n.path("fecha").asText(""), n.path("hora_inicio").asText(""),
                n.path("hora_fin").asText(""), n.path("matricula").asText(""));
    }

    static List<Reservacion> todas() {
        List<Reservacion> l = new ArrayList<>();
        for (Map<String, Object> f : Db.consultar("SELECT " + COLS + " FROM reservaciones ORDER BY fecha, hora_inicio")) l.add(desde(f));
        return l;
    }

    static Reservacion obtener(String folio) {
        List<Map<String, Object>> filas = Db.consultar("SELECT " + COLS + " FROM reservaciones WHERE folio_reservacion = ?", folio);
        if (filas.isEmpty()) throw new IllegalArgumentException("No existe la reservación " + folio + ".");
        return desde(filas.get(0));
    }

    /** Reservaciones locales más las publicadas en la API (las locales ganan si se repite el folio). */
    private static Map<String, Reservacion> conocidas() {
        Map<String, Reservacion> m = new LinkedHashMap<>();
        try {
            for (JsonNode n : ApiClient.lista("reservaciones")) { Reservacion r = desde(n); m.put(r.folioReservacion(), r); }
        } catch (RuntimeException e) {
            // Sin API solo se usan las reservaciones locales.
        }
        for (Reservacion r : todas()) m.put(r.folioReservacion(), r);
        return m;
    }

    static String siguienteFolio(Collection<String> usados) {
        int max = 0;
        for (String f : usados)
            if (f.matches("RES-\\d+")) max = Math.max(max, Integer.parseInt(f.substring(4)));
        return String.format("RES-%03d", max + 1);
    }

    static Reservacion reservar(String laboratorio, String claveEquipo, String fecha, String inicio, String fin, String matricula) {
        laboratorio = laboratorio.trim().toUpperCase(); claveEquipo = claveEquipo.trim().toUpperCase();
        fecha = fecha.trim(); inicio = inicio.trim(); fin = fin.trim(); matricula = matricula.trim();
        List<String> errores = Validaciones.validarReservacion(laboratorio, claveEquipo, fecha, inicio, fin, matricula);
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));

        Map<String, Reservacion> todas = conocidas();
        for (Reservacion r : todas.values())
            if (r.laboratorio().equals(laboratorio) && r.claveEquipo().equals(claveEquipo) && r.fecha().equals(fecha)
                    && inicio.compareTo(r.horaFin()) < 0 && r.horaInicio().compareTo(fin) < 0)
                throw new IllegalArgumentException("El equipo " + claveEquipo + " ya está reservado de " + r.horaInicio()
                        + " a " + r.horaFin() + " (" + r.folioReservacion() + ").");

        Reservacion r = new Reservacion(siguienteFolio(todas.keySet()), laboratorio, claveEquipo, fecha, inicio, fin, matricula);
        Db.ejecutar("INSERT INTO reservaciones (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                r.folioReservacion(), r.laboratorio(), r.claveEquipo(), r.fecha(), r.horaInicio(), r.horaFin(), r.matricula());
        return r;
    }

    static void cancelar(String folio) {
        obtener(folio);
        Db.ejecutar("DELETE FROM reservaciones WHERE folio_reservacion = ?", folio);
    }
}
