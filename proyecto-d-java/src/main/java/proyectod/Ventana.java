package proyectod;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;

/** Interfaz gráfica (Swing): una pestaña por sección. Usa los mismos servicios que el menú de consola. */
final class Ventana extends JFrame {
    private final JLabel estado = new JLabel(" ");

    static void abrir() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Se queda con el aspecto por defecto de Java.
            }
            try {
                Db.get();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "No pude conectar con MySQL: " + e.getMessage()
                        + "\nRevisa que MySQL esté encendido y los datos de Config.java / variables de entorno.",
                        "Laboratorios y talleres", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            new Ventana().setVisible(true);
        });
    }

    private Ventana() {
        super("Laboratorios y talleres · Equipo 4");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Kits", new PanelKits());
        tabs.addTab("Accidentes", new PanelAccidentes());
        tabs.addTab("Resultados", new PanelResultados());
        tabs.addTab("Préstamos", new PanelPrestamos());
        tabs.addTab("Reservaciones", new PanelReservaciones());
        tabs.addTab("API", new PanelApi());
        estado.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(tabs, BorderLayout.CENTER);
        add(estado, BorderLayout.SOUTH);
        setSize(1050, 650);
        setLocationRelativeTo(null);
        avisar("Base de datos: " + Config.DB_NAME + " · conectada  |  API: " + Config.API_BASE);
    }

    // ---------------- utilidades ----------------
    private void avisar(String msg) { estado.setText(msg); }

    /** Ejecuta una acción y muestra el error de validación / API en un cuadro de diálogo. */
    private void seguro(Runnable r) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try { r.run(); }
        catch (RuntimeException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo", JOptionPane.WARNING_MESSAGE); }
        finally { setCursor(Cursor.getDefaultCursor()); }
    }

    private static JButton boton(String texto, Runnable r) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Barra de botones en dos renglones: acciones y publicación. */
    private static JPanel barra(JPanel acciones, JPanel publicar) {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.add(acciones);
        p.add(publicar);
        return p;
    }

    private static JPanel fila(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JComponent c : cs) p.add(c);
        return p;
    }

    /** Formulario de dos columnas: etiqueta y campo. */
    private static JPanel formulario(Object... pares) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < pares.length; i += 2) {
            int col = (i / 2) % 2 * 2, ren = (i / 2) / 2;
            g.gridx = col; g.gridy = ren; g.fill = GridBagConstraints.NONE; g.weightx = 0;
            p.add(new JLabel((String) pares[i]), g);
            g.gridx = col + 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            p.add((Component) pares[i + 1], g);
        }
        return p;
    }

    private static JTable tabla(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setAutoCreateRowSorter(true);
        DefaultTableCellRenderer texto = new DefaultTableCellRenderer();
        texto.putClientProperty("html.disable", Boolean.TRUE);
        t.setDefaultRenderer(Object.class, texto);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return t;
    }

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            /** Así las columnas numéricas se ordenan como números y no como texto. */
            @Override public Class<?> getColumnClass(int c) {
                Object v = getRowCount() > 0 ? getValueAt(0, c) : null;
                return v == null ? Object.class : v.getClass();
            }
        };
    }

    /** Valor de la columna 0 de la fila seleccionada (el folio). */
    private static String seleccionado(JTable t, String que) {
        int r = t.getSelectedRow();
        if (r < 0) throw new IllegalArgumentException("Selecciona " + que + " en la tabla.");
        return String.valueOf(t.getModel().getValueAt(t.convertRowIndexToModel(r), 0));
    }

    private static JTextField campo(String inicial) { return new JTextField(inicial, 14); }
    private static String hoy() { return LocalDate.now().toString(); }
    private static String ahora() { return LocalTime.now().toString().substring(0, 5); }

    private <T> void publicarTodos(String recurso, List<T> registros, Function<T, String> clave, Function<T, Map<String, Object>> mapa) {
        avisar("Publicando " + registros.size() + " registros en " + recurso + "…");
        // Las llamadas HTTP van en otro hilo para que la ventana no se congele; el diálogo modal
        // impide cambiar registros mientras se publica la lista ya leída.
        JDialog espera = new JDialog(this, "Publicando…", true);
        JProgressBar barra = new JProgressBar();
        barra.setIndeterminate(true);
        espera.add(new JLabel("  Publicando " + registros.size() + " registros en " + recurso + "…  "), BorderLayout.NORTH);
        espera.add(barra, BorderLayout.CENTER);
        espera.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        espera.pack();
        espera.setLocationRelativeTo(this);
        SwingWorker<Integer, Void> trabajo = new SwingWorker<>() {
            private final List<String> errores = new ArrayList<>();

            @Override protected Integer doInBackground() {
                int ok = 0;
                for (T r : registros) {
                    try { ApiClient.publicar(recurso, clave.apply(r), mapa.apply(r)); ok++; }
                    catch (RuntimeException e) { errores.add(clave.apply(r) + ": " + e.getMessage()); }
                }
                return ok;
            }

            @Override protected void done() {
                espera.dispose();
                int ok;
                try { ok = get(); } catch (Exception e) { ok = 0; errores.add(e.getMessage()); }
                String msg = "Publicados en " + recurso + ": " + ok + " de " + registros.size() + ".";
                avisar(msg);
                if (!errores.isEmpty()) msg += "\n\n✗ " + String.join("\n✗ ", errores);
                JOptionPane.showMessageDialog(Ventana.this, msg, "Publicar",
                        errores.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            }
        };
        trabajo.execute();
        if (!trabajo.isDone()) espera.setVisible(true);
    }

    private boolean confirmar(String pregunta) {
        return JOptionPane.showConfirmDialog(this, pregunta, "Confirmar", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    // ---------------- Kits ----------------
    private final class PanelKits extends JPanel {
        private final DefaultMutableTreeNode raiz = new DefaultMutableTreeNode("Kits");
        private final JTree arbol = new JTree(raiz);

        private record NodoKit(Kit kit) { @Override public String toString() {
            return "[" + kit.id + "] " + kit.nombre + "  (" + KitService.contarPiezas(kit) + " piezas en total)"; } }
        private record NodoMaterial(Kit.Material m) { @Override public String toString() {
            return String.format("%s %s ×%d · $%.2f c/u", m.clave(), m.nombre(), m.cantidad(), m.costo()); } }

        PanelKits() {
            super(new BorderLayout());
            add(new JScrollPane(arbol), BorderLayout.CENTER);
            add(fila(boton("Nuevo kit", this::nuevoKit), boton("Agregar material", this::agregarMaterial),
                    boton("Revisar devolución", this::revisar), boton("Eliminar seleccionado", this::eliminar),
                    boton("Actualizar", this::cargar)), BorderLayout.NORTH);
            add(new JLabel("  Selecciona un kit para meterle un subkit o material, revisar su devolución o eliminarlo."), BorderLayout.SOUTH);
            cargar();
        }

        private void cargar() {
            seguro(() -> {
                raiz.removeAllChildren();
                for (Kit k : KitService.cargar()) raiz.add(nodo(k));
                ((DefaultTreeModel) arbol.getModel()).reload();
                for (int i = 0; i < arbol.getRowCount(); i++) arbol.expandRow(i);
            });
        }

        private DefaultMutableTreeNode nodo(Kit k) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(new NodoKit(k));
            for (Kit.Material m : k.materiales) n.add(new DefaultMutableTreeNode(new NodoMaterial(m)));
            for (Kit sub : k.subkits) n.add(nodo(sub));
            return n;
        }

        private Object elegido() {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) arbol.getLastSelectedPathComponent();
            return n == null ? null : n.getUserObject();
        }

        private Kit kitElegido() {
            if (elegido() instanceof NodoKit nk) return nk.kit();
            throw new IllegalArgumentException("Selecciona un kit en el árbol.");
        }

        private void nuevoKit() {
            seguro(() -> {
                Kit padre = elegido() instanceof NodoKit nk ? nk.kit() : null;
                String nombre = JOptionPane.showInputDialog(this, padre == null ? "Nombre del kit (sin kit padre):"
                        : "Nombre del subkit dentro de «" + padre.nombre + "»:");
                if (nombre == null) return;
                KitService.kitCrear(nombre, padre == null ? "" : String.valueOf(padre.id));
                avisar("✔ Kit «" + nombre.trim() + "» guardado.");
                cargar();
            });
        }

        private void agregarMaterial() {
            seguro(() -> {
                Kit k = kitElegido();
                JTextField clave = campo(""), nombre = campo(""), cantidad = campo("1"), costo = campo("0");
                JPanel f = formulario("Clave", clave, "Material", nombre, "Cantidad", cantidad, "Costo por pieza ($)", costo);
                if (JOptionPane.showConfirmDialog(this, f, "Material para «" + k.nombre + "»", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                KitService.materialAgregar(String.valueOf(k.id), clave.getText(), nombre.getText(), cantidad.getText(), costo.getText());
                avisar("✔ Material guardado.");
                cargar();
            });
        }

        private void revisar() {
            seguro(() -> {
                Kit k = kitElegido();
                Map<String, KitService.Pieza> piezas = KitService.piezasPorClave(k);
                if (piezas.isEmpty()) throw new IllegalArgumentException("Este kit todavía no tiene material.");
                List<Object> pares = new ArrayList<>();
                Map<String, JSpinner> devueltas = new LinkedHashMap<>();
                piezas.forEach((clave, p) -> {
                    JSpinner s = new JSpinner(new SpinnerNumberModel(p.cantidad, 0, 999999, 1));
                    devueltas.put(clave, s);
                    pares.add(clave + " " + p.nombre + " (esperadas " + p.cantidad + ")");
                    pares.add(s);
                });
                JPanel f = new JPanel(new GridLayout(0, 2, 6, 4));
                for (Object o : pares) f.add(o instanceof String t ? new JLabel(t) : (Component) o);
                JScrollPane desliza = new JScrollPane(f);
                desliza.setPreferredSize(new Dimension(f.getPreferredSize().width + 30, Math.min(f.getPreferredSize().height + 10, 400)));
                if (JOptionPane.showConfirmDialog(this, desliza, "Devolución de «" + k.nombre + "»", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                Map<String, Integer> devuelto = new LinkedHashMap<>();
                devueltas.forEach((c, s) -> devuelto.put(c, (Integer) s.getValue()));
                List<KitService.Faltante> faltantes = KitService.revisarDevolucion(k, devuelto);
                if (faltantes.isEmpty()) { JOptionPane.showMessageDialog(this, "✔ Kit completo."); return; }
                StringBuilder sb = new StringBuilder("✗ Kit INCOMPLETO. Faltan:\n");
                double adeudo = 0;
                for (KitService.Faltante x : faltantes) {
                    sb.append(String.format("  %s %s: faltan %d (costo $%.2f)%n", x.clave(), x.nombre(), x.faltan(), x.costoFaltante()));
                    adeudo += x.costoFaltante();
                }
                sb.append(String.format("%nAdeudo sugerido (RN-D06): $%.2f", adeudo));
                JOptionPane.showMessageDialog(this, sb.toString(), "Devolución", JOptionPane.WARNING_MESSAGE);
            });
        }

        private void eliminar() {
            seguro(() -> {
                Object o = elegido();
                if (o instanceof NodoKit nk) {
                    if (!confirmar("¿Eliminar el kit «" + nk.kit().nombre + "» con sus subkits y materiales?")) return;
                    KitService.kitEliminar(String.valueOf(nk.kit().id));
                    avisar("✔ Kit eliminado (con subkits y materiales).");
                } else if (o instanceof NodoMaterial nm) {
                    if (!confirmar("¿Quitar " + nm.m().clave() + " " + nm.m().nombre() + "?")) return;
                    KitService.materialEliminar(String.valueOf(nm.m().id()));
                    avisar("✔ Material eliminado.");
                } else throw new IllegalArgumentException("Selecciona un kit o un material en el árbol.");
                cargar();
            });
        }
    }

    // ---------------- Accidentes ----------------
    private final class PanelAccidentes extends JPanel {
        private final DefaultTableModel m = modelo("Folio", "Fecha", "Hora", "Matrícula", "Laboratorio", "Gravedad", "Descripción");
        private final JTable t = tabla(m);
        private final JTextField fecha = campo(hoy()), hora = campo(ahora()), matricula = campo(""), laboratorio = campo(""), descripcion = campo("");
        private final JComboBox<String> gravedad = new JComboBox<>(new String[]{"1", "2", "3", "4", "5"});

        PanelAccidentes() {
            super(new BorderLayout());
            JPanel arriba = new JPanel(new BorderLayout());
            arriba.add(formulario("Fecha (AAAA-MM-DD)", fecha, "Hora (HH:MM)", hora, "Matrícula del alumno", matricula,
                    "Laboratorio (ej. LAB-QUIM)", laboratorio, "Gravedad (1 a 5)", gravedad, "Descripción", descripcion), BorderLayout.CENTER);
            arriba.add(barra(fila(boton("Reportar y publicar", this::reportar), boton("Corregir seleccionado", this::corregir),
                    boton("Eliminar seleccionado", this::eliminar)),
                    fila(boton("Publicar todos (reporte_accidentes → módulo A)", () -> seguro(() ->
                            publicarTodos("reporte_accidentes", AccidenteService.todos(), Accidente::folio, Accidente::aMapa))))),
                    BorderLayout.SOUTH);
            add(arriba, BorderLayout.NORTH);
            add(new JScrollPane(t), BorderLayout.CENTER);
            cargar();
        }

        private void cargar() {
            seguro(() -> {
                m.setRowCount(0);
                for (Accidente a : AccidenteService.todos())
                    m.addRow(new Object[]{a.folio(), a.fecha(), a.hora(), a.matricula(), a.laboratorio(), a.gravedad(), a.descripcion()});
            });
        }

        private void reportar() {
            seguro(() -> {
                Accidente a = AccidenteService.reportar(fecha.getText(), hora.getText(), matricula.getText(), laboratorio.getText(),
                        descripcion.getText(), (String) gravedad.getSelectedItem());
                String api = Main.avisoPublicar("reporte_accidentes", a.folio(), a.aMapa());
                avisar("✔ Accidente guardado con folio " + a.folio() + ".  " + api);
                JOptionPane.showMessageDialog(this, "✔ Accidente guardado con folio " + a.folio() + ".\n" + api);
                matricula.setText(""); descripcion.setText("");
                cargar();
            });
        }

        private void corregir() {
            seguro(() -> {
                Accidente a = AccidenteService.obtener(seleccionado(t, "un accidente"));
                JTextField h = campo(a.hora()), mat = campo(a.matricula()), lab = campo(a.laboratorio()), d = campo(a.descripcion());
                JComboBox<String> g = new JComboBox<>(new String[]{"1", "2", "3", "4", "5"});
                g.setSelectedItem(String.valueOf(a.gravedad()));
                JPanel f = formulario("Hora", h, "Matrícula", mat, "Laboratorio", lab, "Gravedad", g, "Descripción", d);
                if (JOptionPane.showConfirmDialog(this, f, "Corregir " + a.folio(), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
                Map<String, String> c = new LinkedHashMap<>();
                c.put("hora", h.getText()); c.put("matricula", mat.getText()); c.put("laboratorio", lab.getText());
                c.put("gravedad", (String) g.getSelectedItem()); c.put("descripcion", d.getText());
                Accidente n = AccidenteService.modificar(a.folio(), c);
                avisar("✔ Accidente " + n.folio() + " actualizado.  " + Main.avisoPublicar("reporte_accidentes", n.folio(), n.aMapa()));
                cargar();
            });
        }

        private void eliminar() {
            seguro(() -> {
                String folio = seleccionado(t, "un accidente");
                if (!confirmar("¿Eliminar el accidente " + folio + "?")) return;
                AccidenteService.eliminar(folio);
                avisar("✔ Accidente " + folio + " eliminado.");
                cargar();
            });
        }
    }

    // ---------------- Resultados ----------------
    private final class PanelResultados extends JPanel {
        private final DefaultTableModel m = modelo("folio_inscripcion", "Matrícula", "Práctica", "Asistió", "Calificación", "Adeudo", "Monto");
        private final JTable t = tabla(m);
        private final JTextField folio = campo(""), matricula = campo(""), practica = campo(""), calif = campo(""), monto = campo("0");
        private final JComboBox<String> asistio = new JComboBox<>(new String[]{"S", "N"}), adeudo = new JComboBox<>(new String[]{"N", "S"});

        PanelResultados() {
            super(new BorderLayout());
            JPanel arriba = new JPanel(new BorderLayout());
            arriba.add(formulario("folio_inscripcion", folio, "Matrícula", matricula, "practica_id (ej. PR-QUIM-01)", practica,
                    "¿Asistió?", asistio, "Calificación (entero 0 a 10)", calif, "¿Adeudo de material?", adeudo, "Monto del adeudo", monto),
                    BorderLayout.CENTER);
            arriba.add(barra(fila(boton("Buscar en módulo C", this::buscar), boton("Guardar / actualizar", this::guardar),
                    boton("Eliminar seleccionado", this::eliminar), boton("Cargar JSON…", this::importar)),
                    fila(boton("Publicar todos (resultados_practicas → módulo C)", () -> seguro(() ->
                            publicarTodos("resultados_practicas", ResultadoService.todos(), Resultado::folioInscripcion, Resultado::aMapa))))),
                    BorderLayout.SOUTH);
            add(arriba, BorderLayout.NORTH);
            add(new JScrollPane(t), BorderLayout.CENTER);
            t.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) llenar(); });
            cargar();
        }

        private void cargar() {
            seguro(() -> {
                m.setRowCount(0);
                for (Resultado r : ResultadoService.todos())
                    m.addRow(new Object[]{r.folioInscripcion(), r.matricula(), r.practicaId(), r.asistio(),
                            r.calificacion(), r.adeudoMaterial(), r.montoAdeudo()});
            });
        }

        private void llenar() {
            int r = t.getSelectedRow();
            if (r < 0) return;
            int i = t.convertRowIndexToModel(r);
            folio.setText((String) m.getValueAt(i, 0)); matricula.setText((String) m.getValueAt(i, 1));
            practica.setText((String) m.getValueAt(i, 2)); asistio.setSelectedItem(m.getValueAt(i, 3));
            double c = (Double) m.getValueAt(i, 4);
            calif.setText(c % 1 == 0 ? String.valueOf((long) c) : String.valueOf(c));
            adeudo.setSelectedItem(m.getValueAt(i, 5));
            monto.setText(String.format(Locale.ROOT, "%.2f", (Double) m.getValueAt(i, 6)));
        }

        /** Trae matrícula y práctica del inscrito publicado por el módulo C. */
        private void buscar() {
            seguro(() -> {
                JsonNode n = ApiClient.uno("inscritos_practicas", folio.getText().trim());
                if (n == null) throw new IllegalArgumentException("Ese folio NO aparece en inscritos_practicas (módulo C).");
                if (n.hasNonNull("matricula")) matricula.setText(n.get("matricula").asText());
                if (n.hasNonNull("practica_id")) practica.setText(n.get("practica_id").asText());
                avisar("✔ Inscrito encontrado en el módulo C: " + n);
            });
        }

        private void guardar() {
            seguro(() -> {
                String f = folio.getText().trim();
                Boolean enC = ApiClient.existe("inscritos_practicas", f);
                String aviso = Boolean.FALSE.equals(enC) ? "  ⚠ Ese folio NO aparece en inscritos_practicas (módulo C)."
                        : enC == null ? "  ⚠ No pude verificar el folio contra el módulo C." : "";
                String a = (String) asistio.getSelectedItem(), ad = (String) adeudo.getSelectedItem();
                String estadoR = ResultadoService.guardar(f, matricula.getText(), practica.getText(), a,
                        "N".equals(a) ? "0" : calif.getText(), ad, "S".equals(ad) ? monto.getText() : "0");
                avisar("✔ Resultado " + f + " " + estadoR + "." + aviso);
                cargar();
            });
        }

        private void eliminar() {
            seguro(() -> {
                String f = seleccionado(t, "un resultado");
                if (!confirmar("¿Eliminar el resultado " + f + "?")) return;
                ResultadoService.eliminar(f);
                avisar("✔ Resultado " + f + " eliminado.");
                cargar();
            });
        }

        private void importar() {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            seguro(() -> {
                try {
                    String r = ResultadoService.importar(Files.readString(fc.getSelectedFile().toPath()));
                    JOptionPane.showMessageDialog(this, r);
                    avisar(r);
                } catch (java.io.IOException e) {
                    throw new IllegalArgumentException("No pude leer el archivo: " + e.getMessage());
                }
                cargar();
            });
        }
    }

    // ---------------- Préstamos ----------------
    private final class PanelPrestamos extends JPanel {
        private final DefaultTableModel m = modelo("Folio", "Fecha", "Matrícula", "Material", "Estado", "Descripción");
        private final JTable t = tabla(m);
        private final JTextField matricula = campo(""), descripcion = campo(""), fecha = campo(hoy());
        private final JComboBox<String> material = new JComboBox<>();

        PanelPrestamos() {
            super(new BorderLayout());
            material.setEditable(true);
            JPanel arriba = new JPanel(new BorderLayout());
            arriba.add(formulario("Matrícula del alumno", matricula, "Material (catálogo)", material,
                    "Descripción", descripcion, "Fecha (AAAA-MM-DD)", fecha), BorderLayout.CENTER);
            arriba.add(barra(fila(boton("Registrar préstamo", this::registrar), boton("Marcar devuelto", this::devolver),
                    boton("Eliminar seleccionado", this::eliminar), boton("Recargar catálogo", this::catalogo)),
                    fila(boton("Publicar todos (prestamos)", () -> seguro(() ->
                            publicarTodos("prestamos", PrestamoService.todos(), Prestamo::folioPrestamo, Prestamo::aMapa))))),
                    BorderLayout.SOUTH);
            add(arriba, BorderLayout.NORTH);
            add(new JScrollPane(t), BorderLayout.CENTER);
            catalogo();
            cargar();
        }

        private void catalogo() {
            material.removeAllItems();
            try {
                for (JsonNode n : ApiClient.lista("materiales"))
                    material.addItem(n.path("clave_material").asText() + " · " + n.path("nombre").asText()
                            + " (existencia " + n.path("existencia").asInt() + ")");
            } catch (RuntimeException e) {
                avisar("⚠ No pude leer el catálogo de materiales: " + e.getMessage() + " (puedes escribir la clave a mano)");
            }
        }

        private void cargar() {
            seguro(() -> {
                m.setRowCount(0);
                for (Prestamo p : PrestamoService.todos())
                    m.addRow(new Object[]{p.folioPrestamo(), p.fecha(), p.matricula(), p.claveMaterial(), p.estado(), p.descripcion()});
            });
        }

        private void registrar() {
            seguro(() -> {
                String sel = material.getSelectedItem() == null ? "" : material.getSelectedItem().toString();
                String clave = sel.contains(" · ") ? sel.substring(0, sel.indexOf(" · ")) : sel;
                Prestamo p = PrestamoService.registrar(matricula.getText(), clave, descripcion.getText(), fecha.getText());
                avisar("✔ Préstamo guardado con folio " + p.folioPrestamo() + ".  " + Main.avisoPublicar("prestamos", p.folioPrestamo(), p.aMapa()));
                matricula.setText(""); descripcion.setText("");
                cargar();
            });
        }

        private void devolver() {
            seguro(() -> {
                Prestamo p = PrestamoService.devolver(seleccionado(t, "un préstamo"));
                avisar("✔ Préstamo " + p.folioPrestamo() + " marcado como DEVUELTO.  " + Main.avisoPublicar("prestamos", p.folioPrestamo(), p.aMapa()));
                cargar();
            });
        }

        private void eliminar() {
            seguro(() -> {
                String folio = PrestamoService.obtener(seleccionado(t, "un préstamo")).folioPrestamo();
                if (!confirmar("¿Eliminar el préstamo " + folio + " (también de la API)?")) return;
                String api = Main.avisoBorrar("prestamos", folio);
                PrestamoService.eliminar(folio);
                avisar("✔ Préstamo " + folio + " eliminado.  " + api);
                cargar();
            });
        }
    }

    // ---------------- Reservaciones ----------------
    private final class PanelReservaciones extends JPanel {
        private final DefaultTableModel m = modelo("Folio", "Fecha", "Inicio", "Fin", "Laboratorio", "Equipo", "Matrícula");
        private final JTable t = tabla(m);
        private final JTextField laboratorio = campo(""), equipo = campo(""), fecha = campo(hoy()),
                inicio = campo(""), fin = campo(""), matricula = campo("");

        PanelReservaciones() {
            super(new BorderLayout());
            JPanel arriba = new JPanel(new BorderLayout());
            arriba.add(formulario("Laboratorio (ej. LAB-PROG)", laboratorio, "Clave del equipo (ej. EST-01)", equipo,
                    "Fecha (AAAA-MM-DD)", fecha, "Matrícula del alumno", matricula,
                    "Hora de inicio (HH:MM)", inicio, "Hora de fin (HH:MM)", fin), BorderLayout.CENTER);
            arriba.add(barra(fila(boton("Reservar", this::reservar), boton("Cancelar seleccionada", this::cancelar)),
                    fila(boton("Publicar todas (reservaciones)", () -> seguro(() ->
                            publicarTodos("reservaciones", ReservacionService.todas(), Reservacion::folioReservacion, Reservacion::aMapa))))),
                    BorderLayout.SOUTH);
            add(arriba, BorderLayout.NORTH);
            add(new JScrollPane(t), BorderLayout.CENTER);
            cargar();
        }

        private void cargar() {
            seguro(() -> {
                m.setRowCount(0);
                for (Reservacion r : ReservacionService.todas())
                    m.addRow(new Object[]{r.folioReservacion(), r.fecha(), r.horaInicio(), r.horaFin(), r.laboratorio(), r.claveEquipo(), r.matricula()});
            });
        }

        private void reservar() {
            seguro(() -> {
                Reservacion r = ReservacionService.reservar(laboratorio.getText(), equipo.getText(), fecha.getText(),
                        inicio.getText(), fin.getText(), matricula.getText());
                avisar("✔ Reservación guardada con folio " + r.folioReservacion() + ".  "
                        + Main.avisoPublicar("reservaciones", r.folioReservacion(), r.aMapa()));
                cargar();
            });
        }

        private void cancelar() {
            seguro(() -> {
                String folio = ReservacionService.obtener(seleccionado(t, "una reservación")).folioReservacion();
                if (!confirmar("¿Cancelar la reservación " + folio + " (también de la API)?")) return;
                String api = Main.avisoBorrar("reservaciones", folio);
                ReservacionService.cancelar(folio);
                avisar("✔ Reservación " + folio + " cancelada.  " + api);
                cargar();
            });
        }
    }

    // ---------------- API ----------------
    private final class PanelApi extends JPanel {
        private final DefaultTableModel m = modelo();
        private final JLabel titulo = new JLabel(" ");

        PanelApi() {
            super(new BorderLayout());
            JPanel arriba = new JPanel(new GridLayout(0, 1));
            arriba.add(fila(new JLabel("Consultar en " + Config.API_BASE + "/api/v1:")));
            arriba.add(fila(boton("inscritos_practicas (módulo C)", () -> ver("inscritos_practicas")),
                    boton("materiales (catálogo)", () -> ver("materiales")),
                    boton("reporte_accidentes", () -> ver("reporte_accidentes"))));
            arriba.add(fila(boton("resultados_practicas", () -> ver("resultados_practicas")),
                    boton("prestamos", () -> ver("prestamos")),
                    boton("reservaciones", () -> ver("reservaciones"))));
            arriba.add(fila(new JLabel("Si el módulo está en modo prueba, la API muestra datos de ejemplo del profesor en lugar de lo publicado.")));
            arriba.add(fila(titulo));
            add(arriba, BorderLayout.NORTH);
            add(new JScrollPane(tabla(m)), BorderLayout.CENTER);
        }

        private void ver(String recurso) {
            seguro(() -> {
                JsonNode lista = ApiClient.lista(recurso);
                LinkedHashSet<String> cols = new LinkedHashSet<>();
                for (JsonNode n : lista) n.fieldNames().forEachRemaining(cols::add);
                m.setDataVector(new Object[0][], cols.toArray());
                for (JsonNode n : lista) {
                    Object[] fila = new Object[cols.size()];
                    int i = 0;
                    for (String c : cols) fila[i++] = n.path(c).isMissingNode() ? "" : n.path(c).asText();
                    m.addRow(fila);
                }
                titulo.setText(recurso + ": " + lista.size() + " registros");
            });
        }
    }
}
