package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.core.HeadObjectRequests;
import com.ourgiant.s3.browser.core.ObjectDetailMapper;
import com.ourgiant.s3.browser.core.S3Arns;
import com.ourgiant.s3.browser.core.SizeFormatter;
import com.ourgiant.s3.browser.core.TextTruncation;
import com.ourgiant.s3.browser.model.ObjectDetail;
import com.ourgiant.s3.browser.model.S3Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * Read-only metadata for a single object. Size/storage class/last-modified are already known
 * from the listing (see model.S3Entry) and shown immediately; content-type, encryption,
 * version, ETag, and user metadata require a separate HeadObject call (ListObjectsV2 doesn't
 * carry them), so those load in the background the same way lambda-inspector's
 * FunctionDetailDialog lazily loads its Triggers section.
 */
public class ObjectDetailDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(ObjectDetailDialog.class);

    // Caps how much of a long value (ARN, S3 URL, ETag, a deeply nested Key, ...) a field
    // displays inline. Without this, GridBagLayout sizes the value column to the widest
    // unwrapped string across every row, which can push the panel's preferred width well past
    // the dialog's actual size - shoving the ARN/S3-URL rows' Copy buttons out of view until
    // the window is manually widened. The full value is always still what's copied/shown in
    // the tooltip, never the truncated display text.
    private static final int MAX_LABEL_CHARS = 64;

    private JLabel headStatusLabel;
    private JLabel contentTypeValue;
    private JLabel encryptionValue;
    private JLabel versionValue;
    private JLabel eTagValue;
    private JPanel metadataPanel;

    public ObjectDetailDialog(Frame owner, S3Client s3, String bucket, S3Entry entry) {
        super(owner, "Object: " + entry.displayName, true);
        setLayout(new BorderLayout());

        add(new JScrollPane(buildBody(bucket, entry)), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Size from actual content (like AboutDialog's pack()) rather than a guessed constant,
        // clamped to a sane range: short content doesn't get an oddly cramped/tiny window, and
        // a huge one (e.g. many user-metadata entries) doesn't grow unbounded - it scrolls
        // instead, same as anything longer than the window already does.
        repackWithinBounds();
        setLocationRelativeTo(owner);

        loadHeadDetail(s3, bucket, entry.key);
    }

    private void repackWithinBounds() {
        pack();
        Dimension natural = getSize();
        setSize(
            Math.min(Math.max(natural.width, 520), 760),
            Math.min(Math.max(natural.height, 420), 680));
    }

    private JPanel buildBody(String bucket, S3Entry entry) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        int row = 0;

        row = addField(panel, gbc, row, "Key:", entry.key);
        row = addFieldWithCopy(panel, gbc, row, "ARN:", S3Arns.objectArn(bucket, entry.key));
        row = addFieldWithCopy(panel, gbc, row, "S3 URL:", S3Arns.objectUrl(bucket, entry.key));
        row = addField(panel, gbc, row, "Size:", SizeFormatter.humanReadable(entry.size));
        row = addField(panel, gbc, row, "Storage Class:", entry.storageClass != null ? entry.storageClass : "—");
        row = addField(panel, gbc, row, "Last Modified:", entry.lastModified != null ? entry.lastModified : "—");

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panel.add(new JLabel(" "), gbc);
        row++;

        headStatusLabel = new JLabel("Loading additional metadata...");
        gbc.gridy = row;
        panel.add(headStatusLabel, gbc);
        row++;
        gbc.gridwidth = 1;

        contentTypeValue = new JLabel("—");
        row = addField(panel, gbc, row, "Content-Type:", contentTypeValue);
        encryptionValue = new JLabel("—");
        row = addField(panel, gbc, row, "Encryption:", encryptionValue);
        versionValue = new JLabel("—");
        row = addField(panel, gbc, row, "Version ID:", versionValue);
        eTagValue = new JLabel("—");
        row = addField(panel, gbc, row, "ETag:", eTagValue);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panel.add(new JLabel("User Metadata:"), gbc);
        row++;

        metadataPanel = new JPanel(new GridBagLayout());
        gbc.gridy = row;
        panel.add(metadataPanel, gbc);

        return panel;
    }

    private int addField(JPanel panel, GridBagConstraints gbc, int row, String labelText, String value) {
        return addField(panel, gbc, row, labelText, truncatedLabel(value));
    }

    private int addField(JPanel panel, GridBagConstraints gbc, int row, String labelText, JLabel valueLabel) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(valueLabel, gbc);
        return row + 1;
    }

    /** Same as addField, but with a small Copy button next to the value - for ARN/S3 URL. */
    private int addFieldWithCopy(JPanel panel, GridBagConstraints gbc, int row, String labelText, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);

        JPanel valueRow = new JPanel(new BorderLayout(6, 0));
        JLabel valueLabel = truncatedLabel(value);
        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(e -> ClipboardUtil.copy(value));
        valueRow.add(valueLabel, BorderLayout.CENTER);
        valueRow.add(copyButton, BorderLayout.EAST);

        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(valueRow, gbc);
        return row + 1;
    }

    /** A JLabel showing (a possibly-truncated version of) value, with the full value always
     *  available via tooltip - see MAX_LABEL_CHARS and core.TextTruncation.
     *
     *  <p>value is untrusted (an S3 key, ETag, content-type, or user-metadata value - all
     *  attacker-influenceable by anyone who can PutObject into a bucket this app later
     *  browses). Swing's JLabel silently renders its text as HTML - including fetching a
     *  remote &lt;img src=...&gt; with no confirmation - whenever the text starts with
     *  "&lt;html&gt;", so a crafted key/metadata value could turn a routine object-detail view
     *  into an SSRF/tracking-pixel beacon. html.disable forces plain-text rendering
     *  unconditionally, the same fix applied to gui.ObjectBrowserPanel's breadcrumb buttons and
     *  table cells. */
    private static JLabel truncatedLabel(String value) {
        JLabel label = new JLabel(TextTruncation.truncate(value, MAX_LABEL_CHARS));
        label.putClientProperty("html.disable", Boolean.TRUE);
        if (TextTruncation.wasTruncated(value, MAX_LABEL_CHARS)) {
            label.setToolTipText(value);
        }
        return label;
    }

    private static void setTruncatedText(JLabel label, String value) {
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(TextTruncation.truncate(value, MAX_LABEL_CHARS));
        label.setToolTipText(TextTruncation.wasTruncated(value, MAX_LABEL_CHARS) ? value : null);
    }

    private void loadHeadDetail(S3Client s3, String bucket, String key) {
        new SwingWorker<ObjectDetail, Void>() {
            @Override
            protected ObjectDetail doInBackground() {
                HeadObjectResponse response = s3.headObject(HeadObjectRequests.build(bucket, key));
                return ObjectDetailMapper.toDetail(key, response);
            }

            @Override
            protected void done() {
                ObjectDetail detail;
                try {
                    detail = get();
                } catch (Exception e) {
                    log.warn("Failed to load object metadata for {}", key, e);
                    setTruncatedText(headStatusLabel, "Failed to load additional metadata: " + rootMessage(e));
                    repackWithinBounds();
                    return;
                }
                headStatusLabel.setText(" ");
                setTruncatedText(contentTypeValue, detail.contentType != null ? detail.contentType : "—");
                setTruncatedText(encryptionValue, describeEncryption(detail));
                setTruncatedText(versionValue, detail.versionId != null ? detail.versionId : "—");
                setTruncatedText(eTagValue, detail.eTag != null ? detail.eTag : "—");
                populateMetadata(detail.userMetadata);
                metadataPanel.revalidate();
                // Content loaded after the initial pack() may be wider/taller than the guessed
                // placeholder text was - re-pack within the same clamped bounds so it still fits.
                repackWithinBounds();
            }
        }.execute();
    }

    private String describeEncryption(ObjectDetail detail) {
        if (detail.serverSideEncryption == null) {
            return "None";
        }
        if (detail.sseKmsKeyId != null) {
            return detail.serverSideEncryption + " (" + detail.sseKmsKeyId + ")";
        }
        return detail.serverSideEncryption;
    }

    private void populateMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.WEST;
            metadataPanel.add(new JLabel("(none)"), gbc);
            return;
        }
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            // entry.getKey() is a user-metadata header NAME (x-amz-meta-*), just as
            // attacker-influenceable as its value - same html.disable treatment as truncatedLabel.
            JLabel keyLabel = new JLabel(entry.getKey() + ":");
            keyLabel.putClientProperty("html.disable", Boolean.TRUE);
            keyLabel.setFont(keyLabel.getFont().deriveFont(Font.BOLD));
            metadataPanel.add(keyLabel, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1;
            metadataPanel.add(truncatedLabel(entry.getValue()), gbc);
            row++;
        }
    }

    private String rootMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
