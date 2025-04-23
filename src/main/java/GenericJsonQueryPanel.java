import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;



public class GenericJsonQueryPanel extends JPanel {

    private JTextField keyField, valueField;
    private JButton loadButton, saveButton, inspectButton;
    private JTextArea resultArea;
    private JsonNode rootNode;
    private final ObjectMapper mapper = new ObjectMapper();
    private List<JsonNode> matchedNodes = new ArrayList<>();

    public GenericJsonQueryPanel() {
        setLayout(new BorderLayout());

        keyField = new JTextField();
        valueField = new JTextField();

        JPanel inputPanel = new JPanel(new GridLayout(2, 2));
        inputPanel.add(new JLabel("Key (e.g. user.name):"));
        inputPanel.add(keyField);
        inputPanel.add(new JLabel("Value (e.g. John or 20-30):"));
        inputPanel.add(valueField);

        loadButton = new JButton("Load JSON");
        saveButton = new JButton("Export Results");
        inspectButton = new JButton("Inspect Keys");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loadButton);
        buttonPanel.add(inspectButton);
        buttonPanel.add(saveButton);

        resultArea = new JTextArea(20, 60);
        resultArea.setEditable(false);

        add(inputPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(new JScrollPane(resultArea), BorderLayout.SOUTH);

        loadButton.addActionListener(e -> loadJsonFile());
        saveButton.addActionListener(e -> exportResultsToFile());
        inspectButton.addActionListener(e -> inspectKeys());

        addLiveFilter(keyField);
        addLiveFilter(valueField);
    }

    private void addLiveFilter(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { queryJson(); }
            public void removeUpdate(DocumentEvent e) { queryJson(); }
            public void changedUpdate(DocumentEvent e) { queryJson(); }
        });
    }

    private void loadJsonFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                rootNode = mapper.readTree(selectedFile);
                JOptionPane.showMessageDialog(this, "JSON Loaded Successfully!");
                queryJson();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load JSON: " + ex.getMessage());
            }
        }
    }

    private void queryJson() {
        if (rootNode == null || !rootNode.isArray()) return;

        String keyPath = keyField.getText().trim();
        String value = valueField.getText().trim();

        resultArea.setText("");
        matchedNodes.clear();

        for (JsonNode node : rootNode) {
            JsonNode target = getValueAtKeyPath(node, keyPath);

            if (target == null || target.isMissingNode()) continue;

            boolean matches = false;

            if (value.isEmpty()) {
                matches = true;
            } else if (target.isNumber() && value.contains("-")) {
                try {
                    String[] range = value.split("-");
                    double min = Double.parseDouble(range[0]);
                    double max = Double.parseDouble(range[1]);
                    double targetValue = target.asDouble();
                    matches = targetValue >= min && targetValue <= max;
                } catch (NumberFormatException ignored) {}
            } else {
                matches = target.asText().equalsIgnoreCase(value);
            }

            if (matches) {
                matchedNodes.add(node);
                resultArea.append("Match: " + target.toPrettyString() + "\n→ Full Node:\n" + node.toPrettyString() + "\n\n");
            }
        }
    }

    private JsonNode getValueAtKeyPath(JsonNode node, String path) {
        if (path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (node == null) return null;
            node = node.get(part);
        }
        return node;
    }

    private void inspectKeys() {
        if (rootNode == null) {
            JOptionPane.showMessageDialog(this, "Load a JSON file first.");
            return;
        }

        Set<String> allKeys = new TreeSet<>();
        extractKeyPaths(rootNode, "", allKeys);

        JTextArea area = new JTextArea(String.join("\n", allKeys));
        area.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        JOptionPane.showMessageDialog(this, scrollPane, "Available Keys", JOptionPane.INFORMATION_MESSAGE);
    }

    private void extractKeyPaths(JsonNode node, String prefix, Set<String> keys) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(field -> {
                JsonNode child = node.get(field);
                String fullPath = prefix.isEmpty() ? field : prefix + "." + field;
                keys.add(fullPath);
                extractKeyPaths(child, fullPath, keys);
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                extractKeyPaths(item, prefix, keys);
            }
        }
    }

    private void exportResultsToFile() {
        if (matchedNodes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No matched results to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("filtered.json"));
        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(file, matchedNodes);
                JOptionPane.showMessageDialog(this, "Results saved to " + file.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Generic JSON Query Panel");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new GenericJsonQueryPanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
