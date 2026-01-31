import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;

// Speed Test Class - Simplified implementation without external dependencies
// Improved Speed Test Class - More reliable implementation
class NetworkSpeedTester {
    private static final Logger logger = Logger.getLogger(NetworkSpeedTester.class.getName());

    public double measureDownloadSpeed() {
        logger.info("Starting download speed test...");

        String[] testUrls = {
            "http://ipv4.download.thinkbroadband.com/5MB.zip",
            "http://speedtest.tele2.net/10MB.zip",
            "http://speedtest-sgp1.digitalocean.com/5mb.test"
        };

        int timeoutSeconds = 5;
        long totalBytesRead = 0;
        long startTime = System.currentTimeMillis();

        for (String urlStr : testUrls) {
            try {
                logger.info("Testing with: " + urlStr);
                URL url = new URL(urlStr);
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;

                    while (System.currentTimeMillis() < deadline) {
                        int bytesRead = in.read(buffer);
                        if (bytesRead == -1) break;
                        totalBytesRead += bytesRead;
                    }
                }

                break; 

            } catch (IOException e) {
                logger.warning("Failed to download from: " + urlStr + " - " + e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        double durationSeconds = durationMs / 1000.0;
        double megabits = totalBytesRead * 8.0 / (1024 * 1024);
        double speedMbps = megabits / durationSeconds;

        logger.info(String.format("Downloaded %.2f MB in %.2f sec (%.2f Mbps)", 
            totalBytesRead / (1024.0 * 1024.0), durationSeconds, speedMbps));

        return speedMbps;
    }
}


// Video Player Class
class VideoPlayer {
    private static final Logger logger = Logger.getLogger(VideoPlayer.class.getName());
    private Process ffplayProcess;
    
    public void playStream(String protocol, int port) {
        try {
            List<String> command = new ArrayList<>();
            command.add("ffplay");
            command.add("-autoexit");
            command.add("-window_title");
            command.add("Streaming Video Player");
            
            String streamUrl;
            switch (protocol.toUpperCase()) {
                case "UDP":
                    streamUrl = "udp://127.0.0.1:" + port;
                    break;
                case "TCP":
                    streamUrl = "tcp://127.0.0.1:" + port;
                    break;
                case "RTP/UDP":
					command.add("-protocol_whitelist");
					command.add("file,rtp,udp");
					streamUrl = "videos/stream.sdp"; 
					break;
                default:
                    streamUrl = "udp://127.0.0.1:" + port;
            }
            
			command.add("-i");
            command.add(streamUrl);
            
            logger.info("Starting video player with command: " + String.join(" ", command));
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            ffplayProcess = pb.start();
            
            // Monitor process output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffplayProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.fine("FFPLAY: " + line);
                    }
                } catch (IOException e) {
                    logger.warning("Error reading FFPLAY output: " + e.getMessage());
                }
            }).start();
            
        } catch (IOException e) {
            logger.severe("Error starting video player: " + e.getMessage());
        }
    }
    
    public void stopPlayer() {
        if (ffplayProcess != null && ffplayProcess.isAlive()) {
            ffplayProcess.destroy();
            logger.info("Video player stopped");
        }
    }
}

// Client GUI Class
class ClientGUI extends JFrame {
	private static final Logger logger = Logger.getLogger(ClientGUI.class.getName());
	
    private JTextField serverAddressField;
    private JTextField serverPortField;
    private JLabel speedLabel;
    private JComboBox<String> formatComboBox;
    private JList<String> videoList;
    private DefaultListModel<String> videoListModel;
    private JComboBox<String> protocolComboBox;
    private JButton connectButton, speedTestButton, requestListButton, playButton, stopButton;
    private JTextArea logArea;
    private JProgressBar progressBar;
    
    private StreamingClient client;
    private double measuredSpeed = 0.0;
    private List<String> availableVideos = new ArrayList<>();
    private boolean isConnected = false;
    private String lastFormat = "";
    
    public ClientGUI() {
        client = new StreamingClient();
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("Streaming Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Server connection panel
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setBorder(BorderFactory.createTitledBorder("Server Connection"));
        
        GridBagConstraints connGbc = new GridBagConstraints();
        connGbc.insets = new Insets(5, 5, 5, 5);
        
        connGbc.gridx = 0; connGbc.gridy = 0;
        connectionPanel.add(new JLabel("Server Address:"), connGbc);
        connGbc.gridx = 1;
        serverAddressField = new JTextField("localhost", 15);
        connectionPanel.add(serverAddressField, connGbc);
        
        connGbc.gridx = 0; connGbc.gridy = 1;
        connectionPanel.add(new JLabel("Server Port:"), connGbc);
        connGbc.gridx = 1;
        serverPortField = new JTextField("7173", 15);
        connectionPanel.add(serverPortField, connGbc);
        
        connGbc.gridx = 0; connGbc.gridy = 2;
        connectButton = new JButton("Connect to Server");
        connectButton.addActionListener(e -> connectToServer());
        connectionPanel.add(connectButton, connGbc);
        
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(connectionPanel, gbc);
        
        // Speed test panel
        JPanel speedPanel = new JPanel(new GridBagLayout());
        speedPanel.setBorder(BorderFactory.createTitledBorder("Connection Speed"));
        
        GridBagConstraints speedGbc = new GridBagConstraints();
        speedGbc.insets = new Insets(5, 5, 5, 5);
        
        speedGbc.gridx = 0; speedGbc.gridy = 0;
        speedTestButton = new JButton("Test Speed");
        speedTestButton.addActionListener(e -> performSpeedTest());
        speedPanel.add(speedTestButton, speedGbc);
        
        speedGbc.gridx = 1;
        speedLabel = new JLabel("Speed: Not tested");
        speedLabel.setFont(new Font("Arial", Font.BOLD, 12));
        speedPanel.add(speedLabel, speedGbc);
        
        gbc.gridy = 1;
        mainPanel.add(speedPanel, gbc);
        
        // Format selection panel
        JPanel formatPanel = new JPanel(new GridBagLayout());
        formatPanel.setBorder(BorderFactory.createTitledBorder("Video Format"));
        
        GridBagConstraints formatGbc = new GridBagConstraints();
        formatGbc.insets = new Insets(5, 5, 5, 5);
        
        formatGbc.gridx = 0; formatGbc.gridy = 0;
        formatPanel.add(new JLabel("Select Format:"), formatGbc);
        formatGbc.gridx = 1;
        formatComboBox = new JComboBox<>(new String[]{"mkv", "mp4", "avi"});
        formatPanel.add(formatComboBox, formatGbc);
        
        formatGbc.gridx = 2;
        requestListButton = new JButton("Get Video List");
        requestListButton.addActionListener(e -> requestVideoList());
        requestListButton.setEnabled(false);
        formatPanel.add(requestListButton, formatGbc);
        
        gbc.gridy = 2;
        mainPanel.add(formatPanel, gbc);
        
        // Video selection panel
        JPanel videoPanel = new JPanel(new BorderLayout());
        videoPanel.setBorder(BorderFactory.createTitledBorder("Available Videos"));
        
        videoListModel = new DefaultListModel<>();
        videoList = new JList<>(videoListModel);
        videoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane videoScrollPane = new JScrollPane(videoList);
        videoScrollPane.setPreferredSize(new Dimension(400, 150));
        videoPanel.add(videoScrollPane, BorderLayout.CENTER);
        
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;
        mainPanel.add(videoPanel, gbc);
        
        // Protocol selection panel
        JPanel protocolPanel = new JPanel(new GridBagLayout());
        protocolPanel.setBorder(BorderFactory.createTitledBorder("Streaming Protocol"));
        
        GridBagConstraints protocolGbc = new GridBagConstraints();
        protocolGbc.insets = new Insets(5, 5, 5, 5);
        
        protocolGbc.gridx = 0; protocolGbc.gridy = 0;
        protocolPanel.add(new JLabel("Select Protocol:"), protocolGbc);
        protocolGbc.gridx = 1;
        protocolComboBox = new JComboBox<>(new String[]{"Auto", "UDP", "TCP", "RTP/UDP"});
        protocolPanel.add(protocolComboBox, protocolGbc);
        
        gbc.gridy = 4;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(protocolPanel, gbc);
        
        // Control buttons panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        playButton = new JButton("Play Video");
        playButton.addActionListener(e -> playSelectedVideo());
        playButton.setEnabled(false);
        
        stopButton = new JButton("Stop Video");
        stopButton.addActionListener(e -> stopVideo());
        stopButton.setEnabled(false);
        
        controlPanel.add(playButton);
        controlPanel.add(stopButton);
        
        gbc.gridy = 5;
        mainPanel.add(controlPanel, gbc);
        
        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        gbc.gridy = 6;
        mainPanel.add(progressBar, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Log area
        logArea = new JTextArea(10, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Courier", Font.PLAIN, 11));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Client Logs"));
        add(logScrollPane, BorderLayout.SOUTH);
        
        pack();
        setLocationRelativeTo(null);
        
        // Setup custom log handler
        setupLogHandler();
    }
    
    private void setupLogHandler() {
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(new Date() + " - " + record.getLevel() + ": " + record.getMessage() + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
            
            @Override
            public void flush() {}
            
            @Override
            public void close() throws SecurityException {}
        };
        
        Logger.getLogger("").addHandler(handler);
        Logger.getLogger("").setLevel(Level.INFO);
    }
    
    private void connectToServer() {
        String address = serverAddressField.getText().trim();
        int port;
        
        try {
            port = Integer.parseInt(serverPortField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port number!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        progressBar.setString("Connecting to server...");
        progressBar.setIndeterminate(true);
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return client.connectToServer(address, port);
            }
            
            @Override
            protected void done() {
                try {
                    boolean connected = get();
                    progressBar.setIndeterminate(false);
                    
                    if (connected) {
                        isConnected = true;
                        progressBar.setString("Connected to server");
                        connectButton.setEnabled(false);
                        requestListButton.setEnabled(true);
                        JOptionPane.showMessageDialog(ClientGUI.this, "Connected to server successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        isConnected = false;
                        progressBar.setString("Connection failed");
                        JOptionPane.showMessageDialog(ClientGUI.this, "Failed to connect to server!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    isConnected = false;
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Connection failed");
                    JOptionPane.showMessageDialog(ClientGUI.this, "Connection error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private void performSpeedTest() {
		progressBar.setString("Testing connection speed...");
		progressBar.setIndeterminate(true);
		speedTestButton.setEnabled(false);
		
		SwingWorker<Double, Void> worker = new SwingWorker<Double, Void>() {
			@Override
			protected Double doInBackground() throws Exception {
				NetworkSpeedTester tester = new NetworkSpeedTester();
				// Use the more reliable method or mock for testing
				return tester.measureDownloadSpeed(); // or measureDownloadSpeedMock() for testing
			}
			
			@Override
			protected void done() {
				try {
					measuredSpeed = get(30, TimeUnit.SECONDS); // Add timeout
					progressBar.setIndeterminate(false);
					progressBar.setString("Speed test completed");
					speedLabel.setText(String.format("Speed: %.2f Mbps", measuredSpeed));
					speedLabel.setForeground(Color.BLUE);
				} catch (TimeoutException e) {
					logger.warning("Speed test timed out");
					measuredSpeed = 10.0; // Fallback speed
					progressBar.setString("Speed test timed out - using default");
					speedLabel.setText("Speed: 10.0 Mbps (default)");
					speedLabel.setForeground(Color.ORANGE);
				} catch (Exception e) {
					logger.warning("Speed test failed: " + e.getMessage());
					measuredSpeed = 10.0; // Fallback speed
					progressBar.setString("Speed test failed - using default");
					speedLabel.setText("Speed: 10.0 Mbps (default)");
					speedLabel.setForeground(Color.RED);
				} finally {
					progressBar.setIndeterminate(false);
					speedTestButton.setEnabled(true);
				}
			}
		};
		
		worker.execute();
	}
    
    private void requestVideoList() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Please connect to server first!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (measuredSpeed == 0.0) {
            JOptionPane.showMessageDialog(this, "Please test your connection speed first!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String selectedFormat = (String) formatComboBox.getSelectedItem();
        
        progressBar.setString("Requesting video list...");
        progressBar.setIndeterminate(true);
        
        SwingWorker<java.util.List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return client.requestVideoList(measuredSpeed, selectedFormat);
            }
            
            @Override
            protected void done() {
                try {
                    availableVideos = get();
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Video list received");
                    
                    videoListModel.clear();
                    for (String video : availableVideos) {
                        videoListModel.addElement(video);
                    }
                    
                    playButton.setEnabled(!availableVideos.isEmpty());
                    lastFormat = (String) formatComboBox.getSelectedItem();
                    
                } catch (Exception e) {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Failed to get video list");
                    JOptionPane.showMessageDialog(ClientGUI.this, "Failed to get video list: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private void playSelectedVideo() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Please connect to server first!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String selectedVideo = videoList.getSelectedValue();
        if (selectedVideo == null) {
            JOptionPane.showMessageDialog(this, "Please select a video first!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
    
        String selectedProtocol = (String) protocolComboBox.getSelectedItem();
    
        // Auto-select protocol based on resolution if "Auto" is selected
        if ("Auto".equals(selectedProtocol)) {
            selectedProtocol = getAutoProtocol(selectedVideo);
        }
    
        // Create final copies for use in the inner class
        final String finalSelectedVideo = selectedVideo;
        final String finalSelectedProtocol = selectedProtocol;
        
        progressBar.setString("Starting video stream...");
        progressBar.setIndeterminate(true);
        
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                client.requestVideoStream(finalSelectedVideo, finalSelectedProtocol);
                return null;
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Video streaming started");
                    playButton.setEnabled(false);
                    stopButton.setEnabled(true);
                } catch (Exception e) {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Failed to start stream");
                    JOptionPane.showMessageDialog(ClientGUI.this, "Failed to start video stream: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
    
        worker.execute();
    }
    
    private String getAutoProtocol(String videoName) {
        if (videoName.contains("-240p")) {
            return "TCP";
        } else if (videoName.contains("-360p") || videoName.contains("-480p")) {
            return "UDP";
        } else if (videoName.contains("-720p") || videoName.contains("-1080p")) {
            return "RTP/UDP";
        }
        return "UDP"; // Default
    }
    
    private void stopVideo() {
        client.stopVideo();
        progressBar.setString("Video stopped");
        playButton.setEnabled(true);
        stopButton.setEnabled(false);
    }
}

// Main Streaming Client Class
public class StreamingClient {
    private static final Logger logger = Logger.getLogger(StreamingClient.class.getName());
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private VideoPlayer videoPlayer;
    private boolean isStreaming = false;
    private boolean connectionEstablished = false;
    
    public StreamingClient() {
        videoPlayer = new VideoPlayer();
        setupLogging();
    }
    
    private void setupLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);
        
        // Remove default console handler to avoid duplicate logs
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            if (handler instanceof ConsoleHandler) {
                rootLogger.removeHandler(handler);
            }
        }
    }
    
    public boolean connectToServer(String address, int port) {
        try {
            // Close existing connection if any
            disconnect();
            
            socket = new Socket(address, port);
            socket.setKeepAlive(true);
            socket.setSoTimeout(30000); // 30 second timeout
            
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            connectionEstablished = true;
            logger.info("Connected to server at " + address + ":" + port);
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to connect to server: " + e.getMessage());
            connectionEstablished = false;
            return false;
        }
    }
    
    public List<String> requestVideoList(double connectionSpeed, String format) throws IOException {
        if (!isConnectionValid()) {
            throw new IOException("Not connected to server or connection lost");
        }

        // Stop any current streaming first
        if (isStreaming) {
            stopVideo();
            // Add small delay to ensure clean stop
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Requesting video list with speed: " + connectionSpeed + " Mbps, format: " + format);

        try {
            // Send connection speed and format to server
            out.println(connectionSpeed);
            out.flush();
            
            // Check if the output stream is still working
            if (out.checkError()) {
                throw new IOException("Connection to server lost (output stream error)");
            }
            
            out.println(format);
            out.flush();
            
            if (out.checkError()) {
                throw new IOException("Connection to server lost (output stream error)");
            }

            // Read the number of available videos with timeout handling
            String countLine = readLineWithTimeout();
            if (countLine == null) {
                throw new IOException("Server closed connection or timeout occurred");
            }

            int videoCount;
            try {
                videoCount = Integer.parseInt(countLine.trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid response from server: " + countLine);
            }

            logger.info("Server has " + videoCount + " compatible videos");

            // Read the video list
            List<String> videos = new ArrayList<>();
            for (int i = 0; i < videoCount; i++) {
                String video = readLineWithTimeout();
                if (video != null) {
                    videos.add(video.trim());
                } else {
                    throw new IOException("Server closed connection while reading video list");
                }
            }

            return videos;
            
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timeout while communicating with server");
        } catch (IOException e) {
            // Connection might be lost, mark as invalid
            connectionEstablished = false;
            throw e;
        }
    }
    
    private String readLineWithTimeout() throws IOException {
        try {
            return in.readLine();
        } catch (SocketTimeoutException e) {
            throw new IOException("Timeout reading from server");
        }
    }
    
    private boolean isConnectionValid() {
        if (socket == null || socket.isClosed() || !connectionEstablished) {
            return false;
        }
        
        try {
            // Test the connection by checking if streams are still valid
            return !socket.isInputShutdown() && !socket.isOutputShutdown() && socket.isConnected();
        } catch (Exception e) {
            connectionEstablished = false;
            return false;
        }
    }
		
    public void requestVideoStream(String videoName, String protocol) throws IOException {
        if (!isConnectionValid()) {
            throw new IOException("Not connected to server or connection lost");
        }
        
        // Stop any existing video first
        if (isStreaming) {
            stopVideo();
            // Add delay to ensure clean stop
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Requesting video stream: " + videoName + " with protocol: " + protocol);
        
        try {
            // Send video selection and protocol to server
            out.println(videoName);
            out.flush();
            
            if (out.checkError()) {
                throw new IOException("Connection to server lost (output stream error)");
            }
            
            out.println(protocol);
            out.flush();
            
            if (out.checkError()) {
                throw new IOException("Connection to server lost (output stream error)");
            }
            
            // Wait for server response with timeout
            String response = readLineWithTimeout();
            if (response == null) {
                throw new IOException("Server closed connection or timeout occurred");
            }
            
            response = response.trim();
            if (!"STREAMING_START".equals(response)) {
                throw new IOException("Server failed to start streaming. Response: " + response);
            }
            
            // Get streaming port
            String portLine = readLineWithTimeout();
            if (portLine == null) {
                throw new IOException("Server closed connection while reading port");
            }
            
            int streamingPort;
            try {
                streamingPort = Integer.parseInt(portLine.trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid port from server: " + portLine);
            }
            
            logger.info("Server started streaming on port: " + streamingPort);
            isStreaming = true;
            
            // Start video player with a small delay to ensure server is ready
            javax.swing.Timer timer = new javax.swing.Timer(7000, e -> {
                videoPlayer.playStream(protocol, streamingPort);
                ((javax.swing.Timer) e.getSource()).stop();
            });
            timer.start();
            
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timeout while starting video stream");
        } catch (IOException e) {
            // Connection might be lost, mark as invalid
            connectionEstablished = false;
            isStreaming = false;
            throw e;
        }
    }
    
    public void stopVideo() {
        if (videoPlayer != null) {
            videoPlayer.stopPlayer();
        }
        isStreaming = false;
        logger.info("Video playback stopped");
    }
    
    public void disconnect() {
        try {
            // Stop video if streaming
            if (isStreaming) {
                stopVideo();
            }
            
            connectionEstablished = false;
            
            // Close streams
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.warning("Error closing input stream: " + e.getMessage());
                }
            }
            if (out != null) {
                out.close();
            }
            
            // Close socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            logger.info("Disconnected from server");
        } catch (IOException e) {
            logger.warning("Error disconnecting from server: " + e.getMessage());
        } finally {
            socket = null;
            in = null;
            out = null;
            isStreaming = false;
            connectionEstablished = false;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                // Use default look and feel
            }
            
            new ClientGUI().setVisible(true);
        });
    }
}