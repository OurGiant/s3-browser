package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.HeadObjectRequests;
import com.ourgiant.s3.browser.core.LocalUploadItem;
import com.ourgiant.s3.browser.core.PutObjectRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch-uploads a planned list of local files (see core.BatchUploadPlanner) to the currently
 * browsed bucket/prefix. The caller confirms the batch's total file count/size before ever
 * constructing this dialog (see ObjectBrowserPanel.openBatchUploadDialog) - by the time this
 * dialog exists, the user has already agreed to the batch's size, so it goes straight into
 * checking destinations. Every actual upload still goes through core.PutObjectRequests, same as
 * the single-file UploadDialog - this only adds batch-level orchestration around that same call:
 * one aggregate overwrite confirmation instead of per-file dialogs, determinate per-batch
 * progress instead of an indeterminate spinner, and a skip-and-continue-on-failure policy with a
 * real end-of-batch summary instead of a generic error dialog.
 */
public class BatchUploadDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(BatchUploadDialog.class);

    private final S3Client s3;
    private final String bucket;
    private final List<LocalUploadItem> allItems;
    private final Runnable onUploadComplete;

    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea summaryArea;
    private JScrollPane summaryScroll;

    public BatchUploadDialog(Frame owner, S3Client s3, String bucket, List<LocalUploadItem> items, Runnable onUploadComplete) {
        super(owner, "Upload " + items.size() + " items to " + bucket, true);
        this.s3 = s3;
        this.bucket = bucket;
        this.allItems = items;
        this.onUploadComplete = onUploadComplete;

        setSize(560, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        add(buildBody(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        checkDestinations();
    }

    private JPanel buildBody() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        statusLabel = new JLabel("Checking destinations...");
        panel.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, Math.max(allItems.size(), 1));
        panel.add(progressBar, BorderLayout.CENTER);

        summaryArea = new JTextArea();
        summaryArea.setEditable(false);
        summaryScroll = new JScrollPane(summaryArea);
        summaryScroll.setPreferredSize(new Dimension(500, 180));
        summaryScroll.setVisible(false);
        panel.add(summaryScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);
        return panel;
    }

    private void checkDestinations() {
        new SwingWorker<List<LocalUploadItem>, Integer>() {
            @Override
            protected List<LocalUploadItem> doInBackground() {
                List<LocalUploadItem> existing = new ArrayList<>();
                for (int i = 0; i < allItems.size(); i++) {
                    LocalUploadItem item = allItems.get(i);
                    try {
                        s3.headObject(HeadObjectRequests.build(bucket, item.key()));
                        existing.add(item);
                    } catch (NoSuchKeyException e) {
                        // doesn't exist yet - nothing to record
                    }
                    publish(i + 1);
                }
                return existing;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                statusLabel.setText("Checking destinations... (" + done + "/" + allItems.size() + ")");
                progressBar.setValue(done);
            }

            @Override
            protected void done() {
                List<LocalUploadItem> existing;
                try {
                    existing = get();
                } catch (Exception e) {
                    finishWithError("Error checking destinations", e);
                    return;
                }

                if (existing.isEmpty()) {
                    startUploadPhase(allItems);
                    return;
                }

                Object[] options = {"Overwrite All", "Skip Existing", "Cancel"};
                int choice = JOptionPane.showOptionDialog(BatchUploadDialog.this,
                    existing.size() + " of " + allItems.size() + " files already exist at their destination in "
                        + bucket + ".",
                    "Confirm Overwrite", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[1]);

                if (choice == 0) {
                    startUploadPhase(allItems);
                } else if (choice == 1) {
                    List<LocalUploadItem> toUpload = new ArrayList<>(allItems);
                    toUpload.removeAll(existing);
                    startUploadPhase(toUpload);
                } else {
                    dispose();
                }
            }
        }.execute();
    }

    private void startUploadPhase(List<LocalUploadItem> toUpload) {
        int skipped = allItems.size() - toUpload.size();
        progressBar.setMaximum(Math.max(toUpload.size(), 1));
        progressBar.setValue(0);
        statusLabel.setText("Uploading... (0/" + toUpload.size() + ")");

        new SwingWorker<BatchUploadResult, Integer>() {
            @Override
            protected BatchUploadResult doInBackground() {
                int uploaded = 0;
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < toUpload.size(); i++) {
                    LocalUploadItem item = toUpload.get(i);
                    try {
                        String contentType = guessContentType(item);
                        s3.putObject(PutObjectRequests.build(bucket, item.key(), contentType), item.localPath());
                        uploaded++;
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.warn("Failed to upload {} to {}", item.localPath(), item.key(), cause);
                        failures.add(item.key() + ": "
                            + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
                    }
                    publish(i + 1);
                }
                return new BatchUploadResult(uploaded, failures);
            }

            @Override
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                statusLabel.setText("Uploading... (" + done + "/" + toUpload.size() + ")");
                progressBar.setValue(done);
            }

            @Override
            protected void done() {
                BatchUploadResult result;
                try {
                    result = get();
                } catch (Exception e) {
                    finishWithError("Upload failed", e);
                    return;
                }
                showSummary(result, skipped);
                if (onUploadComplete != null) {
                    onUploadComplete.run();
                }
            }
        }.execute();
    }

    private void showSummary(BatchUploadResult result, int skipped) {
        statusLabel.setText(result.uploaded() + " uploaded, " + skipped + " skipped, "
            + result.failures().size() + " failed.");
        progressBar.setValue(progressBar.getMaximum());

        if (!result.failures().isEmpty()) {
            summaryArea.setText(String.join("\n", result.failures()));
            summaryScroll.setVisible(true);
        }
    }

    private void finishWithError(String context, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        log.error("{} for bucket {}", context, bucket, cause);
        statusLabel.setText(context + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
    }

    private String guessContentType(LocalUploadItem item) {
        try {
            return Files.probeContentType(item.localPath());
        } catch (IOException e) {
            return null;
        }
    }

    private record BatchUploadResult(int uploaded, List<String> failures) {
    }
}
