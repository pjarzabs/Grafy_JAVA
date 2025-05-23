import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GraphVisualizer extends JFrame {
    private GraphPanel graphPanel;

    public GraphVisualizer() {
        super("Graph Visualizer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
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
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Graph graph = GraphLoader.loadFromFile(chooser.getSelectedFile());
            graphPanel.setGraph(graph);
            graphPanel.revalidate();
            graphPanel.repaint();
            JOptionPane.showMessageDialog(this,
                    "Loaded: " + graph.n + " vertices, " + graph.edgeCount() + " edges.",
                    "Load Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
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
    List<Point> cellPositions;
    boolean[][] adj;
    int[] group;

    public Graph(List<Point> positions) {
        this.n = positions.size();
        this.cellPositions = positions;
        this.adj = new boolean[n][n];
        this.group = new int[n];
    }

    int edgeCount() {
        int count = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (adj[i][j]) count++;
        return count;
    }
}

class GraphLoader {
    public static Graph loadFromFile(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        // 1) read spatial matrix header
        while ((line = br.readLine()) != null) {
            if (line.trim().startsWith("Macierz")) break;
        }
        if (line == null) throw new IllegalArgumentException("Matrix header not found");
        // read matrix rows
        List<boolean[]> mat = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("[")) break;
            String content = line.substring(1, line.length() - 1);
            String[] tokens = content.split("\\s+");
            boolean[] row = new boolean[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                String tok = tokens[i].replace(".", "").trim();
                if (!tok.equals("0") && !tok.equals("1"))
                    throw new IllegalArgumentException("Invalid token: " + tokens[i]);
                row[i] = tok.equals("1");
            }
            mat.add(row);
        }
        int size = mat.size();
        // build cellPositions: for each matrix true cell, assign a vertex ID in row-major
        List<Point> positions = new ArrayList<>();
        int[][] vidMap = new int[size][size];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (mat.get(r)[c]) {
                    vidMap[r][c] = positions.size();
                    positions.add(new Point(c, r));
                } else {
                    vidMap[r][c] = -1;
                }
            }
        }
        Graph graph = new Graph(positions);
        // 2) read connection list header
        while (line != null && !line.trim().startsWith("Lista polaczen")) {
            line = br.readLine();
        }
        if (line == null) throw new IllegalArgumentException("Connection list header not found");
        // read edges
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.contains("-")) break;
            String[] parts = line.split("\\s*-\\s*");
            int v1 = Integer.parseInt(parts[0].trim());
            int v2 = Integer.parseInt(parts[1].trim());
            graph.adj[v1][v2] = graph.adj[v2][v1] = true;
        }
        // 3) attempt to read groups; if not present, skip grouping
        boolean hasGroups = false;
        while (line != null) {
            if (line.trim().startsWith("Grupa")) { hasGroups = true; break; }
            line = br.readLine();
        }
        if (hasGroups) {
            for (int g = 0; g < 3; g++) {
                if (line == null || !line.trim().startsWith("Grupa " + g + ":"))
                    throw new IllegalArgumentException("Expected Grupa " + g + " but not found");
                String[] parts = line.split(":", 2);
                String[] items = parts[1].trim().split("\\s+");
                for (String item : items) {
                    String tok = item.replaceAll("[^0-9]", "");
                    if (tok.isEmpty()) continue;
                    int vid = Integer.parseInt(tok);
                    if (vid < 0 || vid >= graph.n)
                        throw new IllegalArgumentException("Vertex id out of range: " + vid);
                    graph.group[vid] = g;
                }
                line = br.readLine();
            }
        }
        br.close();
        return graph;
    }
}

class GraphPanel extends JPanel {
    private Graph graph;
    private Point[] positions;
    private final Color[] colors = {Color.RED, Color.GREEN.darker(), Color.BLUE};
    GraphPanel() {
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                computePositions();
                repaint();
            }
        });
    }
    void setGraph(Graph graph) {
        this.graph = graph;
        computePositions();
    }
    private void computePositions() {
        if (graph == null) return;
        int w = getWidth(), h = getHeight();
        positions = new Point[graph.n];
        int maxC = 0, maxR = 0;
        for (Point p : graph.cellPositions) {
            maxC = Math.max(maxC, p.x);
            maxR = Math.max(maxR, p.y);
        }
        int cols = maxC + 1, rows = maxR + 1;
        int cellW = w / (cols + 1), cellH = h / (rows + 1);
        for (int i = 0; i < graph.n; i++) {
            Point grid = graph.cellPositions.get(i);
            positions[i] = new Point(cellW * (grid.x + 1), cellH * (grid.y + 1));
        }
    }
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (graph == null) return;
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // draw edges
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < graph.n; i++) {
            for (int j = i + 1; j < graph.n; j++) {
                if (graph.adj[i][j]) {
                    Point p1 = positions[i], p2 = positions[j];
                    g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                }
            }
        }
        // draw nodes with group colors
        int size = 20;
        for (int i = 0; i < graph.n; i++) {
            Point p = positions[i];
            int gId = graph.group[i];
            g2.setColor(colors[gId % colors.length]);
            g2.fillOval(p.x - size/2, p.y - size/2, size, size);
            g2.setColor(Color.BLACK);
            g2.drawOval(p.x - size/2, p.y - size/2, size, size);
            // label
            String lbl = String.valueOf(i);
            FontMetrics fm = g2.getFontMetrics();
            int lx = p.x - fm.stringWidth(lbl)/2;
            int ly = p.y + fm.getAscent()/2;
            g2.drawString(lbl, lx, ly);
        }
    }
}
