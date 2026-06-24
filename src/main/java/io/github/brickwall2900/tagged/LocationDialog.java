package io.github.brickwall2900.tagged;

import io.github.brickwall2900.swing.core.TargetLocator;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class LocationDialog extends JDialog {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(LocationDialog.class.getName());

    public LocationDialog(Tagged tagged) {
        super(tagged);
        this.tagged = tagged;

        initContentPane();

        setTitle(BUNDLE.getString("title"));
        setModalityType(ModalityType.APPLICATION_MODAL);
        pack();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(tagged);
    }

    private void initContentPane() {
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();

        JLabel header = new JLabel(BUNDLE.getString("label.header"));

        JList<Path> pathList = new JList<>();
        DefaultListModel<Path> listModel = new DefaultListModel<>();
        pathList.setModel(listModel);
        JScrollPane scrollPane = new JScrollPane(pathList);

        JButton addButton = new JButton(BUNDLE.getString("button.add"));
        JButton removeButton = new JButton(BUNDLE.getString("button.remove"));
        JButton closeButton = new JButton(BUNDLE.getString("button.close"));

        addButton.setName("AddButton");
        removeButton.setName("RemoveButton");
        closeButton.setName("CloseButton");

        addButton.addActionListener(this::onAddButtonPressed);
        removeButton.addActionListener(this::onRemoveButtonPressed);
        closeButton.addActionListener(this::onCloseButtonPressed);

        removeButton.setEnabled(false);

        pathList.setName("PathList");
        pathList.addListSelectionListener(this::onListSelected);

        listModel.addAll(tagged.getLocations());

        c.insets = new Insets(4, 4, 4, 4);

        c.gridx = c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        contentPane.add(header, c);

        c.gridy++;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = c.weighty = 1;
        contentPane.add(scrollPane, c);

        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = 1;
        c.weightx = c.weighty = 0;
        c.anchor = GridBagConstraints.SOUTHWEST;
        contentPane.add(addButton, c);

        c.gridx++;
        contentPane.add(removeButton, c);

        c.gridx++;
        c.anchor = GridBagConstraints.SOUTHEAST;
        contentPane.add(closeButton, c);

        contentPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setContentPane(contentPane);
    }

    @SuppressWarnings("unchecked")
    private void onAddButtonPressed(ActionEvent e) {
        Path newLocation = tagged.openFileChooserDirectory(false);
        if (newLocation != null) {
            JList<Path> pathList = $("PathList", JList.class);
            DefaultListModel<Path> listMode = (DefaultListModel<Path>) pathList.getModel();

            tagged.addLocation(newLocation);
            listMode.addElement(newLocation);
        }
    }

    @SuppressWarnings("unchecked")
    private void onRemoveButtonPressed(ActionEvent e) {
        JList<Path> pathList = $("PathList", JList.class);
        DefaultListModel<Path> listMode = (DefaultListModel<Path>) pathList.getModel();

        Path selected = pathList.getSelectedValue();
        if (selected != null) {
            tagged.removeLocation(selected);
            listMode.removeElement(selected);
        }
    }

    private void onListSelected(ListSelectionEvent e) {
        $("RemoveButton", JButton.class).setEnabled(e.getFirstIndex() != -1);
    }

    private void onCloseButtonPressed(ActionEvent e) {
        dispose();
    }

    private <T extends Component> T $(String selector, Class<T> as) {
        return $(getContentPane(), selector, as);
    }

    private <T extends Component> T $(Component src, String selector, Class<T> as) {
        return as.cast(TargetLocator.getTarget(selector, src, this));
    }

    private Tagged tagged;
}
