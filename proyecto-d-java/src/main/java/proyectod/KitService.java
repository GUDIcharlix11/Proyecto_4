package proyectod;

import java.util.*;

/** Equivale a funciones_kits.php (incluye la recursividad de kits dentro de kits). */
final class KitService {
    static final class Pieza {
        final String nombre; int cantidad; final double costo;
        Pieza(String nombre, int cantidad, double costo) { this.nombre = nombre; this.cantidad = cantidad; this.costo = costo; }
    }
    record Faltante(String clave, String nombre, int esperadas, int devueltas, int faltan, double costoFaltante) {}

    private KitService() {}

    static List<Kit> cargar() {
        Map<Integer, Kit> porId = new LinkedHashMap<>();
        Map<Integer, List<Integer>> porPadre = new HashMap<>();
        for (Map<String, Object> f : Db.consultar("SELECT id, nombre, padre_id FROM kits ORDER BY id")) {
            int id = Db.i(f.get("id"));
            porId.put(id, new Kit(id, Db.s(f.get("nombre"))));
            int padre = f.get("padre_id") == null ? 0 : Db.i(f.get("padre_id"));
            porPadre.computeIfAbsent(padre, k -> new ArrayList<>()).add(id);
        }
        for (Map<String, Object> m : Db.consultar("SELECT id, kit_id, clave, nombre, cantidad, costo FROM materiales ORDER BY id")) {
            Kit k = porId.get(Db.i(m.get("kit_id")));
            if (k != null) k.materiales.add(new Kit.Material(Db.i(m.get("id")), Db.s(m.get("clave")), Db.s(m.get("nombre")),
                    Db.i(m.get("cantidad")), Db.d(m.get("costo"))));
        }
        for (Map.Entry<Integer, List<Integer>> e : porPadre.entrySet())
            if (e.getKey() != 0) for (int hijo : e.getValue()) porId.get(e.getKey()).subkits.add(porId.get(hijo));
        List<Kit> raices = new ArrayList<>();
        for (int id : porPadre.getOrDefault(0, List.of())) raices.add(porId.get(id));
        return raices;
    }

    /** Total de piezas del kit y de todos sus subkits (recursivo). */
    static int contarPiezas(Kit kit) {
        int total = 0;
        for (Kit.Material m : kit.materiales) total += m.cantidad();
        for (Kit sub : kit.subkits) total += contarPiezas(sub);
        return total;
    }

    private static void sumar(Map<String, Pieza> total, String clave, String nombre, int cantidad, double costo) {
        total.computeIfAbsent(clave, k -> new Pieza(nombre, 0, costo)).cantidad += cantidad;
    }

    static Map<String, Pieza> piezasPorClave(Kit kit) {
        Map<String, Pieza> total = new LinkedHashMap<>();
        for (Kit.Material m : kit.materiales) sumar(total, m.clave(), m.nombre(), m.cantidad(), m.costo());
        for (Kit sub : kit.subkits)
            for (Map.Entry<String, Pieza> e : piezasPorClave(sub).entrySet())
                sumar(total, e.getKey(), e.getValue().nombre, e.getValue().cantidad, e.getValue().costo);
        return total;
    }

    static Kit buscar(List<Kit> kits, int id) {
        for (Kit k : kits) {
            if (k.id == id) return k;
            Kit dentro = buscar(k.subkits, id);
            if (dentro != null) return dentro;
        }
        return null;
    }

    static Map<Integer, String> planos(List<Kit> kits, int nivel) {
        Map<Integer, String> lista = new LinkedHashMap<>();
        for (Kit k : kits) {
            lista.put(k.id, "— ".repeat(nivel) + k.nombre);
            lista.putAll(planos(k.subkits, nivel + 1));
        }
        return lista;
    }

    static List<Faltante> revisarDevolucion(Kit kit, Map<String, Integer> devuelto) {
        List<Faltante> faltantes = new ArrayList<>();
        for (Map.Entry<String, Pieza> e : piezasPorClave(kit).entrySet()) {
            int recibidas = devuelto.getOrDefault(e.getKey(), 0);
            Pieza p = e.getValue();
            if (recibidas < p.cantidad) {
                int faltan = p.cantidad - recibidas;
                faltantes.add(new Faltante(e.getKey(), p.nombre, p.cantidad, recibidas, faltan,
                        Math.round(faltan * p.costo * 100.0) / 100.0));
            }
        }
        return faltantes;
    }

    private static boolean existe(String tabla, int id) {
        return !Db.consultar("SELECT 1 FROM " + tabla + " WHERE id = ?", id).isEmpty();
    }

    static void kitCrear(String nombre, String padreTxt) {
        nombre = nombre == null ? "" : nombre.trim();
        if (nombre.isEmpty()) throw new IllegalArgumentException("El nombre del kit no puede ir vacío.");
        if (nombre.length() > 100) throw new IllegalArgumentException("El nombre del kit es muy largo (máximo 100 caracteres).");
        Integer padre = null;
        String t = padreTxt == null ? "" : padreTxt.trim();
        if (!t.isEmpty()) {
            if (!t.matches("\\d+") || !existe("kits", Integer.parseInt(t)))
                throw new IllegalArgumentException("El kit donde quieres meterlo no existe.");
            padre = Integer.parseInt(t);
        }
        Db.ejecutar("INSERT INTO kits (nombre, padre_id) VALUES (?, ?)", nombre, padre);
    }

    static void materialAgregar(String kitTxt, String clave, String nombre, String cantidad, String costo) {
        String kit = kitTxt == null ? "" : kitTxt.trim();
        if (!kit.matches("\\d+") || !existe("kits", Integer.parseInt(kit)))
            throw new IllegalArgumentException("Elige el kit al que pertenece el material.");
        clave = clave.trim(); nombre = nombre.trim(); cantidad = cantidad.trim(); costo = costo.trim();
        List<String> errores = new ArrayList<>();
        if (clave.isEmpty() || clave.length() > 20) errores.add("La clave es obligatoria (máximo 20 caracteres).");
        if (nombre.isEmpty() || nombre.length() > 100) errores.add("El nombre del material es obligatorio (máximo 100 caracteres).");
        if (!cantidad.matches("\\d{1,6}") || Integer.parseInt(cantidad) < 1) errores.add("La cantidad debe ser un entero mayor a 0.");
        if (!Validaciones.esNumero(costo) || Double.parseDouble(costo) < 0 || Double.parseDouble(costo) > 1000000)
            errores.add("El costo debe ser un número entre 0 y 1,000,000.");
        if (errores.isEmpty() && !Db.consultar("SELECT 1 FROM materiales WHERE kit_id = ? AND clave = ?",
                Integer.parseInt(kit), clave).isEmpty())
            errores.add("Ese kit ya tiene un material con la clave " + clave + ".");
        if (!errores.isEmpty()) throw new IllegalArgumentException(String.join(" ", errores));
        Db.ejecutar("INSERT INTO materiales (kit_id, clave, nombre, cantidad, costo) VALUES (?, ?, ?, ?, ?)",
                Integer.parseInt(kit), clave, nombre, Integer.parseInt(cantidad), Double.parseDouble(costo));
    }

    static void kitEliminar(String idTxt) {
        String id = idTxt == null ? "" : idTxt.trim();
        if (!id.matches("\\d+") || !existe("kits", Integer.parseInt(id))) throw new IllegalArgumentException("Ese kit no existe.");
        Db.ejecutar("DELETE FROM kits WHERE id = ?", Integer.parseInt(id));
    }

    static void materialEliminar(String idTxt) {
        String id = idTxt == null ? "" : idTxt.trim();
        if (!id.matches("\\d+") || !existe("materiales", Integer.parseInt(id))) throw new IllegalArgumentException("Ese material no existe.");
        Db.ejecutar("DELETE FROM materiales WHERE id = ?", Integer.parseInt(id));
    }
}
