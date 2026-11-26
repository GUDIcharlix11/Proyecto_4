package proyectod;

import java.util.LinkedHashMap;
import java.util.Map;

record Reservacion(String folioReservacion, String laboratorio, String claveEquipo, String fecha,
                   String horaInicio, String horaFin, String matricula) {
    Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("folio_reservacion", folioReservacion);
        m.put("laboratorio", laboratorio);
        m.put("clave_equipo", claveEquipo);
        m.put("fecha", fecha);
        m.put("hora_inicio", horaInicio);
        m.put("hora_fin", horaFin);
        m.put("matricula", matricula);
        return m;
    }
}
