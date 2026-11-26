# Proyecto D · Laboratorios y talleres (Equipo 4) — versión Java

Aplicación de consola en Java 17 + MySQL (JDBC) con tres secciones: **Kits** (recursivos), **Accidentes** y **Resultados de práctica**, más un cliente HTTP para la API del proyecto integrador.

## Requisitos
- JDK 17+, Maven 3.8+, MySQL (por ejemplo el de XAMPP) encendido.

## Configuración
Por defecto usa `root` sin contraseña en `localhost:3306` y crea sola la base `proyecto_d_equipo4` con sus tablas. Se puede cambiar con variables de entorno: `DB_SERVER`, `DB_NAME`, `DB_USER`, `DB_PASS`, `API_BASE`, `API_KEY`.
(`schema.sql` es el mismo script de la versión PHP, por si quieres cargarlo a mano con datos de ejemplo.)

## Ejecutar
```
mvn compile exec:java
```

## API
- **Consume**: `inscritos_practicas` (publica el módulo C) para validar los folios al capturar resultados.
- **Publica**: `reporte_accidentes` (lo consume el módulo A / Equipo 1) y `resultados_practicas` (lo consume el módulo C).

## Estructura
| PHP | Java |
|---|---|
| conexion.php | Config.java, Db.java |
| validaciones.php | Validaciones.java |
| funciones_kits.php / kits.php | Kit.java, KitService.java, menú Kits |
| funciones_accidentes.php / accidentes.php | Accidente.java, AccidenteService.java, menú Accidentes |
| funciones_resultados.php / resultados.php | Resultado.java, ResultadoService.java, menú Resultados |
| (Api_Proyecto.txt) | ApiClient.java |
