package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.HeadObjectRequests;
import com.ourgiant.s3.browser.core.PutObjectRequests;
import com.ourgiant.s3.browser.core.SizeFormatter;
import com.ourgiant.s3.browser.core.UploadKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Upload a single local file to the currently browsed bucket/prefix. This is the app's only
 * mutating AWS action (see README Scope) - uploading to an existing key silently overwrites it
 * (S3 has no versioning by default), so before ever calling PutObject this checks the
 * destination with HeadObject and asks for explicit confirmation if something is already there,
 * mirroring dynamodb-client's delete-confirmation caution for anything that could destroy data.
 */
public class UploadDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(UploadDialog.class);

    private final S3Client s3;
    private final String bucket;
    private final String currentPrefix;
    private final Runnable onUploadComplete;

    private File selectedFile;
    private JLabel fileLabel;
    private JTextField keyField;
    private JButton uploadButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    public UploadDialog(Frame owner, S3Client s3, String bucket, String currentPrefix, Runnable onUploadComplete) {
        super(owner, "Upload to " + bucket, true);
        this.s3 = s3;
        this.bucket = bucket;
        this.currentPrefix = currentPrefix != null ? currentPrefix : "";
        this.onUploadComplete = onUploadComplete;

        setSize(560, 240);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));

        add(buildForm(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Local File:"), gbc);

        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        JButton chooseButton = new JButton("Choose File...");
        chooseButton.addActionListener(e -> chooseFile());
        fileLabel = new JLabel("(no file selected)");
        fileRow.add(chooseButton, BorderLayout.WEST);
        fileRow.add(fileLabel, BorderLayout.CENTER);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(fileRow, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Destination Key:"), gbc);

        keyField = new JTextField(30);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(keyField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(new JLabel("Bucket: " + bucket), gbc);

        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusLabel = new JLabel(" ");
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(progressBar, BorderLayout.EAST);
        panel.add(statusRow, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        uploadButton = new JButton("Upload");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> startUpload());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttons.add(uploadButton);
        buttons.add(closeButton);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a file to upload");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        selectFile(chooser.getSelectedFile());
    }

    /** Pre-selects a file dropped onto the object browser (see ObjectBrowserPanel's
     *  TransferHandler) - call after construction, before setVisible(true). Reuses the exact
     *  same selection/confirmation/upload path as choosing a file via the picker, so a drop
     *  still requires an explicit click on Upload rather than firing immediately. */
    public void preSelectFile(File file) {
        selectFile(file);
    }

    private void selectFile(File file) {
        selectedFile = file;
        fileLabel.setText(selectedFile.getName() + " (" + SizeFormatter.humanReadable(selectedFile.length()) + ")");
        keyField.setText(UploadKeys.defaultKey(currentPrefix, selectedFile.getName()));
        uploadButton.setEnabled(true);
    }

    private void startUpload() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Choose a file first.");
            return;
        }
        String key = keyField.getText().trim();
        if (key.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a destination key.");
            return;
        }

        uploadButton.setEnabled(false);
        statusLabel.setText("Checking destination...");
        progressBar.setVisible(true);

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    s3.headObject(HeadObjectRequests.build(bucket, key));
                    return true; // object exists
                } catch (NoSuchKeyException e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                boolean exists;
                try {
                    exists = get();
                } catch (Exception e) {
                    finishWithError("Error checking destination", e);
                    return;
                }

                if (exists) {
                    progressBar.setVisible(false);
                    int confirm = JOptionPane.showConfirmDialog(UploadDialog.this,
                        "An object already exists at \"" + key + "\" in " + bucket + ".\nOverwrite it?",
                        "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) {
                        uploadButton.setEnabled(true);
                        statusLabel.setText(" ");
                        return;
                    }
                    progressBar.setVisible(true);
                }

                doUpload(key);
            }
        }.execute();
    }

    private void doUpload(String key) {
        statusLabel.setText("Uploading...");
        Path path = selectedFile.toPath();
        String contentType = guessContentType(path);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                s3.putObject(PutObjectRequests.build(bucket, key, contentType), path);
                return null;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                uploadButton.setEnabled(true);
                try {
                    get();
                } catch (Exception e) {
                    finishWithError("Upload failed", e);
                    return;
                }
                statusLabel.setText("Upload complete: " + key);
                if (onUploadComplete != null) {
                    onUploadComplete.run();
                }
            }
        }.execute();
    }

    private void finishWithError(String context, Exception e) {
        progressBar.setVisible(false);
        uploadButton.setEnabled(true);
        statusLabel.setText(" ");
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error("{} for bucket {} ", context, bucket, cause);
        JOptionPane.showMessageDialog(this,
            context + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()),
            "Upload Error", JOptionPane.ERROR_MESSAGE);
    }

    private String guessContentType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return null;
        }
    }
}
