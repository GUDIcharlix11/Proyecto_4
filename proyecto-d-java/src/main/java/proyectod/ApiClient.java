package proyectod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Cliente de la API del profesor (Api_Proyecto.txt). Rutas: /api/v1/{recurso} y /api/v1/{recurso}/{clave}.
 *
 * Lo que consume el módulo D:  inscritos_practicas (lo publica C).
 * Lo que publica el módulo D:  resultados_practicas (lo consume C) y reporte_accidentes (lo consume A = Equipo 1).
 * Internos del módulo D:       materiales (catálogo fijo, solo lectura), prestamos y reservaciones.
 */
final class ApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private ApiClient() {}

    private static HttpRequest.Builder base(String ruta) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(Config.API_BASE + ruta))
                .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        if (!Config.API_KEY.isEmpty()) b.header("Authorization", "Bearer " + Config.API_KEY);
        return b;
    }

    private static HttpResponse<String> enviar(HttpRequest r) {
        try {
            return HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("No pude conectar con la API (" + Config.API_BASE + "): " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la llamada a la API.");
        }
    }

    private static String ruta(String recurso) { return "/api/v1/" + recurso; }
    private static String ruta(String recurso, String clave) {
        return ruta(recurso) + "/" + URLEncoder.encode(clave, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** GET de la lista completa. Acepta [..] o {"datos": [..]}. */
    static JsonNode lista(String recurso) {
        HttpResponse<String> r = enviar(base(ruta(recurso)).GET().build());
        if (r.statusCode() != 200) throw new IllegalStateException("La API respondió " + r.statusCode() + " en " + ruta(recurso));
        try {
            JsonNode n = JSON.readTree(r.body());
            return n.isObject() && n.has("datos") ? n.get("datos") : n;
        } catch (IOException e) {
            throw new IllegalStateException("La API no devolvió un JSON válido.");
        }
    }

    /** true / false si la API contesta 200 / 404; null si no se pudo saber. */
    static Boolean existe(String recurso, String clave) {
        try {
            int s = enviar(base(ruta(recurso, clave)).GET().build()).statusCode();
            return s == 200 ? Boolean.TRUE : s == 404 ? Boolean.FALSE : null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /** GET de un registro; null si la API responde 404. */
    static JsonNode uno(String recurso, String clave) {
        HttpResponse<String> r = enviar(base(ruta(recurso, clave)).GET().build());
        if (r.statusCode() == 404) return null;
        if (r.statusCode() != 200) throw new IllegalStateException("La API respondió " + r.statusCode() + " en " + ruta(recurso, clave));
        try {
            return JSON.readTree(r.body());
        } catch (IOException e) {
            throw new IllegalStateException("La API no devolvió un JSON válido.");
        }
    }

    /** DELETE de un registro. Devuelve false si no estaba publicado (404). */
    static boolean borrar(String recurso, String clave) {
        HttpResponse<String> r = enviar(base(ruta(recurso, clave)).DELETE().build());
        if (r.statusCode() == 404) return false;
        if (r.statusCode() / 100 != 2)
            throw new IllegalStateException("La API no borró " + clave + " (" + r.statusCode() + "): " + r.body());
        return true;
    }

    /** Publica un registro: POST a la lista; si ya existe (409), PUT al recurso. Devuelve el código HTTP final. */
    static int publicar(String recurso, String clave, Map<String, Object> cuerpo) {
        String json;
        try {
            json = JSON.writeValueAsString(cuerpo);
        } catch (IOException e) {
            throw new IllegalStateException("No pude convertir el registro a JSON.");
        }
        HttpResponse<String> r = enviar(base(ruta(recurso)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
        if (r.statusCode() == 409) {
            r = enviar(base(ruta(recurso, clave)).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
        }
        if (r.statusCode() / 100 != 2)
            throw new IllegalStateException("La API rechazó " + clave + " (" + r.statusCode() + "): " + r.body());
        return r.statusCode();
    }
}
