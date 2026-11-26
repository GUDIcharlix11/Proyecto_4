package proyectod;

import java.util.ArrayList;
import java.util.List;

final class Kit {
    record Material(int id, String clave, String nombre, int cantidad, double costo) {}

    final int id;
    final String nombre;
    final List<Material> materiales = new ArrayList<>();
    final List<Kit> subkits = new ArrayList<>();

    Kit(int id, String nombre) { this.id = id; this.nombre = nombre; }
}
