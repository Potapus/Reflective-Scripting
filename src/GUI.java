import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Objects;

public class GUI extends JFrame {
    private final JTable resultsTable;
    private final DefaultTableModel tableModel;
    private final DefaultListModel<File> modelsListModel;
    private final DefaultListModel<File> dataListModel;
    private Controller controller;
    private final JList<File> modelsList;
    private final JList<File> dataList;

    public GUI() {
        setTitle("Modelling framework sample");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel(new BorderLayout());
        modelsListModel = new DefaultListModel<>();
        modelsList = new JList<>(modelsListModel);
        dataListModel = new DefaultListModel<>();
        dataList = new JList<>(dataListModel);

        modelsList.setBorder(BorderFactory.createTitledBorder("Models"));
        dataList.setBorder(BorderFactory.createTitledBorder("Data Files"));

        populateFileList("src/models", modelsListModel);
        populateFileList("src/data", dataListModel);

        JButton runModelButton = new JButton("Run model");
        runModelButton.addActionListener(this::runModel);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(runModelButton);

        leftPanel.add(new JScrollPane(modelsList), BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(dataList), BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);

        tableModel = new DefaultTableModel();
        resultsTable = new JTable(tableModel);
        JScrollPane resultsScrollPane = new JScrollPane(resultsTable);
        resultsScrollPane.setBorder(BorderFactory.createTitledBorder("Results"));
        add(resultsScrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout());
        JButton runScriptButton = new JButton("Run Script");
        JButton runHotScriptButton = new JButton("Create and run ad hoc script");
        runScriptButton.addActionListener(this::runScript);
        runHotScriptButton.addActionListener(this::createAndRunAdHocScript);
        bottomPanel.add(runScriptButton);
        bottomPanel.add(runHotScriptButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void populateFileList(String directoryPath, DefaultListModel<File> listModel) {
        listModel.clear();
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                if (file.isFile() && (file.getName().endsWith(".java") || file.getName().endsWith(".txt"))) {
                    listModel.addElement(file);
                }
            }
        }
    }

    private void runModel(ActionEvent e) {
        File selectedModel = modelsList.getSelectedValue();
        File selectedData = dataList.getSelectedValue();

        if (selectedModel == null || selectedData == null) {
            JOptionPane.showMessageDialog(this, "Select a model and a data file.");
            return;
        }

        try {
            String modelName = selectedModel.getName().replace(".java", "");
            controller = new Controller(modelName);
            controller.readDataFrom(selectedData.getPath());
            controller.runModel();
            updateTable(controller.getResultsAsTsv());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void runScript(ActionEvent e) {
        if (controller == null) {
            JOptionPane.showMessageDialog(this, "Run a model first.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Groovy Files", "groovy"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                controller.runScriptFromFile(fileChooser.getSelectedFile().getAbsolutePath());
                updateTable(controller.getResultsAsTsv());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void createAndRunAdHocScript(ActionEvent e) {
        if (controller == null) {
            JOptionPane.showMessageDialog(this, "Run a model first.");
            return;
        }

        JDialog scriptDialog = new JDialog(this, "Write script.", true);
        scriptDialog.setSize(600, 400);
        scriptDialog.setLocationRelativeTo(this);
        scriptDialog.setLayout(new BorderLayout());

        JTextArea scriptArea = new JTextArea();
        scriptArea.setLineWrap(true);
        scriptArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(scriptArea);
        scriptDialog.add(scrollPane, BorderLayout.CENTER);

        JButton runButton = new JButton("Run Script");
        runButton.addActionListener(event -> {
            String script = scriptArea.getText();
            if (script.trim().isEmpty()) {
                JOptionPane.showMessageDialog(scriptDialog, "Empty script.");
                return;
            }

            try {
                controller.runScript(script);
                updateTable(controller.getResultsAsTsv());
                scriptDialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(scriptDialog, "Error: " + ex.getMessage());
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(runButton);
        scriptDialog.add(buttonPanel, BorderLayout.SOUTH);
        scriptDialog.setVisible(true);
    }

    private void updateTable(String tsvData) {
        String[] rows = tsvData.split("\n");
        if (rows.length == 0) return;

        String[] columnNames = rows[0].split("\t");
        tableModel.setColumnIdentifiers(columnNames);

        tableModel.setRowCount(0);
        for (int i = 1; i < rows.length; i++) {
            String[] values = rows[i].split("\t");
            tableModel.addRow(values);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GUI().setVisible(true));
    }
}
