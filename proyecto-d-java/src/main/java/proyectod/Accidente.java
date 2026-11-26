package proyectod;

import java.util.LinkedHashMap;
import java.util.Map;

record Accidente(String folio, String fecha, String hora, String matricula,
                 String laboratorio, String descripcion, int gravedad) {
    Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folio", folio);
        m.put("fecha", fecha);
        m.put("hora", hora);
        m.put("matricula", matricula);
        m.put("laboratorio", laboratorio);
        m.put("descripcion", descripcion);
        m.put("gravedad", gravedad);
        return m;
    }
}
