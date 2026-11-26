package proyectod;

import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Equivale a validaciones.php. Todas reciben texto, igual que llegaba del formulario. */
final class Validaciones {
    private static final DateTimeFormatter F_FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter F_COMPACTA =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern FOLIO = Pattern.compile("^ACC-(\\d{8})-(\\d{3})$");

    private Validaciones() {}

    static boolean fechaReal(String t, DateTimeFormatter f) {
        try { LocalDate.parse(t, f); return true; } catch (DateTimeParseException e) { return false; }
    }

    static boolean esNumero(String x) {
        return x != null && x.trim().matches("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)");
    }

    static String errorFecha(String f) {
        if (f == null || !f.matches("\\d{4}-\\d{2}-\\d{2}") || !fechaReal(f, F_FECHA))
            return "Fecha inválida '" + f + "': usa AAAA-MM-DD (por ejemplo 2026-11-23).";
        return null;
    }

    static String errorHora(String h) {
        if (h == null || !h.matches("([01]\\d|2[0-3]):[0-5]\\d"))
            return "Hora inválida '" + h + "': usa HH:MM en 24 horas (por ejemplo 09:35).";
        return null;
    }

    static String errorFolio(String folio) {
        Matcher m = FOLIO.matcher(folio == null ? "" : folio);
        boolean ok = m.matches() && fechaReal(m.group(1), F_COMPACTA) && !m.group(2).equals("000");
        return ok ? null : "Folio inválido '" + folio + "': debe ser ACC-AAAAMMDD-### (ej. ACC-20261123-001) (RN-D08).";
    }

    static String errorGravedad(String g) {
        return (g != null && g.trim().matches("[1-5]")) ? null
                : "Gravedad inválida '" + g + "': debe ser un entero de 1 a 5 (RN-D07).";
    }

    private static void texto(List<String> errores, String campo, String valor, int max) {
        if (valor == null || valor.trim().isEmpty()) errores.add("El campo '" + campo + "' no puede ir vacío.");
        else if (valor.length() > max) errores.add("El campo '" + campo + "' es muy largo (máximo " + max + " caracteres).");
    }

    static List<String> validarAccidente(String folio, String fecha, String hora, String matricula,
                                         String laboratorio, String descripcion, String gravedad) {
        List<String> e = new ArrayList<>();
        for (String x : new String[]{errorFolio(folio), errorFecha(fecha), errorHora(hora), errorGravedad(gravedad)})
            if (x != null) e.add(x);
        texto(e, "matricula", matricula, 50);
        texto(e, "laboratorio", laboratorio, 100);
        texto(e, "descripcion", descripcion, 60000);
        return e;
    }

    static List<String> validarResultado(String folio, String matricula, String practica, String asistio,
                                         String calificacion, String adeudo, String monto) {
        List<String> e = new ArrayList<>();
        texto(e, "folio_inscripcion", folio, 30);
        texto(e, "matricula", matricula, 50);
        texto(e, "practica_id", practica, 50);
        if (!"S".equals(asistio) && !"N".equals(asistio)) e.add("'asistio' debe ser \"S\" o \"N\".");
        if (!"S".equals(adeudo) && !"N".equals(adeudo)) e.add("'adeudo_material' debe ser \"S\" o \"N\".");

        if (!esNumero(calificacion) || Double.parseDouble(calificacion.trim()) < 0 || Double.parseDouble(calificacion.trim()) > 10
                || Double.parseDouble(calificacion.trim()) % 1 != 0)
            e.add("La calificación debe ser un número entero de 0 a 10.");
        else if ("N".equals(asistio) && Double.parseDouble(calificacion.trim()) != 0)
            e.add("Si el alumno no asistió, la calificación es 0 (RN-D03).");

        if (!esNumero(monto) || Double.parseDouble(monto.trim()) < 0 || Double.parseDouble(monto.trim()) > 99999999)
            e.add("monto_adeudo debe ser un número entre 0 y 99,999,999.");
        else if ("N".equals(adeudo) && Double.parseDouble(monto.trim()) != 0)
            e.add("Sin adeudo (\"N\") el monto_adeudo debe ser 0 (RN-D06).");
        return e;
    }

    static List<String> validarPrestamo(String matricula, String claveMaterial, String descripcion, String fecha) {
        List<String> e = new ArrayList<>();
        texto(e, "matricula", matricula, 50);
        texto(e, "clave_material", claveMaterial, 20);
        texto(e, "descripcion", descripcion, 255);
        String f = errorFecha(fecha);
        if (f != null) e.add(f);
        return e;
    }

    static List<String> validarReservacion(String laboratorio, String claveEquipo, String fecha,
                                           String inicio, String fin, String matricula) {
        List<String> e = new ArrayList<>();
        texto(e, "laboratorio", laboratorio, 100);
        texto(e, "clave_equipo", claveEquipo, 30);
        texto(e, "matricula", matricula, 50);
        for (String x : new String[]{errorFecha(fecha), errorHora(inicio), errorHora(fin)}) if (x != null) e.add(x);
        if (errorHora(inicio) == null && errorHora(fin) == null && inicio.compareTo(fin) >= 0)
            e.add("La hora de fin debe ser posterior a la de inicio.");
        return e;
    }
}
