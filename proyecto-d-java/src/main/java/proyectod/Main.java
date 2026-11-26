package proyectod;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;

/** Menú de consola. Reemplaza kits.php, accidentes.php y resultados.php. */
public final class Main {
    private static final Scanner IN = new Scanner(System.in);

    private static String leer(String p) {
        System.out.print(p + ": ");
        if (!IN.hasNextLine()) {
            System.out.println("\nSe cerró la entrada. Hasta luego.");
            System.exit(0);
        }
        return IN.nextLine().trim();
    }

    private static String leer(String p, String actual) {
        String t = leer(p + " [" + actual + "]");
        return t.isEmpty() ? actual : t;
    }

    private static void seguro(Runnable r) {
        try { r.run(); }
        catch (RuntimeException e) { System.out.println("✗ " + e.getMessage()); }
    }

    public static void main(String[] args) {
        try {
            Db.get();
        } catch (Exception e) {
            System.out.println("No pude conectar con MySQL: " + e.getMessage());
            System.out.println("Revisa que MySQL esté encendido y los datos de Config.java / variables de entorno.");
            return;
        }
        System.out.println("Base de datos: " + Config.DB_NAME + " · conectada");
        while (true) {
            System.out.println("\n=== Laboratorios y talleres · Equipo 4 ===");
            System.out.println("1) Kits\n2) Accidentes\n3) Resultados de práctica\n4) API (módulos A y C)\n5) Préstamos de material\n6) Reservaciones de laboratorio\n0) Salir");
            switch (leer("Opción")) {
                case "1" -> menuKits();
                case "2" -> menuAccidentes();
                case "3" -> menuResultados();
                case "4" -> menuApi();
                case "5" -> menuPrestamos();
                case "6" -> menuReservaciones();
                case "0" -> { return; }
                default -> System.out.println("Opción no válida.");
            }
        }
    }

    // ---------------- Kits ----------------
    private static void arbol(Kit k, String sangria) {
        System.out.println(sangria + "▸ [" + k.id + "] " + k.nombre + "  (" + KitService.contarPiezas(k) + " piezas en total)");
        for (Kit.Material m : k.materiales)
            System.out.printf("%s    - (mat %d) %s %s ×%d · $%.2f c/u%n", sangria, m.id(), m.clave(), m.nombre(), m.cantidad(), m.costo());
        for (Kit sub : k.subkits) arbol(sub, sangria + "    ");
    }

    private static void menuKits() {
        while (true) {
            System.out.println("\n-- Kits --\n1) Ver kits\n2) Registrar kit\n3) Agregar material\n4) Revisar devolución\n"
                    + "5) Eliminar kit\n6) Quitar material\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        List<Kit> kits = KitService.cargar();
                        if (kits.isEmpty()) System.out.println("Todavía no hay kits.");
                        for (Kit k : kits) arbol(k, "");
                    }
                    case "2" -> {
                        String nombre = leer("Nombre del kit");
                        mostrarPlanos();
                        KitService.kitCrear(nombre, leer("ID del kit padre (Enter = ninguno)"));
                        System.out.println("✔ Kit «" + nombre + "» guardado.");
                    }
                    case "3" -> {
                        mostrarPlanos();
                        KitService.materialAgregar(leer("ID del kit"), leer("Clave"), leer("Material"),
                                leer("Cantidad", "1"), leer("Costo por pieza ($)", "0"));
                        System.out.println("✔ Material guardado.");
                    }
                    case "4" -> revisar();
                    case "5" -> { mostrarPlanos(); KitService.kitEliminar(leer("ID del kit a eliminar")); System.out.println("✔ Kit eliminado (con subkits y materiales)."); }
                    case "6" -> { KitService.materialEliminar(leer("ID del material (mat N)")); System.out.println("✔ Material eliminado."); }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }

    private static void mostrarPlanos() {
        Map<Integer, String> p = KitService.planos(KitService.cargar(), 0);
        p.forEach((id, n) -> System.out.println("  " + id + " = " + n));
    }

    private static void revisar() {
        List<Kit> kits = KitService.cargar();
        mostrarPlanos();
        String t = leer("ID del kit a revisar");
        Kit kit = t.matches("\\d+") ? KitService.buscar(kits, Integer.parseInt(t)) : null;
        if (kit == null) throw new IllegalArgumentException("Elige un kit de la lista.");
        Map<String, KitService.Pieza> piezas = KitService.piezasPorClave(kit);
        if (piezas.isEmpty()) { System.out.println("Este kit todavía no tiene material."); return; }
        Map<String, Integer> devuelto = new LinkedHashMap<>();
        for (Map.Entry<String, KitService.Pieza> e : piezas.entrySet()) {
            String n = leer(e.getKey() + " " + e.getValue().nombre + " (esperadas " + e.getValue().cantidad + ") devueltas",
                    String.valueOf(e.getValue().cantidad));
            if (!n.matches("\\d+")) throw new IllegalArgumentException("Las piezas devueltas de " + e.getKey() + " deben ser un entero ≥ 0.");
            devuelto.put(e.getKey(), Integer.parseInt(n));
        }
        List<KitService.Faltante> faltantes = KitService.revisarDevolucion(kit, devuelto);
        System.out.println("Kit «" + kit.nombre + "»: debe devolverse con " + KitService.contarPiezas(kit) + " piezas.");
        if (faltantes.isEmpty()) { System.out.println("✔ Kit completo."); return; }
        System.out.println("✗ Kit INCOMPLETO. Faltan:");
        double adeudo = 0;
        for (KitService.Faltante f : faltantes) {
            System.out.printf("  %s %s: faltan %d (costo $%.2f)%n", f.clave(), f.nombre(), f.faltan(), f.costoFaltante());
            adeudo += f.costoFaltante();
        }
        System.out.printf("Adeudo sugerido (RN-D06): $%.2f%n", adeudo);
    }

    // ---------------- Accidentes ----------------
    private static void menuAccidentes() {
        while (true) {
            System.out.println("\n-- Accidentes --\n1) Listar\n2) Reportar\n3) Corregir\n4) Eliminar\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        List<Accidente> l = AccidenteService.todos();
                        if (l.isEmpty()) System.out.println("No hay accidentes registrados.");
                        for (Accidente a : l)
                            System.out.printf("%s | %s %s | %s | %s | gravedad %d | %s%n", a.folio(), a.fecha(), a.hora(),
                                    a.matricula(), a.laboratorio(), a.gravedad(), a.descripcion());
                    }
                    case "2" -> {
                        Accidente a = AccidenteService.reportar(leer("Fecha (AAAA-MM-DD)", java.time.LocalDate.now().toString()),
                                leer("Hora (HH:MM)", java.time.LocalTime.now().toString().substring(0, 5)),
                                leer("Matrícula del alumno"), leer("Laboratorio (ej. LAB-QUIM)"),
                                leer("Descripción"), leer("Gravedad (1 a 5)"));
                        System.out.println("✔ Accidente guardado con folio " + a.folio() + ".");
                        try {
                            ApiClient.publicar("reporte_accidentes", a.folio(), a.aMapa());
                            System.out.println("✔ Publicado en la API (reporte_accidentes).");
                        } catch (RuntimeException e) {
                            System.out.println("⚠ Quedó guardado en MySQL pero no se publicó en la API: " + e.getMessage());
                        }
                    }
                    case "3" -> {
                        String folio = leer("Folio a corregir");
                        Accidente a = AccidenteService.obtener(folio);
                        Map<String, String> c = new LinkedHashMap<>();
                        c.put("hora", leer("Hora", a.hora()));
                        c.put("matricula", leer("Matrícula", a.matricula()));
                        c.put("laboratorio", leer("Laboratorio", a.laboratorio()));
                        c.put("gravedad", leer("Gravedad", String.valueOf(a.gravedad())));
                        c.put("descripcion", leer("Descripción", a.descripcion()));
                        AccidenteService.modificar(folio, c);
                        System.out.println("✔ Accidente " + folio + " actualizado (usa API > publicar para reenviarlo).");
                    }
                    case "4" -> {
                        String folio = leer("Folio a eliminar");
                        if (leer("¿Seguro? (s/n)").equalsIgnoreCase("s")) { AccidenteService.eliminar(folio); System.out.println("✔ Accidente " + folio + " eliminado."); }
                    }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }

    // ---------------- Resultados ----------------
    private static void menuResultados() {
        while (true) {
            System.out.println("\n-- Resultados de práctica --\n1) Listar\n2) Capturar / actualizar\n3) Eliminar\n4) Cargar JSON desde archivo\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        List<Resultado> l = ResultadoService.todos();
                        if (l.isEmpty()) System.out.println("No hay resultados guardados.");
                        for (Resultado r : l)
                            System.out.printf("%s | %s | %s | asistió %s | calif %.2f | adeudo %s | $%.2f%n", r.folioInscripcion(),
                                    r.matricula(), r.practicaId(), r.asistio(), r.calificacion(), r.adeudoMaterial(), r.montoAdeudo());
                    }
                    case "2" -> {
                        String folio = leer("folio_inscripcion (ej. INS-0001)");
                        Boolean enC = ApiClient.existe("inscritos_practicas", folio);
                        if (Boolean.FALSE.equals(enC)) System.out.println("⚠ Ese folio NO aparece en inscritos_practicas (módulo C).");
                        else if (enC == null) System.out.println("⚠ No pude verificar el folio contra el módulo C.");
                        String asistio = leer("¿Asistió? (S/N)", "S").toUpperCase();
                        String calif = asistio.equals("N") ? "0" : leer("Calificación (entero de 0 a 10)");
                        String adeudo = leer("¿Adeudo de material? (S/N)", "N").toUpperCase();
                        String monto = adeudo.equals("S") ? leer("Monto del adeudo", "0") : "0";
                        String estado = ResultadoService.guardar(folio, leer("Matrícula"), leer("practica_id (ej. PR-QUIM-01)"),
                                asistio, calif, adeudo, monto);
                        System.out.println("✔ Resultado " + folio + " " + estado + ".");
                    }
                    case "3" -> { String f = leer("folio_inscripcion a eliminar"); ResultadoService.eliminar(f); System.out.println("✔ Resultado " + f + " eliminado."); }
                    case "4" -> {
                        try {
                            System.out.println(ResultadoService.importar(Files.readString(Path.of(leer("Ruta del archivo .json")))));
                        } catch (java.io.IOException e) {
                            throw new IllegalArgumentException("No pude leer el archivo: " + e.getMessage());
                        }
                    }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }

    private static void publicarUno(String recurso, String clave, Map<String, Object> cuerpo) {
        try {
            ApiClient.publicar(recurso, clave, cuerpo);
            System.out.println("✔ Publicado en la API (" + recurso + ").");
        } catch (RuntimeException e) {
            System.out.println("⚠ Quedó guardado en MySQL pero no se publicó en la API: " + e.getMessage());
        }
    }

    /** Borra primero de la API; si falla, no se toca MySQL para que se pueda reintentar. */
    private static void borrarUno(String recurso, String clave) {
        try {
            System.out.println(ApiClient.borrar(recurso, clave) ? "✔ Se borró de la API (" + recurso + ")."
                    : "(No estaba publicado en la API.)");
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se borró nada; reintenta cuando la API responda. " + e.getMessage());
        }
    }

    // ---------------- Préstamos ----------------
    private static void menuPrestamos() {
        while (true) {
            System.out.println("\n-- Préstamos de material --\n1) Listar\n2) Registrar préstamo\n3) Registrar devolución\n4) Eliminar\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        List<Prestamo> l = PrestamoService.todos();
                        if (l.isEmpty()) System.out.println("No hay préstamos registrados.");
                        for (Prestamo p : l)
                            System.out.printf("%s | %s | %s | %s | %s | %s%n", p.folioPrestamo(), p.fecha(), p.matricula(),
                                    p.claveMaterial(), p.estado(), p.descripcion());
                    }
                    case "2" -> {
                        mostrarCatalogo();
                        Prestamo p = PrestamoService.registrar(leer("Matrícula del alumno"), leer("Clave del material (ej. MAT-001)"),
                                leer("Descripción"), leer("Fecha (AAAA-MM-DD)", java.time.LocalDate.now().toString()));
                        System.out.println("✔ Préstamo guardado con folio " + p.folioPrestamo() + ".");
                        publicarUno("prestamos", p.folioPrestamo(), p.aMapa());
                    }
                    case "3" -> {
                        Prestamo p = PrestamoService.devolver(leer("Folio del préstamo"));
                        System.out.println("✔ Préstamo " + p.folioPrestamo() + " marcado como DEVUELTO.");
                        publicarUno("prestamos", p.folioPrestamo(), p.aMapa());
                    }
                    case "4" -> {
                        String folio = PrestamoService.obtener(leer("Folio a eliminar")).folioPrestamo();
                        if (leer("¿Seguro? (s/n)").equalsIgnoreCase("s")) {
                            borrarUno("prestamos", folio);
                            PrestamoService.eliminar(folio);
                            System.out.println("✔ Préstamo " + folio + " eliminado.");
                        }
                    }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }

    private static void mostrarCatalogo() {
        try {
            for (JsonNode n : ApiClient.lista("materiales"))
                System.out.printf("  %s  %-30s $%8.2f  existencia %d%n", n.path("clave_material").asText(),
                        n.path("nombre").asText(), n.path("costo").asDouble(), n.path("existencia").asInt());
        } catch (RuntimeException e) {
            System.out.println("⚠ No pude leer el catálogo de materiales: " + e.getMessage());
        }
    }

    // ---------------- Reservaciones ----------------
    private static void menuReservaciones() {
        while (true) {
            System.out.println("\n-- Reservaciones de laboratorio --\n1) Listar\n2) Reservar\n3) Cancelar\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        List<Reservacion> l = ReservacionService.todas();
                        if (l.isEmpty()) System.out.println("No hay reservaciones registradas.");
                        for (Reservacion r : l)
                            System.out.printf("%s | %s %s-%s | %s | %s | %s%n", r.folioReservacion(), r.fecha(), r.horaInicio(),
                                    r.horaFin(), r.laboratorio(), r.claveEquipo(), r.matricula());
                    }
                    case "2" -> {
                        Reservacion r = ReservacionService.reservar(leer("Laboratorio (ej. LAB-PROG)"), leer("Clave del equipo (ej. EST-01)"),
                                leer("Fecha (AAAA-MM-DD)", java.time.LocalDate.now().toString()), leer("Hora de inicio (HH:MM)"),
                                leer("Hora de fin (HH:MM)"), leer("Matrícula del alumno"));
                        System.out.println("✔ Reservación guardada con folio " + r.folioReservacion() + ".");
                        publicarUno("reservaciones", r.folioReservacion(), r.aMapa());
                    }
                    case "3" -> {
                        String folio = ReservacionService.obtener(leer("Folio a cancelar")).folioReservacion();
                        if (leer("¿Seguro? (s/n)").equalsIgnoreCase("s")) {
                            borrarUno("reservaciones", folio);
                            ReservacionService.cancelar(folio);
                            System.out.println("✔ Reservación " + folio + " cancelada.");
                        }
                    }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }

    // ---------------- API ----------------
    private static void menuApi() {
        System.out.println("\nAPI base: " + Config.API_BASE);
        while (true) {
            System.out.println("\n-- API --\n1) Ver inscritos_practicas (módulo C)\n2) Publicar accidentes (reporte_accidentes → módulo A)\n"
                    + "3) Publicar resultados (resultados_practicas → módulo C)\n4) Ver catálogo de materiales\n"
                    + "5) Publicar préstamos (prestamos → módulo D)\n6) Publicar reservaciones (reservaciones → módulo D)\n0) Volver");
            String op = leer("Opción");
            if (op.equals("0")) return;
            seguro(() -> {
                switch (op) {
                    case "1" -> {
                        JsonNode l = ApiClient.lista("inscritos_practicas");
                        System.out.println(l.size() + " inscritos:");
                        for (JsonNode n : l) System.out.println("  " + n);
                    }
                    case "2" -> {
                        int ok = 0;
                        for (Accidente a : AccidenteService.todos()) {
                            try { ApiClient.publicar("reporte_accidentes", a.folio(), a.aMapa()); ok++; }
                            catch (RuntimeException e) { System.out.println("✗ " + a.folio() + ": " + e.getMessage()); }
                        }
                        System.out.println("Publicados: " + ok);
                    }
                    case "3" -> {
                        int ok = 0;
                        for (Resultado r : ResultadoService.todos()) {
                            try { ApiClient.publicar("resultados_practicas", r.folioInscripcion(), r.aMapa()); ok++; }
                            catch (RuntimeException e) { System.out.println("✗ " + r.folioInscripcion() + ": " + e.getMessage()); }
                        }
                        System.out.println("Publicados: " + ok);
                    }
                    case "4" -> mostrarCatalogo();
                    case "5" -> {
                        int ok = 0;
                        for (Prestamo p : PrestamoService.todos()) {
                            try { ApiClient.publicar("prestamos", p.folioPrestamo(), p.aMapa()); ok++; }
                            catch (RuntimeException e) { System.out.println("✗ " + p.folioPrestamo() + ": " + e.getMessage()); }
                        }
                        System.out.println("Publicados: " + ok);
                    }
                    case "6" -> {
                        int ok = 0;
                        for (Reservacion r : ReservacionService.todas()) {
                            try { ApiClient.publicar("reservaciones", r.folioReservacion(), r.aMapa()); ok++; }
                            catch (RuntimeException e) { System.out.println("✗ " + r.folioReservacion() + ": " + e.getMessage()); }
                        }
                        System.out.println("Publicados: " + ok);
                    }
                    default -> System.out.println("Opción no válida.");
                }
            });
        }
    }
}
