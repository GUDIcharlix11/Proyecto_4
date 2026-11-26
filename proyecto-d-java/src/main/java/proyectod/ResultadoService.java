package proyectod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Equivale a funciones_resultados.php. */
final class ResultadoService {
    private static final String COLS = "folio_inscripcion, matricula, practica_id, asistio, calificacion, adeudo_material, monto_adeudo";
    private static final ObjectMapper JSON = new ObjectMapper();

    private ResultadoService() {}

    private static Resultado desde(Map<String, Object> f) {
        return new Resultado(Db.s(f.get("folio_inscripcion")), Db.s(f.get("matricula")), Db.s(f.get("practica_id")),
                Db.s(f.get("asistio")), Db.d(f.get("calificacion")), Db.s(f.get("adeudo_material")), Db.d(f.get("monto_adeudo")));
    }

    static List<Resultado> todos() {
        List<Resultado> l = new ArrayList<>();
        for (Map<String, Object> f : Db.consultar("SELECT " + COLS + " FROM resultados ORDER BY registrado, folio_inscripcion"))
            l.add(desde(f));
        return l;
    }

    private static String limpio(String t) { return t == null ? null : t.trim(); }
    private static String sn(String t)     { return t == null ? null : t.trim().toUpperCase(); }

    /** Inserta o actualiza. Devuelve "guardado" o "actualizado". */
    static String guardar(String folio, String matricula, String practica, String asistio,
                          String calificacion, String adeudo, String monto) {
        folio = limpio(folio); matricula = limpio(matricula); practica = limpio(practica);
        asistio = sn(asistio); adeudo = sn(adeudo); calificacion = limpio(calificacion); monto = limpio(monto);
        List<String> errores = Validaciones.validarResultado(folio, matricula, practica, asistio, calificacion, adeudo, monto);
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));
        double calif = Double.parseDouble(calificacion), m = Double.parseDouble(monto);
        boolean existe = !Db.consultar("SELECT 1 FROM resultados WHERE folio_inscripcion = ?", folio).isEmpty();
        if (existe) {
            Db.ejecutar("UPDATE resultados SET matricula = ?, practica_id = ?, asistio = ?, calificacion = ?, "
                      + "adeudo_material = ?, monto_adeudo = ? WHERE folio_inscripcion = ?",
                    matricula, practica, asistio, calif, adeudo, m, folio);
            return "actualizado";
        }
        Db.ejecutar("INSERT INTO resultados (" + COLS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                folio, matricula, practica, asistio, calif, adeudo, m);
        return "guardado";
    }

    static void eliminar(String folio) {
        if (Db.consultar("SELECT 1 FROM resultados WHERE folio_inscripcion = ?", folio).isEmpty())
            throw new IllegalArgumentException("No existe el resultado " + folio + ".");
        Db.ejecutar("DELETE FROM resultados WHERE folio_inscripcion = ?", folio);
    }

    private static String txt(JsonNode n, String k) {
        return n.has(k) && !n.get(k).isNull() ? n.get(k).asText() : null;
    }

    /** Carga una lista JSON o {"datos": [...]}. Devuelve un resumen con lo guardado y lo rechazado. */
    static String importar(String json) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ese JSON no es válido: " + e.getOriginalMessage());
        }
        if (raiz != null && raiz.isObject() && raiz.has("datos")) raiz = raiz.get("datos");
        if (raiz == null || !raiz.isArray())
            throw new IllegalArgumentException("Debe ser una lista de resultados o {\"datos\": [...]}.");
        int guardados = 0, i = 0;
        List<String> mensajes = new ArrayList<>();
        for (JsonNode n : raiz) {
            i++;
            try {
                guardar(txt(n, "folio_inscripcion"), txt(n, "matricula"), txt(n, "practica_id"), txt(n, "asistio"),
                        txt(n, "calificacion"), txt(n, "adeudo_material"), txt(n, "monto_adeudo"));
                guardados++;
            } catch (IllegalArgumentException e) {
                mensajes.add("Resultado #" + i + ": " + e.getMessage());
            }
        }
        return "Resultados: " + guardados + " guardados, " + mensajes.size() + " rechazados."
                + (mensajes.isEmpty() ? "" : " " + String.join(" | ", mensajes));
    }
}
