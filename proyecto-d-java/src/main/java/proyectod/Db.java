package proyectod;

import java.sql.*;
import java.util.*;

/** Equivale a conexion.php: crea la BD y las tablas, y ofrece consultar()/ejecutar(). */
final class Db {
    static class DbError extends RuntimeException {
        DbError(String msg, Throwable causa) { super("Error de base de datos: " + msg, causa); }
    }

    private static Connection con;

    private static final String FIN = ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    private static final String[] TABLAS = {
        """
        CREATE TABLE IF NOT EXISTS kits (
          id INT AUTO_INCREMENT PRIMARY KEY,
          nombre VARCHAR(100) NOT NULL,
          padre_id INT NULL,
          CONSTRAINT fk_kit_padre FOREIGN KEY (padre_id) REFERENCES kits(id) ON DELETE CASCADE
        """ + FIN,
        """
        CREATE TABLE IF NOT EXISTS materiales (
          id INT AUTO_INCREMENT PRIMARY KEY,
          kit_id INT NOT NULL,
          clave VARCHAR(20) NOT NULL,
          nombre VARCHAR(100) NOT NULL,
          cantidad INT NOT NULL,
          costo DECIMAL(10,2) NOT NULL DEFAULT 0,
          CONSTRAINT fk_material_kit FOREIGN KEY (kit_id) REFERENCES kits(id) ON DELETE CASCADE
        """ + FIN,
        """
        CREATE TABLE IF NOT EXISTS accidentes (
          folio VARCHAR(20) PRIMARY KEY,
          fecha DATE NOT NULL,
          hora CHAR(5) NOT NULL,
          matricula VARCHAR(50) NOT NULL,
          laboratorio VARCHAR(100) NOT NULL,
          descripcion TEXT NOT NULL,
          gravedad TINYINT NOT NULL,
          CHECK (gravedad BETWEEN 1 AND 5)
        """ + FIN,
        """
        CREATE TABLE IF NOT EXISTS resultados (
          folio_inscripcion VARCHAR(30) PRIMARY KEY,
          matricula VARCHAR(50) NOT NULL,
          practica_id VARCHAR(50) NOT NULL,
          asistio CHAR(1) NOT NULL,
          calificacion DECIMAL(4,2) NOT NULL,
          adeudo_material CHAR(1) NOT NULL,
          monto_adeudo DECIMAL(10,2) NOT NULL DEFAULT 0,
          registrado TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CHECK (asistio IN ('S','N')),
          CHECK (adeudo_material IN ('S','N')),
          CHECK (calificacion BETWEEN 0 AND 10)
        """ + FIN,
        """
        CREATE TABLE IF NOT EXISTS prestamos (
          folio_prestamo VARCHAR(20) PRIMARY KEY,
          matricula VARCHAR(50) NOT NULL,
          clave_material VARCHAR(20) NOT NULL,
          descripcion VARCHAR(255) NOT NULL,
          fecha DATE NOT NULL,
          estado VARCHAR(10) NOT NULL,
          CHECK (estado IN ('ACTIVO','DEVUELTO'))
        """ + FIN,
        """
        CREATE TABLE IF NOT EXISTS reservaciones (
          folio_reservacion VARCHAR(20) PRIMARY KEY,
          laboratorio VARCHAR(100) NOT NULL,
          clave_equipo VARCHAR(30) NOT NULL,
          fecha DATE NOT NULL,
          hora_inicio CHAR(5) NOT NULL,
          hora_fin CHAR(5) NOT NULL,
          matricula VARCHAR(50) NOT NULL,
          CHECK (hora_inicio < hora_fin)
        """ + FIN
    };

    private Db() {}

    static synchronized Connection get() throws SQLException {
        if (con != null && !con.isClosed()) return con;
        try (Connection tmp = DriverManager.getConnection(Config.DB_SERVER, Config.DB_USER, Config.DB_PASS);
             Statement st = tmp.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + Config.DB_NAME
                     + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        con = DriverManager.getConnection(Config.DB_SERVER + Config.DB_NAME
                + "?useUnicode=true&characterEncoding=utf8", Config.DB_USER, Config.DB_PASS);
        try (Statement st = con.createStatement()) {
            for (String sql : TABLAS) st.execute(sql);
        }
        return con;
    }

    /** SELECT con parámetros; devuelve filas como mapas columna -> valor. */
    static List<Map<String, Object>> consultar(String sql, Object... params) {
        try (PreparedStatement ps = get().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            List<Map<String, Object>> filas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    for (int c = 1; c <= md.getColumnCount(); c++) f.put(md.getColumnLabel(c), rs.getObject(c));
                    filas.add(f);
                }
            }
            return filas;
        } catch (SQLException e) {
            throw new DbError(e.getMessage(), e);
        }
    }

    /** INSERT / UPDATE / DELETE con parámetros. */
    static int ejecutar(String sql, Object... params) {
        try (PreparedStatement ps = get().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbError(e.getMessage(), e);
        }
    }

    static String s(Object o) { return o == null ? "" : String.valueOf(o); }
    static int i(Object o)    { return ((Number) o).intValue(); }
    static double d(Object o) { return ((Number) o).doubleValue(); }
}
