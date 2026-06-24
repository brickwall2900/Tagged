package io.github.brickwall2900.tagged;

import io.github.brickwall2900.swing.core.TargetLocator;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class OptionDialog extends JDialog {
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(OptionDialog.class.getName());

    public OptionDialog(Frame owner) {
        super(owner);

        initContentPane();

        setTitle(BUNDLE.getString("title"));
        pack();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(owner);
    }

    private void initContentPane() {
        JPanel contentPane = new JPanel();
        JPanel optionPane = new JPanel();
        JPanel switchesPane = new JPanel();
        JPanel controlButtonPanel = new JPanel();

        // mfw  i handwrite GroupLayout code by myself
        JCheckBox darkModeCheckbox = new JCheckBox();
        darkModeCheckbox.setText(BUNDLE.getString("switch.darkMode"));
        darkModeCheckbox.setName("DarkMode");
        darkModeCheckbox.setToolTipText(BUNDLE.getString("switch.darkMode.tooltip"));

        JCheckBox fastTargetCheckbox = new JCheckBox();
        fastTargetCheckbox.setText(BUNDLE.getString("switch.fastTarget"));
        fastTargetCheckbox.setName("FastTarget");
        fastTargetCheckbox.setToolTipText(BUNDLE.getString("switch.fastTarget.tooltip"));

        switchesPane.setBorder(BorderFactory.createTitledBorder(null,
                BUNDLE.getString("border.switches"),
                TitledBorder.CENTER,
                TitledBorder.TOP));
        switchesPane.setLayout(new FlowLayout(FlowLayout.LEADING));
        switchesPane.add(darkModeCheckbox);
        switchesPane.add(fastTargetCheckbox);

        JLabel cellSizeLabel = new JLabel(BUNDLE.getString("label.cellSize"));
        JLabel cellPaddingLabel = new JLabel(BUNDLE.getString("label.cellPadding"));
        JLabel threadsLabel = new JLabel(BUNDLE.getString("label.threads"));
        JLabel cacheBufferLabel = new JLabel(BUNDLE.getString("label.cacheBuffer"));
        JLabel cacheSizeLimitLabel = new JLabel(BUNDLE.getString("label.cacheSizeLimit"));

        JSpinner cellSizeField = new JSpinner(new SpinnerNumberModel(32, 32, Short.MAX_VALUE, 1));
        JSpinner cellPaddingField = new JSpinner(new SpinnerNumberModel(0, 0, Short.MAX_VALUE, 1));
        JSpinner threadsField = new JSpinner(new SpinnerNumberModel(1, 1, Runtime.getRuntime().availableProcessors(), 1));
        JSpinner cacheBufferField = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        JSpinner cacheSizeLimitField = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE / 1024 / 1024, 1));

        cellSizeField.setName("CellSize");
        cellPaddingField.setName("CellPadding");
        threadsField.setName("Threads");
        cacheBufferField.setName("CacheBuffer");
        cacheSizeLimitField.setName("CacheSizeLimit");

        cellSizeField.setToolTipText(BUNDLE.getString("label.cellSize.tooltip"));
        cellPaddingField.setToolTipText(BUNDLE.getString("label.cellPadding.tooltip"));
        threadsField.setToolTipText(BUNDLE.getString("label.threads.tooltip"));
        cacheBufferField.setToolTipText(BUNDLE.getString("label.cacheBuffer.tooltip"));
        cacheSizeLimitField.setToolTipText(BUNDLE.getString("label.cacheSizeLimit.tooltip"));

        cellSizeLabel.setToolTipText(BUNDLE.getString("label.cellSize.tooltip"));
        cellPaddingLabel.setToolTipText(BUNDLE.getString("label.cellPadding.tooltip"));
        threadsLabel.setToolTipText(BUNDLE.getString("label.threads.tooltip"));
        cacheBufferLabel.setToolTipText(BUNDLE.getString("label.cacheBuffer.tooltip"));
        cacheSizeLimitLabel.setToolTipText(BUNDLE.getString("label.cacheSizeLimit.tooltip"));

        cellSizeLabel.setLabelFor(cellSizeField);
        cellPaddingLabel.setLabelFor(cellPaddingField);
        threadsLabel.setLabelFor(threadsField);
        cacheBufferLabel.setLabelFor(cacheBufferField);
        cacheSizeLimitLabel.setLabelFor(cacheSizeLimitField);

        JButton applyButton = new JButton(BUNDLE.getString("button.apply"));
        JButton saveButton = new JButton(BUNDLE.getString("button.save"));
        JButton cancelButton = new JButton(BUNDLE.getString("button.cancel"));

        applyButton.setToolTipText(BUNDLE.getString("button.apply.tooltip"));
        saveButton.setToolTipText(BUNDLE.getString("button.save.tooltip"));
        cancelButton.setToolTipText(BUNDLE.getString("button.cancel.tooltip"));

        applyButton.addActionListener(this::onApplyButtonPressed);
        saveButton.addActionListener(this::onSaveButtonPressed);
        cancelButton.addActionListener(this::onCancelButtonPressed);

        GridBagLayout gridBagLayout = new GridBagLayout();
        optionPane.setLayout(gridBagLayout);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);

        // switches pane
        c.gridwidth = 3;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        optionPane.add(switchesPane, c);

        c.gridwidth = 1;
        c.weightx = 0;
        c.gridx = 0;

        c.gridy++;
        optionPane.add(cellSizeLabel, c);

        c.gridy++;
        optionPane.add(cellPaddingLabel, c);

        c.gridy++;
        optionPane.add(threadsLabel, c);

        c.gridy++;
        optionPane.add(cacheBufferLabel, c);

        c.gridy++;
        optionPane.add(cacheSizeLimitLabel, c);

        c.gridwidth = 2;
        c.weightx = 1;
        c.gridx = 1;

        c.gridy = 0;

        c.gridy++;
        optionPane.add(cellSizeField, c);

        c.gridy++;
        optionPane.add(cellPaddingField, c);

        c.gridy++;
        optionPane.add(threadsField, c);

        c.gridy++;
        optionPane.add(cacheBufferField, c);

        c.gridy++;
        optionPane.add(cacheSizeLimitField, c);

        controlButtonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
        controlButtonPanel.add(saveButton);
        controlButtonPanel.add(cancelButton);
        controlButtonPanel.add(applyButton);

        c.weightx = 1;
        c.weighty = 1;
        c.gridwidth = 3;
        c.gridx = 0;
        c.gridy++;
        optionPane.add(new JPanel(), c);

        contentPane.setLayout(new BorderLayout());
        contentPane.add(optionPane, BorderLayout.CENTER);
        contentPane.add(controlButtonPanel, BorderLayout.SOUTH);

        contentPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        setContentPane(contentPane);
    }

    public void loadOptions(ApplicationOptions options) {
        $("DarkMode", JCheckBox.class).setSelected(options.darkMode);
        $("FastTarget", JCheckBox.class).setSelected(options.fastTarget);
        $("CellSize", JSpinner.class).setValue(options.cellSize);
        $("CellPadding", JSpinner.class).setValue(options.cellPadding);
        $("Threads", JSpinner.class).setValue(options.threads);
        $("CacheBuffer", JSpinner.class).setValue(options.cacheBuffer);
        $("CacheSizeLimit", JSpinner.class).setValue(options.cacheSizeLimit);
    }

    private ApplicationOptions getOptions() {
        boolean darkMode = $("DarkMode", JCheckBox.class).isSelected();
        boolean fastTarget = $("FastTarget", JCheckBox.class).isSelected();
        int cellSize = Math.clamp((int) $("CellSize", JSpinner.class).getValue(), 32, Short.MAX_VALUE);
        int cellPadding = Math.clamp((int) $("CellPadding", JSpinner.class).getValue(), 0, Short.MAX_VALUE);
        int threads = Math.clamp((int) $("Threads", JSpinner.class).getValue(), 1, Runtime.getRuntime().availableProcessors());
        int cacheBuffer = Math.clamp((int) $("CacheBuffer", JSpinner.class).getValue(), 0, Integer.MAX_VALUE);
        int cacheSizeLimit = Math.clamp((int) $("CacheSizeLimit", JSpinner.class).getValue(), 0, Integer.MAX_VALUE / 1024 / 1024);
        return new ApplicationOptions(darkMode, fastTarget, cellSize, cellPadding, threads, cacheBuffer, cacheSizeLimit);
    }

    public Consumer<ApplicationOptions> getOnOptionsApplied() {
        return onOptionsApplied;
    }

    public void setOnOptionsApplied(Consumer<ApplicationOptions> onOptionsApplied) {
        this.onOptionsApplied = onOptionsApplied;
    }

    private <T extends Component> T $(String selector, Class<T> as) {
        return as.cast(TargetLocator.getTarget(selector, getContentPane(), this));
    }

    private void onApplyButtonPressed(ActionEvent e) {
        if (onOptionsApplied != null) {
            onOptionsApplied.accept(getOptions());
        }
    }

    private void onSaveButtonPressed(ActionEvent e) {
        onApplyButtonPressed(e);
        dispose();
    }

    private void onCancelButtonPressed(ActionEvent e) {
        dispose();
    }

    private Consumer<ApplicationOptions> onOptionsApplied;

    public record ApplicationOptions(
            boolean darkMode,
            boolean fastTarget,
            int cellSize,
            int cellPadding,
            int threads,
            int cacheBuffer,
            int cacheSizeLimit
    ) { }
}
