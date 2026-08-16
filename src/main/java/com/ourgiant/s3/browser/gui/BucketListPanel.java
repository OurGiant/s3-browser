package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.BucketGridModel;
import com.ourgiant.s3.browser.core.BucketNameFilter;
import com.ourgiant.s3.browser.core.BucketRequests;
import com.ourgiant.s3.browser.model.BucketSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Top-level bucket list - the landing view after connecting. Double-click a bucket (or select
 * it and click Open) to browse its objects (see ObjectBrowserPanel).
 *
 * <p>Loads the connected profile's *entire* bucket list in the background on connect/refresh
 * (looping ListBuckets pages internally - see loadInitial()) rather than the paginated
 * Load-More pattern used elsewhere in this family. That's a deliberate departure: the search
 * field only searches what's actually in the table, so a paginated list would silently miss
 * any bucket not yet paged in - loading everything up front is what makes "search for a
 * bucket" actually work, not just "search the first 50."
 */
public class BucketListPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(BucketListPanel.class);

    // Per-call page size for the internal load-everything loop - not a user-facing page size.
    private static final int FETCH_PAGE_SIZE = 1000;

    private final Frame owner;
    private final Consumer<String> onOpenBucket;

    private S3Client s3;
    private DefaultTableModel tableModel;
    private JTable table;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private final List<BucketSummary> allBuckets = new ArrayList<>();

    public BucketListPanel(Frame owner, Consumer<String> onOpenBucket) {
        this.owner = owner;
        this.onOpenBucket = onOpenBucket;
        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadInitial());
        JButton openButton = new JButton("Open");
        openButton.addActionListener(e -> openSelectedBucket());
        topPanel.add(new JLabel("Buckets"));
        topPanel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        topPanel.add(searchField);
        topPanel.add(refreshButton);
        topPanel.add(openButton);
        statusLabel = new JLabel(" ");
        topPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(BucketGridModel.COLUMN_NAMES.toArray(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        // Bucket names are lower-risk than object keys (creating a bucket needs broader IAM
        // permission than writing an object into an existing one) but the fix is free and
        // keeps this table consistent with ObjectBrowserPanel's - see that file's identical
        // block for the full html.disable rationale (Swing JLabel-based renderers silently
        // render text starting with "<html>", including fetching remote images).
        TableCellRenderer defaultRenderer = table.getDefaultRenderer(Object.class);
        if (defaultRenderer instanceof JComponent jComponent) {
            jComponent.putClientProperty("html.disable", Boolean.TRUE);
        }
        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedBucket();
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setClient(S3Client s3) {
        this.s3 = s3;
    }

    /** Loads every bucket in the background, paging internally - see class Javadoc for why. */
    public void loadInitial() {
        allBuckets.clear();
        tableModel.setRowCount(0);
        searchField.setText("");
        searchField.setEnabled(false);
        statusLabel.setText("Loading buckets...");

        new SwingWorker<Void, List<BucketSummary>>() {
            @Override
            protected Void doInBackground() {
                String token = null;
                do {
                    ListBucketsResponse response = s3.listBuckets(BucketRequests.build(FETCH_PAGE_SIZE, token));
                    List<BucketSummary> page = BucketGridModel.toSummaries(response.buckets());
                    if (!page.isEmpty()) {
                        publish(page);
                    }
                    token = response.continuationToken();
                } while (token != null && !token.isEmpty());
                return null;
            }

            @Override
            protected void process(List<List<BucketSummary>> chunks) {
                for (List<BucketSummary> chunk : chunks) {
                    for (BucketSummary summary : chunk) {
                        allBuckets.add(summary);
                        tableModel.addRow(BucketGridModel.formatRow(summary).toArray());
                    }
                }
                statusLabel.setText(allBuckets.size() + " bucket" + (allBuckets.size() == 1 ? "" : "s"));
            }

            @Override
            protected void done() {
                searchField.setEnabled(true);
                try {
                    get();
                } catch (Exception e) {
                    log.error("Error loading buckets", e);
                    statusLabel.setText(" ");
                    JOptionPane.showMessageDialog(BucketListPanel.this,
                        "Error loading buckets: " + rootMessage(e),
                        "Load Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (allBuckets.isEmpty()) {
                    statusLabel.setText("0 buckets");
                    JOptionPane.showMessageDialog(BucketListPanel.this, "No buckets found for this profile.");
                }
            }
        }.execute();
    }

    private void applyFilter() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            rowSorter.setRowFilter(null);
            return;
        }
        rowSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                String name = (String) entry.getValue(0);
                return BucketNameFilter.matches(name, query);
            }
        });
    }

    private void openSelectedBucket() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= allBuckets.size()) {
            return;
        }
        onOpenBucket.accept(allBuckets.get(modelRow).name);
    }

    private String rootMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
