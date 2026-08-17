package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.AwsConsoleLauncher;
import com.ourgiant.s3.browser.core.BatchUploadPlanner;
import com.ourgiant.s3.browser.core.GetObjectRequests;
import com.ourgiant.s3.browser.core.LocalUploadItem;
import com.ourgiant.s3.browser.core.ObjectGridModel;
import com.ourgiant.s3.browser.core.ObjectListRequests;
import com.ourgiant.s3.browser.core.S3Arns;
import com.ourgiant.s3.browser.core.S3ConsoleUrls;
import com.ourgiant.s3.browser.core.SizeFormatter;
import com.ourgiant.s3.browser.model.S3Entry;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
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
import javax.swing.DropMode;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
 * per-object equivalents live in ObjectDetailDialog. Open in Console signs the connected
 * profile into the AWS Console via the federation endpoint (see core.AwsConsoleLauncher) and
 * opens it in a fresh, disposable ChromeDriver session scoped to the current bucket+prefix -
 * ported from aws-idp-saml-ui's same feature. Dragging a single local file onto the object
 * table pre-selects it in the same UploadDialog the Upload button opens (see
 * fileDropTransferHandler) - a faster path to the picker, not a bypass of it; the user still
 * confirms via the Upload button, and a multi-file drop is rejected with a message rather than
 * silently reopening upload's single-file scope (that's what Upload Multiple is for). Upload
 * Multiple opens a FILES_AND_DIRECTORIES multi-select picker, plans the selection into a flat
 * file-to-key list (see core.BatchUploadPlanner - a folder is walked recursively, preserving its
 * own name as a subprefix), confirms the batch's total count/size up front, then hands the plan
 * to BatchUploadDialog for aggregate overwrite confirmation, determinate progress, and a
 * skip-and-continue-on-failure summary.
 * silently reopening upload's single-file scope.
 */
public class ObjectBrowserPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ObjectBrowserPanel.class);
    private static final int PAGE_SIZE = 1000; // S3's own per-request cap for ListObjectsV2

    private final Frame owner;
    private final Runnable onBackToBuckets;
    private final List<WebDriver> openConsoleDrivers = Collections.synchronizedList(new ArrayList<>());

    private S3Client s3;
    private AwsCredentialsProvider consoleCredentialsProvider;
    private String consoleRegion;
    private String currentBucket;
    private String currentPrefix = "";

    private JLabel bucketLabel;
    private JPanel breadcrumbPanel;
    private DefaultTableModel tableModel;
    private JTable table;
    private JButton loadMoreButton;
    private JButton downloadButton;
    private JProgressBar downloadProgressBar;
    private JButton openInConsoleButton;
    private final List<S3Entry> allEntries = new ArrayList<>();
    private String nextToken;

    public ObjectBrowserPanel(Frame owner, Runnable onBackToBuckets) {
        this.owner = owner;
        this.onBackToBuckets = onBackToBuckets;
        buildUi();
        Runtime.getRuntime().addShutdownHook(new Thread(this::quitOpenConsoleDrivers, "console-driver-cleanup"));
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
        openInConsoleButton = new JButton("Open in Console");
        openInConsoleButton.addActionListener(e -> openInConsole());
        controlsRow.add(backButton);
        controlsRow.add(bucketLabel);
        controlsRow.add(refreshButton);
        controlsRow.add(copyBucketArnButton);
        controlsRow.add(copyBucketUrlButton);
        controlsRow.add(openInConsoleButton);
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
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(fileDropTransferHandler());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        JButton viewDetailsButton = new JButton("View Details");
        viewDetailsButton.addActionListener(e -> openSelectedEntry());
        JButton uploadButton = new JButton("Upload");
        uploadButton.addActionListener(e -> openUploadDialog());
        JButton uploadMultipleButton = new JButton("Upload Multiple...");
        uploadMultipleButton.addActionListener(e -> openBatchUploadDialog());
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
        bottomPanel.add(uploadMultipleButton);
        bottomPanel.add(downloadButton);
        bottomPanel.add(downloadProgressBar);
        bottomPanel.add(loadMoreButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setClient(S3Client s3) {
        this.s3 = s3;
    }

    /** Credentials/region behind Open in Console (see openInConsole) - separate from setClient
     *  since building the console federation URL needs the raw temporary credentials, not just
     *  an already-built S3Client. */
    public void setConsoleContext(AwsCredentialsProvider credentialsProvider, String region) {
        this.consoleCredentialsProvider = credentialsProvider;
        this.consoleRegion = region;
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
        openUploadDialog(null);
    }

    private void openUploadDialog(File preSelectedFile) {
        UploadDialog dialog = new UploadDialog(owner, s3, currentBucket, currentPrefix, () -> navigateTo(currentPrefix));
        if (preSelectedFile != null) {
            dialog.preSelectFile(preSelectedFile);
        }
        dialog.setVisible(true);
    }

    /**
     * Lets the user pick any mix of local files and/or folders in one dialog, plans the
     * selection into a flat file-to-key list (see core.BatchUploadPlanner), confirms the
     * batch's total count/size up front (a safety net against an unexpectedly huge folder), and
     * only then hands the confirmed plan to BatchUploadDialog for the rest (aggregate overwrite
     * confirmation, progress, failure summary).
     */
    private void openBatchUploadDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose files or folders to upload");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selection = chooser.getSelectedFiles();
        if (selection.length == 0) {
            return;
        }

        List<LocalUploadItem> planned = BatchUploadPlanner.plan(List.of(selection), currentPrefix);
        if (planned.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No files found in that selection.");
            return;
        }

        long totalBytes = 0;
        for (LocalUploadItem item : planned) {
            try {
                totalBytes += Files.size(item.localPath());
            } catch (IOException e) {
                // best-effort total for the confirmation dialog - a file that vanishes between
                // planning and sizing just doesn't count toward the estimate shown here
            }
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Upload " + planned.size() + " files (" + SizeFormatter.humanReadable(totalBytes) + ") to "
                + currentBucket + "?",
            "Confirm Batch Upload", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        new BatchUploadDialog(owner, s3, currentBucket, planned, () -> navigateTo(currentPrefix)).setVisible(true);
    }

    /**
     * Accepts a single local file dropped onto the object table and opens it directly in
     * UploadDialog, pre-selected - see the class javadoc. Only javaFileListFlavor is accepted;
     * canImport rejects anything else (e.g. a plain text/string drag) outright. A multi-file
     * drop is accepted by canImport (the flavor itself is still a file-list) but rejected in
     * importData with a message, so drag-and-drop doesn't implicitly reopen upload's
     * single-file scope decision.
     */
    private TransferHandler fileDropTransferHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                List<File> files;
                try {
                    files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    log.warn("Failed to read dropped file list", e);
                    return false;
                }
                if (files.size() != 1) {
                    JOptionPane.showMessageDialog(ObjectBrowserPanel.this, "Drop a single file to upload.");
                    return false;
                }
                openUploadDialog(files.get(0));
                return true;
            }
        };
    }

    /**
     * Signs the connected profile into the AWS Console (via the federation endpoint - see
     * core.AwsConsoleLauncher) and opens it at the current bucket+prefix in a fresh ChromeDriver
     * session. Each click gets its own driver instance/cookie jar rather than reusing one across
     * clicks, so consoles opened for different profiles or locations don't collide.
     */
    private void openInConsole() {
        if (consoleCredentialsProvider == null) {
            return;
        }

        String bucket = currentBucket;
        String prefix = currentPrefix;
        String region = consoleRegion;
        openInConsoleButton.setEnabled(false);
        openInConsoleButton.setText("Opening...");

        new SwingWorker<WebDriver, Void>() {
            @Override
            protected WebDriver doInBackground() throws Exception {
                AwsCredentials credentials = consoleCredentialsProvider.resolveCredentials();
                String destination = S3ConsoleUrls.bucketPrefixUrl(bucket, prefix, region);
                String loginUrl = AwsConsoleLauncher.buildLoginUrl(credentials, destination);

                ChromeOptions options = new ChromeOptions();
                options.addArguments("--disable-dev-shm-usage");
                WebDriver driver = new ChromeDriver(options);
                try {
                    driver.get(loginUrl);
                } catch (Exception e) {
                    driver.quit();
                    throw e;
                }
                return driver;
            }

            @Override
            protected void done() {
                openInConsoleButton.setEnabled(true);
                openInConsoleButton.setText("Open in Console");
                try {
                    openConsoleDrivers.add(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.error("Failed to open AWS Console for bucket {}", bucket, cause);
                    JOptionPane.showMessageDialog(ObjectBrowserPanel.this,
                        "Failed to open AWS Console: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()),
                        "Console Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Quits every still-open console browser session so none are orphaned after app exit. */
    private void quitOpenConsoleDrivers() {
        synchronized (openConsoleDrivers) {
            for (WebDriver driver : openConsoleDrivers) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Failed to close a console browser window on shutdown", e);
                }
            }
            openConsoleDrivers.clear();
        }
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
