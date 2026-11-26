package proyectod;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Préstamos de material del catálogo fijo (recurso interno "prestamos" del módulo D). */
final class PrestamoService {
    static final String ACTIVO = "ACTIVO", DEVUELTO = "DEVUELTO";
    private static final String COLS = "folio_prestamo, matricula, clave_material, descripcion, fecha, estado";

    private PrestamoService() {}

    private static Prestamo desde(Map<String, Object> f) {
        return new Prestamo(Db.s(f.get("folio_prestamo")), Db.s(f.get("matricula")), Db.s(f.get("clave_material")),
                Db.s(f.get("descripcion")), Db.s(f.get("fecha")), Db.s(f.get("estado")));
    }

    private static Prestamo desde(JsonNode n) {
        return new Prestamo(n.path("folio_prestamo").asText(""), n.path("matricula").asText(""),
                n.path("clave_material").asText(""), n.path("descripcion").asText(""),
                n.path("fecha").asText(""), n.path("estado").asText(""));
    }

    static List<Prestamo> todos() {
        List<Prestamo> l = new ArrayList<>();
        for (Map<String, Object> f : Db.consultar("SELECT " + COLS + " FROM prestamos ORDER BY folio_prestamo")) l.add(desde(f));
        return l;
    }

    static Prestamo obtener(String folio) {
        List<Map<String, Object>> filas = Db.consultar("SELECT " + COLS + " FROM prestamos WHERE folio_prestamo = ?", folio);
        if (filas.isEmpty()) throw new IllegalArgumentException("No existe el préstamo " + folio + ".");
        return desde(filas.get(0));
    }

    /** Préstamos locales más los publicados en la API (los locales ganan si se repite el folio). */
    private static Map<String, Prestamo> conocidos() {
        Map<String, Prestamo> m = new LinkedHashMap<>();
        try {
            for (JsonNode n : ApiClient.lista("prestamos")) { Prestamo p = desde(n); m.put(p.folioPrestamo(), p); }
        } catch (RuntimeException e) {
            // Sin API solo se usan los préstamos locales.
        }
        for (Prestamo p : todos()) m.put(p.folioPrestamo(), p);
        return m;
    }

    static String siguienteFolio(Collection<String> usados) {
        int max = 0;
        for (String f : usados)
            if (f.matches("PRE-\\d+")) max = Math.max(max, Integer.parseInt(f.substring(4)));
        return String.format("PRE-%03d", max + 1);
    }

    static Prestamo registrar(String matricula, String claveMaterial, String descripcion, String fecha) {
        matricula = matricula.trim(); descripcion = descripcion.trim(); fecha = fecha.trim();
        claveMaterial = claveMaterial.trim().toUpperCase();
        List<String> errores = Validaciones.validarPrestamo(matricula, claveMaterial, descripcion, fecha);
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));

        JsonNode material = ApiClient.uno("materiales", claveMaterial);
        if (material == null) throw new IllegalArgumentException("La clave " + claveMaterial + " no está en el catálogo de materiales.");
        Map<String, Prestamo> todos = conocidos();
        final String clave = claveMaterial;
        long activos = todos.values().stream().filter(p -> p.claveMaterial().equals(clave) && ACTIVO.equals(p.estado())).count();
        int existencia = material.path("existencia").asInt(0);
        if (activos >= existencia)
            throw new IllegalArgumentException("No hay existencia de " + claveMaterial + ": " + activos + " de " + existencia + " ya están prestados.");

        Prestamo p = new Prestamo(siguienteFolio(todos.keySet()), matricula, claveMaterial, descripcion, fecha, ACTIVO);
        Db.ejecutar("INSERT INTO prestamos (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?)",
                p.folioPrestamo(), p.matricula(), p.claveMaterial(), p.descripcion(), p.fecha(), p.estado());
        return p;
    }

    static Prestamo devolver(String folio) {
        Prestamo p = obtener(folio);
        if (DEVUELTO.equals(p.estado())) throw new IllegalArgumentException("El préstamo " + folio + " ya estaba devuelto.");
        Db.ejecutar("UPDATE prestamos SET estado = ? WHERE folio_prestamo = ?", DEVUELTO, p.folioPrestamo());
        return new Prestamo(p.folioPrestamo(), p.matricula(), p.claveMaterial(), p.descripcion(), p.fecha(), DEVUELTO);
    }

    static void eliminar(String folio) {
        obtener(folio);
        Db.ejecutar("DELETE FROM prestamos WHERE folio_prestamo = ?", folio);
    }
}
