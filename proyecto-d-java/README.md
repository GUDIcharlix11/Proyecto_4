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
Abre la interfaz gráfica (ventanas Swing) con una pestaña por sección: Kits, Accidentes, Resultados, Préstamos, Reservaciones y API.

Para usar el menú de consola en lugar de las ventanas:
```
mvn compile exec:java -Dexec.args=consola
```

## API
Servidor del profesor: `https://servicios-proyecto-ed.vercel.app/api/v1` (ya es el valor por defecto de `API_BASE`; no pide clave). Especificación en `/docs`.
- Cada registro se publica con `POST` de un solo objeto (si la clave ya existe, lo sustituye). Nunca se manda `{"datos": [...]}` porque eso reemplaza la lista completa del servidor.
- `calificacion` debe ser entero (la API responde 422 con decimales).
- El folio de un accidente, préstamo (`PRE-###`) o reservación (`RES-###`) nuevo también toma en cuenta los folios que devuelve la API.
- Modo prueba / base: mientras el profesor tenga el módulo en prueba, los GET devuelven datos inventados y lo que publicas se guarda pero no se ve en la lectura. Al pasar a base, la lectura ya muestra lo publicado.

### Recursos internos del módulo D
- **materiales** (catálogo fijo, solo lectura): menú 4) API → 4) y al registrar un préstamo.
- **prestamos** (menú 5): registrar (valida que la clave exista en el catálogo y que haya existencia), marcar DEVUELTO y eliminar. Cada cambio se publica o se borra en la API.
- **reservaciones** (menú 6): reservar (hora de fin posterior a la de inicio y sin choque con otra reservación del mismo equipo, laboratorio y fecha) y cancelar. Cada cambio se publica o se borra en la API.
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
