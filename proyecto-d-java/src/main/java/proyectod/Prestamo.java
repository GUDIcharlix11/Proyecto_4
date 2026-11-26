package proyectod;

import java.util.LinkedHashMap;
import java.util.Map;

record Prestamo(String folioPrestamo, String matricula, String claveMaterial, String descripcion,
                String fecha, String estado) {
    Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folio_prestamo", folioPrestamo);
        m.put("matricula", matricula);
        m.put("clave_material", claveMaterial);
        m.put("descripcion", descripcion);
        m.put("fecha", fecha);
        m.put("estado", estado);
        return m;
    }
}
