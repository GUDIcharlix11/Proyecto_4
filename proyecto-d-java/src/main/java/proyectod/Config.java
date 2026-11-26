package proyectod;

/** Configuración (equivale a las constantes de conexion.php). Se puede cambiar con variables de entorno. */
final class Config {
    static final String DB_SERVER = env("DB_SERVER", "jdbc:mysql://localhost:3306/");
    static final String DB_NAME   = env("DB_NAME", "proyecto_d_equipo4");
    static final String DB_USER   = env("DB_USER", "root");
    static final String DB_PASS   = env("DB_PASS", "");

    /** URL base del servicio de la API del profesor (sin /api/v1). */
    static final String API_BASE  = env("API_BASE", "https://servicios-proyecto-ed.vercel.app");
    static final String API_KEY   = env("API_KEY", "");

    private Config() {}

    private static String env(String clave, String defecto) {
        String v = System.getenv(clave);
        return v == null ? defecto : v;
    }
}
