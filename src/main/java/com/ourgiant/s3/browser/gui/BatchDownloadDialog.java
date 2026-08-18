package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.GetObjectRequests;
import com.ourgiant.s3.browser.core.RemoteDownloadItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;

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
 * Batch-downloads a planned list of S3 objects (see core.BatchDownloadPlanner) to local paths
 * already computed by the caller. The caller confirms the batch's total object count/size (and
 * picks the destination directory) before ever constructing this dialog (see
 * ObjectBrowserPanel.downloadSelectedEntries) - by the time this dialog exists, the user has
 * already agreed to the batch's size, so it goes straight into checking for local overwrites.
 * Every actual download still goes through core.GetObjectRequests, same as the single-object
 * Download - this only adds batch-level orchestration around that same call: one aggregate
 * overwrite confirmation instead of per-file dialogs (backed by a cheap local Files.exists
 * check, not an AWS HeadObject call, since the destinations are local paths), determinate
 * per-batch progress instead of an indeterminate spinner, and a skip-and-continue-on-failure
 * policy with a real end-of-batch summary - the exact same three shapes BatchUploadDialog uses,
 * deliberately mirrored rather than redesigned.
 */
public class BatchDownloadDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(BatchDownloadDialog.class);

    private final S3Client s3;
    private final String bucket;
    private final List<RemoteDownloadItem> allItems;
    private final Runnable onDownloadComplete;

    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea summaryArea;
    private JScrollPane summaryScroll;

    public BatchDownloadDialog(Frame owner, S3Client s3, String bucket, List<RemoteDownloadItem> items, Runnable onDownloadComplete) {
        super(owner, "Download " + items.size() + " items from " + bucket, true);
        this.s3 = s3;
        this.bucket = bucket;
        this.allItems = items;
        this.onDownloadComplete = onDownloadComplete;

        setSize(560, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(8, 8));
        add(buildBody(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        checkLocalOverwrites();
    }

    private JPanel buildBody() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        statusLabel = new JLabel("Checking local files...");
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

    private void checkLocalOverwrites() {
        new SwingWorker<List<RemoteDownloadItem>, Integer>() {
            @Override
            protected List<RemoteDownloadItem> doInBackground() {
                List<RemoteDownloadItem> existing = new ArrayList<>();
                for (int i = 0; i < allItems.size(); i++) {
                    RemoteDownloadItem item = allItems.get(i);
                    if (Files.exists(item.localPath())) {
                        existing.add(item);
                    }
                    publish(i + 1);
                }
                return existing;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                statusLabel.setText("Checking local files... (" + done + "/" + allItems.size() + ")");
                progressBar.setValue(done);
            }

            @Override
            protected void done() {
                List<RemoteDownloadItem> existing;
                try {
                    existing = get();
                } catch (Exception e) {
                    finishWithError("Error checking local files", e);
                    return;
                }

                if (existing.isEmpty()) {
                    startDownloadPhase(allItems);
                    return;
                }

                Object[] options = {"Overwrite All", "Skip Existing", "Cancel"};
                int choice = JOptionPane.showOptionDialog(BatchDownloadDialog.this,
                    existing.size() + " of " + allItems.size() + " files already exist locally.",
                    "Confirm Overwrite", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[1]);

                if (choice == 0) {
                    startDownloadPhase(allItems);
                } else if (choice == 1) {
                    List<RemoteDownloadItem> toDownload = new ArrayList<>(allItems);
                    toDownload.removeAll(existing);
                    startDownloadPhase(toDownload);
                } else {
                    dispose();
                }
            }
        }.execute();
    }

    private void startDownloadPhase(List<RemoteDownloadItem> toDownload) {
        int skipped = allItems.size() - toDownload.size();
        progressBar.setMaximum(Math.max(toDownload.size(), 1));
        progressBar.setValue(0);
        statusLabel.setText("Downloading... (0/" + toDownload.size() + ")");

        new SwingWorker<BatchDownloadResult, Integer>() {
            @Override
            protected BatchDownloadResult doInBackground() {
                int downloaded = 0;
                List<String> failures = new ArrayList<>();
                for (int i = 0; i < toDownload.size(); i++) {
                    RemoteDownloadItem item = toDownload.get(i);
                    try {
                        if (item.localPath().getParent() != null) {
                            Files.createDirectories(item.localPath().getParent());
                        }
                        // getObject(request, Path) creates the destination file fresh, so clear
                        // any existing file the user just confirmed overwriting first.
                        Files.deleteIfExists(item.localPath());
                        s3.getObject(GetObjectRequests.build(bucket, item.key()), item.localPath());
                        downloaded++;
                    } catch (IOException | RuntimeException e) {
                        log.warn("Failed to download {} to {}", item.key(), item.localPath(), e);
                        failures.add(item.key() + ": "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                    }
                    publish(i + 1);
                }
                return new BatchDownloadResult(downloaded, failures);
            }

            @Override
            protected void process(List<Integer> chunks) {
                int done = chunks.get(chunks.size() - 1);
                statusLabel.setText("Downloading... (" + done + "/" + toDownload.size() + ")");
                progressBar.setValue(done);
            }

            @Override
            protected void done() {
                BatchDownloadResult result;
                try {
                    result = get();
                } catch (Exception e) {
                    finishWithError("Download failed", e);
                    return;
                }
                showSummary(result, skipped);
                if (onDownloadComplete != null) {
                    onDownloadComplete.run();
                }
            }
        }.execute();
    }

    private void showSummary(BatchDownloadResult result, int skipped) {
        statusLabel.setText(result.downloaded() + " downloaded, " + skipped + " skipped, "
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

    private record BatchDownloadResult(int downloaded, List<String> failures) {
    }
}
