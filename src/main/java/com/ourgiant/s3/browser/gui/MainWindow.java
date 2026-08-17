package com.ourgiant.s3.browser.gui;

import com.ourgiant.s3.browser.AppPreferences;
import com.ourgiant.s3.browser.ThemeManager;
import com.ourgiant.s3.browser.core.AwsProfileActivityChecker;
import com.ourgiant.s3.browser.core.AwsProfiles;
import com.ourgiant.s3.browser.core.ConnectionMessages;
import com.ourgiant.s3.browser.model.ProfileActivity;
import com.ourgiant.s3.browser.util.AppVersion;
import com.ourgiant.s3.browser.util.UpdateChecker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Top-level window: shows a connection dialog (AWS profile + region, mirroring
 * dynamodb-client's/lambda-inspector's active-profile-aware pattern) on startup, then a
 * CardLayout swapping between the bucket list (BucketListPanel, the landing view) and a single
 * bucket's object browser (ObjectBrowserPanel, prefix/folder-style navigation) - opening a
 * bucket switches cards rather than opening a new window, mirroring how a file manager drills
 * into a folder in place.
 */
public class MainWindow extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);
    private static final String CARD_BUCKETS = "buckets";
    private static final String CARD_OBJECTS = "objects";

    private final AppPreferences prefs = new AppPreferences();
    private String connectedProfile;
    private String connectedAccountId;
    private String connectedRegion;

    private S3Client s3;
    private boolean uiInitialized;
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private BucketListPanel bucketListPanel;
    private ObjectBrowserPanel objectBrowserPanel;

    private List<Image> loadAppIcons() {
        URL iconUrl = MainWindow.class.getResource("/app-icon.png");
        if (iconUrl == null) {
            return List.of();
        }
        Image source = new ImageIcon(iconUrl).getImage();
        List<Image> icons = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128}) {
            icons.add(source.getScaledInstance(size, size, Image.SCALE_SMOOTH));
        }
        return icons;
    }

    public MainWindow() {
        log.debug("MainWindow constructor called");
        setTitle("S3 Browser");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            setIconImages(loadAppIcons());
        } catch (Exception e) {
            // Ignore if icon loading fails
        }

        try {
            String savedProfile = prefs.getAwsProfile(null);
            String savedRegion = prefs.getAwsRegion(null);

            boolean connected = showConnectionDialog(savedProfile, savedRegion);
            log.debug("Connection result: {}", connected);
            if (!connected) {
                log.info("Connection dialog cancelled. Exiting.");
                System.exit(0);
            }
            checkForUpdateAndNotifyIfNewer();
        } catch (Exception e) {
            log.error("Error initializing application", e);
            JOptionPane.showMessageDialog(this,
                "Error initializing: " + e.getMessage(),
                "Initialization Error",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    /**
     * Fires from the constructor, after startup, inside its own SwingWorker so launch is never
     * delayed by network I/O. Dedupes via a "last notified version" preference so the same
     * available version is announced once, not on every launch.
     */
    private void checkForUpdateAndNotifyIfNewer() {
        SwingWorker<Optional<UpdateChecker.ReleaseInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<UpdateChecker.ReleaseInfo> doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                Optional<UpdateChecker.ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    log.warn("Silent startup update check failed", e);
                    return;
                }
                if (release.isEmpty()) {
                    return;
                }
                UpdateChecker.ReleaseInfo info = release.get();
                if (!UpdateChecker.isNewerVersion(info.version(), AppVersion.resolve())) {
                    return;
                }
                if (info.version().equals(prefs.getLastNotifiedUpdateVersion())) {
                    return;
                }
                prefs.setLastNotifiedUpdateVersion(info.version());
                new AboutDialog(MainWindow.this, info).setVisible(true);
            }
        };
        worker.execute();
    }

    private boolean showConnectionDialog(String defaultProfile, String defaultRegion) {
        log.debug("Showing connection dialog...");

        List<String> profiles = AwsProfiles.readAwsProfiles();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> profileCombo = new JComboBox<>(profiles.toArray(new String[0]));
        if (defaultProfile != null && profiles.contains(defaultProfile)) {
            profileCombo.setSelectedItem(defaultProfile);
        } else if (profiles.contains("default")) {
            profileCombo.setSelectedItem("default");
        }
        profileCombo.setEditable(true); // Allow custom profile names

        JLabel profileStatusLabel = new JLabel(" ");

        JTextField regionField = new JTextField(20);
        String initialProfile = comboText(profileCombo);
        regionField.setText(defaultRegion != null && !defaultRegion.isEmpty()
            ? defaultRegion
            : AwsProfiles.resolveRegionForProfile(initialProfile));

        JButton testConnectionButton = new JButton("Test Connection");

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("AWS Profile:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(profileCombo, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(profileStatusLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Region:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(regionField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(testConnectionButton, gbc);

        // Holds the account ID from the most recent successful profile check, so it can be
        // shown in the window title without an extra STS call at connect time.
        String[] lastKnownAccountId = new String[1];

        Runnable testConnection = () -> {
            String profile = comboText(profileCombo);
            if (profile.isEmpty()) {
                return;
            }

            profileStatusLabel.setText("Checking...");
            profileStatusLabel.setForeground(Color.GRAY);
            profileStatusLabel.setToolTipText(null);
            testConnectionButton.setEnabled(false);
            lastKnownAccountId[0] = null;

            new SwingWorker<ProfileActivity, Void>() {
                @Override
                protected ProfileActivity doInBackground() {
                    return AwsProfileActivityChecker.check(profile);
                }

                @Override
                protected void done() {
                    ProfileActivity activity;
                    try {
                        activity = get();
                    } catch (Exception e) {
                        activity = new ProfileActivity(false, null, e.getMessage(), null);
                    }

                    testConnectionButton.setEnabled(true);

                    if (activity.active) {
                        lastKnownAccountId[0] = activity.accountId;
                        profileStatusLabel.setForeground(new Color(0, 130, 0));
                        profileStatusLabel.setText("● Active");
                        profileStatusLabel.setToolTipText("Account: " + activity.accountId + " (" + activity.region + ")");
                        if (activity.region != null && regionField.getText().trim().isEmpty()) {
                            regionField.setText(activity.region);
                        }
                    } else {
                        profileStatusLabel.setForeground(Color.RED);
                        profileStatusLabel.setText("● Inactive");
                        profileStatusLabel.setToolTipText(activity.errorMessage != null
                            ? activity.errorMessage
                            : "Profile could not be verified");
                    }
                }
            }.execute();
        };

        profileCombo.addActionListener(e -> {
            String profile = comboText(profileCombo);
            if (!profile.isEmpty()) {
                regionField.setText(AwsProfiles.resolveRegionForProfile(profile));
            }
            testConnection.run();
        });
        testConnectionButton.addActionListener(e -> testConnection.run());

        this.setVisible(true);
        this.toFront();
        this.requestFocus();

        if (defaultProfile != null && !defaultProfile.trim().isEmpty()) {
            testConnection.run();
        }

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Connect to AWS", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        log.debug("Dialog result: {}", result);

        if (result == JOptionPane.OK_OPTION) {
            String profile = comboText(profileCombo);
            String region = regionField.getText().trim();

            if (profile.isEmpty() || region.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please provide both an AWS profile and a region.");
                return showConnectionDialog(profile, region);
            }

            connectedProfile = profile;
            connectedAccountId = lastKnownAccountId[0];
            connectedRegion = region;

            prefs.setAwsProfile(profile);
            prefs.setAwsRegion(region);

            ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profile);
            s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();

            setTitle(ConnectionMessages.windowTitle(connectedProfile, connectedAccountId, connectedRegion));

            if (!uiInitialized) {
                initializeUI();
                uiInitialized = true;
            }
            bucketListPanel.setClient(s3);
            objectBrowserPanel.setClient(s3);
            objectBrowserPanel.setConsoleContext(credentialsProvider, region);
            cardLayout.show(cardPanel, CARD_BUCKETS);
            bucketListPanel.loadInitial();
            return true;
        }
        return false;
    }

    private String comboText(JComboBox<String> combo) {
        Object editorItem = combo.getEditor().getItem();
        return editorItem != null ? editorItem.toString().trim() : "";
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitItem.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        JMenu viewMenu = new JMenu("View");
        JMenu themeMenu = new JMenu("Theme");
        for (String themeName : ThemeManager.getAvailableThemeNames()) {
            JMenuItem item = new JMenuItem(themeName);
            item.addActionListener(e -> ThemeManager.applyTheme(themeName));
            themeMenu.add(item);
        }
        viewMenu.add(themeMenu);
        menuBar.add(viewMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(aboutItem);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton settingsButton = new JButton("Change Connection");
        settingsButton.addActionListener(e -> showConnectionDialog(connectedProfile, connectedRegion));
        topPanel.add(new JLabel("Profile: " + connectedProfile));
        topPanel.add(settingsButton);
        add(topPanel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        bucketListPanel = new BucketListPanel(this, this::openBucket);
        objectBrowserPanel = new ObjectBrowserPanel(this, this::backToBuckets);
        cardPanel.add(bucketListPanel, CARD_BUCKETS);
        cardPanel.add(objectBrowserPanel, CARD_OBJECTS);
        add(cardPanel, BorderLayout.CENTER);
    }

    private void openBucket(String bucketName) {
        objectBrowserPanel.openBucket(bucketName);
        cardLayout.show(cardPanel, CARD_OBJECTS);
    }

    private void backToBuckets() {
        cardLayout.show(cardPanel, CARD_BUCKETS);
    }
}
