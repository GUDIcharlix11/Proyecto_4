package proyectod;

import java.util.LinkedHashMap;
import java.util.Map;

record Resultado(String folioInscripcion, String matricula, String practicaId, String asistio,
                 double calificacion, String adeudoMaterial, double montoAdeudo) {
    Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folio_inscripcion", folioInscripcion);
        m.put("matricula", matricula);
        m.put("practica_id", practicaId);
        m.put("asistio", asistio);
        m.put("calificacion", (int) Math.round(calificacion));
        m.put("adeudo_material", adeudoMaterial);
        m.put("monto_adeudo", montoAdeudo);
        return m;
    }
}
