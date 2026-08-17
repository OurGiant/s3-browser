package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.GetObjectRequests;
import com.ourgiant.s3.browser.core.ObjectGridModel;
import com.ourgiant.s3.browser.core.ObjectListRequests;
import com.ourgiant.s3.browser.core.S3Arns;
import com.ourgiant.s3.browser.model.S3Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prefix/folder-style browser for a single bucket's objects. "Folders" are S3 common prefixes,
 * not real objects (see core.ObjectGridModel) - double-clicking one navigates deeper without a
 * full-bucket listing; double-clicking a real object opens its metadata (see
 * ObjectDetailDialog). The breadcrumb row rebuilds on every navigation so any ancestor segment
 * (or the bucket root) is a one-click jump back, not just "Up one level." The Upload button
 * opens UploadDialog, uploading into whatever prefix is currently being browsed; a successful
 * upload refreshes the current listing so the new object shows up immediately. The Download
 * button saves the selected object to a local file via GetObject, mirroring Upload's
 * overwrite-confirmation caution in reverse (see downloadSelectedEntry). Copy Bucket ARN/Copy
 * Bucket URL copy the current bucket's identifiers to the clipboard (see core.S3Arns); the
 * per-object equivalents live in ObjectDetailDialog.
 */
public class ObjectBrowserPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ObjectBrowserPanel.class);
    private static final int PAGE_SIZE = 1000; // S3's own per-request cap for ListObjectsV2

    private final Frame owner;
    private final Runnable onBackToBuckets;

    private S3Client s3;
    private String currentBucket;
    private String currentPrefix = "";

    private JLabel bucketLabel;
    private JPanel breadcrumbPanel;
    private DefaultTableModel tableModel;
    private JTable table;
    private JButton loadMoreButton;
    private JButton downloadButton;
    private JProgressBar downloadProgressBar;
    private final List<S3Entry> allEntries = new ArrayList<>();
    private String nextToken;

    public ObjectBrowserPanel(Frame owner, Runnable onBackToBuckets) {
        this.owner = owner;
        this.onBackToBuckets = onBackToBuckets;
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new BorderLayout(4, 4));
        JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton backButton = new JButton("All Buckets");
        backButton.addActionListener(e -> onBackToBuckets.run());
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> navigateTo(currentPrefix));
        bucketLabel = new JLabel(" ");
        JButton copyBucketArnButton = new JButton("Copy Bucket ARN");
        copyBucketArnButton.addActionListener(e -> ClipboardUtil.copy(S3Arns.bucketArn(currentBucket)));
        JButton copyBucketUrlButton = new JButton("Copy Bucket URL");
        copyBucketUrlButton.addActionListener(e -> ClipboardUtil.copy(S3Arns.bucketUrl(currentBucket)));
        controlsRow.add(backButton);
        controlsRow.add(bucketLabel);
        controlsRow.add(refreshButton);
        controlsRow.add(copyBucketArnButton);
        controlsRow.add(copyBucketUrlButton);
        topPanel.add(controlsRow, BorderLayout.NORTH);

        breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        breadcrumbPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        topPanel.add(breadcrumbPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(ObjectGridModel.COLUMN_NAMES.toArray(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        // Table cells (object/folder display names, straight from S3 keys) are the same
        // attacker-influenceable data as the breadcrumb buttons above - JTable's shared default
        // renderer for the model's column class (Object.class here, since this model never
        // overrides getColumnClass()) is itself a JLabel, so it needs the same html.disable fix.
        // Setting it once on the shared renderer instance covers every cell in the table, since
        // JTable reuses that single instance across all rows/cells as it paints.
        TableCellRenderer defaultRenderer = table.getDefaultRenderer(Object.class);
        if (defaultRenderer instanceof JComponent jComponent) {
            jComponent.putClientProperty("html.disable", Boolean.TRUE);
        }
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedEntry();
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JButton viewDetailsButton = new JButton("View Details");
        viewDetailsButton.addActionListener(e -> openSelectedEntry());
        JButton uploadButton = new JButton("Upload");
        uploadButton.addActionListener(e -> openUploadDialog());
        downloadButton = new JButton("Download");
        downloadButton.addActionListener(e -> downloadSelectedEntry());
        downloadProgressBar = new JProgressBar();
        downloadProgressBar.setIndeterminate(true);
        downloadProgressBar.setVisible(false);
        loadMoreButton = new JButton("Load More");
        loadMoreButton.addActionListener(e -> loadMore());
        loadMoreButton.setEnabled(false);
        bottomPanel.add(viewDetailsButton);
        bottomPanel.add(uploadButton);
        bottomPanel.add(downloadButton);
        bottomPanel.add(downloadProgressBar);
        bottomPanel.add(loadMoreButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setClient(S3Client s3) {
        this.s3 = s3;
    }

    /** Opens a bucket at its root - the entry point from BucketListPanel. */
    public void openBucket(String bucketName) {
        this.currentBucket = bucketName;
        bucketLabel.putClientProperty("html.disable", Boolean.TRUE);
        bucketLabel.setText("Bucket: " + bucketName);
        navigateTo("");
    }

    private void navigateTo(String prefix) {
        this.currentPrefix = prefix;
        allEntries.clear();
        nextToken = null;
        tableModel.setRowCount(0);
        loadMoreButton.setEnabled(false);
        rebuildBreadcrumb();
        loadMore();
    }

    private void rebuildBreadcrumb() {
        breadcrumbPanel.removeAll();

        JButton rootButton = linkButton(currentBucket);
        rootButton.addActionListener(e -> navigateTo(""));
        breadcrumbPanel.add(rootButton);

        if (!currentPrefix.isEmpty()) {
            String[] segments = currentPrefix.split("/");
            StringBuilder accumulated = new StringBuilder();
            for (String segment : segments) {
                if (segment.isEmpty()) {
                    continue;
                }
                breadcrumbPanel.add(new JLabel(" / "));
                accumulated.append(segment).append("/");
                String target = accumulated.toString();
                JButton segmentButton = linkButton(segment);
                segmentButton.addActionListener(e -> navigateTo(target));
                breadcrumbPanel.add(segmentButton);
            }
        }

        breadcrumbPanel.revalidate();
        breadcrumbPanel.repaint();
    }

    /**
     * text is either the bucket name or an S3 key prefix segment - both attacker-influenceable
     * by anyone who can write into a bucket this app later browses. AbstractButton (JButton's
     * parent) renders its text as HTML - including fetching a remote &lt;img src=...&gt; with
     * no confirmation - whenever the text starts with "&lt;html&gt;", so html.disable is applied
     * unconditionally here, same as ObjectDetailDialog's truncatedLabel.
     */
    private JButton linkButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(new Color(0, 102, 204));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 2, 0, 2));
        return button;
    }

    private void loadMore() {
        try {
            ListObjectsV2Response response = s3.listObjectsV2(
                ObjectListRequests.build(currentBucket, currentPrefix, PAGE_SIZE, nextToken));
            List<S3Entry> entries = ObjectGridModel.toEntries(response, currentPrefix);

            if (entries.isEmpty() && allEntries.isEmpty()) {
                loadMoreButton.setEnabled(false);
                return;
            }

            for (S3Entry entry : entries) {
                allEntries.add(entry);
                tableModel.addRow(ObjectGridModel.formatRow(entry).toArray());
            }

            nextToken = Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
            loadMoreButton.setEnabled(nextToken != null && !nextToken.isEmpty());
        } catch (SdkException e) {
            log.error("Error loading objects for bucket {} prefix {}", currentBucket, currentPrefix, e);
            JOptionPane.showMessageDialog(this,
                "Error loading objects: " + e.getMessage(),
                "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openUploadDialog() {
        new UploadDialog(owner, s3, currentBucket, currentPrefix, () -> navigateTo(currentPrefix)).setVisible(true);
    }

    /**
     * Saves the selected object to a local file via GetObject. Mirrors Upload's
     * overwrite-confirmation caution in reverse: if the chosen local save path already exists,
     * this asks before clobbering it, same "don't silently destroy something" ethos as Upload's
     * HeadObject-before-PutObject check - just against the local filesystem instead of S3, so a
     * plain Files.exists check is enough (no AWS call needed).
     */
    private void downloadSelectedEntry() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select an object to download.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= allEntries.size()) {
            return;
        }
        S3Entry entry = allEntries.get(modelRow);
        if (entry.type != S3Entry.Type.OBJECT) {
            JOptionPane.showMessageDialog(this, "Select an object (not a folder) to download.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save As");
        chooser.setSelectedFile(new File(entry.displayName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destination = chooser.getSelectedFile();

        if (destination.exists()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "\"" + destination.getName() + "\" already exists.\nOverwrite it?",
                "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        String key = entry.key;
        Path destinationPath = destination.toPath();
        downloadButton.setEnabled(false);
        downloadProgressBar.setVisible(true);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // getObject(request, Path) creates the destination file fresh, so clear any
                // existing file the user just confirmed overwriting first.
                Files.deleteIfExists(destinationPath);
                s3.getObject(GetObjectRequests.build(currentBucket, key), destinationPath);
                return null;
            }

            @Override
            protected void done() {
                downloadButton.setEnabled(true);
                downloadProgressBar.setVisible(false);
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.error("Download failed for {}/{}", currentBucket, key, cause);
                    JOptionPane.showMessageDialog(ObjectBrowserPanel.this,
                        "Download failed: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()),
                        "Download Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void openSelectedEntry() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= allEntries.size()) {
            return;
        }
        S3Entry entry = allEntries.get(modelRow);
        if (entry.type == S3Entry.Type.FOLDER) {
            navigateTo(entry.key);
        } else {
            new ObjectDetailDialog(owner, s3, currentBucket, entry).setVisible(true);
        }
    }
}
