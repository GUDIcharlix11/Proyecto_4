package proyectod;

import java.util.*;

/** Equivale a funciones_accidentes.php. */
final class AccidenteService {
    static final Set<String> CAMPOS_EDITABLES = Set.of("hora", "matricula", "laboratorio", "descripcion", "gravedad");
    private static final String COLS = "folio, fecha, hora, matricula, laboratorio, descripcion, gravedad";

    private AccidenteService() {}

    private static Accidente desde(Map<String, Object> f) {
        return new Accidente(Db.s(f.get("folio")), Db.s(f.get("fecha")), Db.s(f.get("hora")), Db.s(f.get("matricula")),
                Db.s(f.get("laboratorio")), Db.s(f.get("descripcion")), Db.i(f.get("gravedad")));
    }

    static List<Accidente> todos() {
        List<Accidente> l = new ArrayList<>();
        for (Map<String, Object> f : Db.consultar("SELECT " + COLS + " FROM accidentes ORDER BY folio")) l.add(desde(f));
        return l;
    }

    static Accidente obtener(String folio) {
        List<Map<String, Object>> filas = Db.consultar("SELECT " + COLS + " FROM accidentes WHERE folio = ?", folio);
        if (filas.isEmpty()) throw new IllegalArgumentException("No existe el accidente " + folio + ".");
        return desde(filas.get(0));
    }

    static String siguienteFolio(String fecha) {
        String prefijo = "ACC-" + fecha.replace("-", "") + "-";
        int max = 0;
        for (Map<String, Object> f : Db.consultar("SELECT folio FROM accidentes WHERE folio LIKE ?", prefijo + "%")) {
            String folio = Db.s(f.get("folio"));
            max = Math.max(max, Integer.parseInt(folio.substring(folio.length() - 3)));
        }
        int n = max + 1;
        if (n > 999) throw new IllegalArgumentException("Ya se usaron los 999 folios del " + fecha + ".");
        return prefijo + String.format("%03d", n);
    }

    static Accidente reportar(String fecha, String hora, String matricula, String laboratorio,
                              String descripcion, String gravedad) {
        fecha = fecha == null ? "" : fecha.trim();
        String e = Validaciones.errorFecha(fecha);
        if (e != null) throw new IllegalArgumentException(e);
        String folio = siguienteFolio(fecha);
        hora = hora.trim(); matricula = matricula.trim(); descripcion = descripcion.trim(); gravedad = gravedad.trim();
        laboratorio = laboratorio.trim().toUpperCase();
        List<String> errores = Validaciones.validarAccidente(folio, fecha, hora, matricula, laboratorio, descripcion, gravedad);
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));
        Accidente a = new Accidente(folio, fecha, hora, matricula, laboratorio, descripcion, Integer.parseInt(gravedad));
        Db.ejecutar("INSERT INTO accidentes (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                a.folio(), a.fecha(), a.hora(), a.matricula(), a.laboratorio(), a.descripcion(), a.gravedad());
        return a;
    }

    static Accidente modificar(String folio, Map<String, String> cambios) {
        Set<String> invalidos = new TreeSet<>(cambios.keySet());
        invalidos.removeAll(CAMPOS_EDITABLES);
        if (!invalidos.isEmpty()) throw new IllegalArgumentException("No se puede cambiar: " + String.join(", ", invalidos) + ".");
        Accidente a = obtener(folio);
        String hora = cambios.getOrDefault("hora", a.hora()).trim();
        String matricula = cambios.getOrDefault("matricula", a.matricula()).trim();
        String laboratorio = cambios.getOrDefault("laboratorio", a.laboratorio()).trim().toUpperCase();
        String descripcion = cambios.getOrDefault("descripcion", a.descripcion()).trim();
        String gravedad = cambios.getOrDefault("gravedad", String.valueOf(a.gravedad())).trim();
        List<String> errores = Validaciones.validarAccidente(a.folio(), a.fecha(), hora, matricula, laboratorio, descripcion, gravedad);
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));
        Accidente n = new Accidente(a.folio(), a.fecha(), hora, matricula, laboratorio, descripcion, Integer.parseInt(gravedad));
        Db.ejecutar("UPDATE accidentes SET hora = ?, matricula = ?, laboratorio = ?, descripcion = ?, gravedad = ? WHERE folio = ?",
                n.hora(), n.matricula(), n.laboratorio(), n.descripcion(), n.gravedad(), n.folio());
        return n;
    }

    static void eliminar(String folio) {
        obtener(folio);
        Db.ejecutar("DELETE FROM accidentes WHERE folio = ?", folio);
    }
}
