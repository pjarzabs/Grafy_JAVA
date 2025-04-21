import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class GraphVisualizer extends JFrame {
    private GraphPanel graphPanel;

    public GraphVisualizer() {
        super("Graph Visualizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initMenuBar();

        graphPanel = new GraphPanel();
        add(graphPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem loadItem = new JMenuItem("Load Graph...");
        loadItem.addActionListener(e -> onLoadGraph());
        fileMenu.add(loadItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void onLoadGraph() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        try {
            Graph graph = GraphLoader.loadFromFile(file);
            if (graph.n == 0) {
                JOptionPane.showMessageDialog(this,
                        "Loaded graph is empty.",
                        "Load Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            graphPanel.setGraph(graph);
            graphPanel.revalidate();
            graphPanel.repaint();
            JOptionPane.showMessageDialog(this,
                    "Graph loaded: " + graph.n + " vertices, " + graph.edgeCount() + " edges.",
                    "Load Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading graph: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GraphVisualizer::new);
    }
}

class Graph {
    int n;
    boolean[][] adj;
    int[] group;

    public Graph(int n) {
        this.n = n;
        adj = new boolean[n][n];
        group = new int[n];
    }

    public int edgeCount() {
        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j]) count++;
            }
        }
        return count;
    }
}

class GraphLoader {
    public static Graph loadFromFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Znajdź nagłówek macierzy
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("Macierz")) break;
            }
            if (line == null) throw new IllegalArgumentException("Adjacency matrix header not found");

            List<boolean[]> rows = new ArrayList<>();
            // Wczytaj wiersze macierzy
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("[")) break;
                String content = line.substring(1, line.length() - 1);
                String[] tokens = content.split("\\s+");
                boolean[] row = new boolean[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    String tok = tokens[i].replace(",", "").replace(".", "").trim();
                    if (tok.isEmpty()) throw new IllegalArgumentException("Invalid matrix token: " + tokens[i]);
                    row[i] = tok.equals("1");
                }
                rows.add(row);
            }
            int n = rows.size();
            Graph graph = new Graph(n);
            for (int i = 0; i < n; i++) {
                if (rows.get(i).length != n)
                    throw new IllegalArgumentException("Matrix is not square at row " + i);
                graph.adj[i] = rows.get(i);
            }

            // Znajdź sekcję grup
            while (line != null && !line.trim().startsWith("Grupa")) {
                line = br.readLine();
            }
            if (line == null) throw new IllegalArgumentException("Groups section not found");

            // Wczytaj przydziały do grup
            for (int g = 0; g < 3; g++) {
                if (!line.trim().startsWith("Grupa " + g + ":"))
                    throw new IllegalArgumentException("Expected Grupa " + g + ", found: " + line);
                String[] parts = line.split(":", 2);
                String[] items = parts[1].trim().split("\\s+");
                for (String item : items) {
                    String tok = item.replace(",", "").replace(".", "").trim();
                    if (tok.isEmpty()) continue;
                    int v = Integer.parseInt(tok);
                    if (v < 0 || v >= n) throw new IllegalArgumentException("Vertex index out of bounds: " + v);
                    graph.group[v] = g;
                }
                line = br.readLine();
            }
            return graph;
        }
    }
}

class GraphPanel extends JPanel {
    private Graph graph;
    private Point[] positions;
    private final Color[] colors = {Color.RED, Color.GREEN.darker(), Color.BLUE};

    public GraphPanel() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                computePositions();
                repaint();
            }
        });
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
        computePositions();
    }

    private void computePositions() {
        if (graph == null || getWidth() <= 0 || getHeight() <= 0) return;
        int n = graph.n;
        positions = new Point[n];
        int w = getWidth();
        int h = getHeight();
        int radius = Math.min(w, h) / 2 - 50;
        Point center = new Point(w / 2, h / 2);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            int x = center.x + (int) (radius * Math.cos(angle));
            int y = center.y + (int) (radius * Math.sin(angle));
            positions[i] = new Point(x, y);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (graph == null || positions == null) return;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Rysuj krawędzie
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < graph.n; i++) {
            for (int j = i + 1; j < graph.n; j++) {
                if (graph.adj[i][j]) {
                    Point p1 = positions[i];
                    Point p2 = positions[j];
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }

        // Rysuj wierzchołki
        int nodeSize = 20;
        for (int i = 0; i < graph.n; i++) {
            Point p = positions[i];
            int gId = graph.group[i];
            g2.setColor(colors[gId % colors.length]);
            g2.fillOval(p.x - nodeSize/2, p.y - nodeSize/2, nodeSize, nodeSize);
            g2.setColor(Color.BLACK);
            g2.drawOval(p.x - nodeSize/2, p.y - nodeSize/2, nodeSize, nodeSize);
            String label = String.valueOf(i);
            FontMetrics fm = g2.getFontMetrics();
            int lx = p.x - fm.stringWidth(label)/2;
            int ly = p.y + fm.getAscent()/2;
            g2.drawString(label, lx, ly);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        positions = null;
    }
}
