import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.swing.*;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.*;
import java.awt.event.*;
import net.bramp.ffmpeg.*;
import net.bramp.ffmpeg.builder.*;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;

// Video Information Class
class VideoInfo {
    private String name;
    private String format;
    private String resolution;
    private String filePath;
    
    public VideoInfo(String name, String format, String resolution, String filePath) {
        this.name = name;
        this.format = format;
        this.resolution = resolution;
        this.filePath = filePath;
    }
    
    // Getters
    public String getName() { return name; }
    public String getFormat() { return format; }
    public String getResolution() { return resolution; }
    public String getFilePath() { return filePath; }
    
    @Override
    public String toString() {
        return name + "-" + resolution + "." + format;
    }
}

// Video Processor Class for FFMPEG operations
class VideoProcessor {
    private static final Logger logger = Logger.getLogger(VideoProcessor.class.getName());
    private static final String[] SUPPORTED_FORMATS = {"avi", "mp4", "mkv"};
    private static final String[] SUPPORTED_RESOLUTIONS = {"240p", "360p", "480p", "720p", "1080p"};
    
    public static void processVideosInDirectory(String videosPath) {
        logger.info("Starting video processing in directory: " + videosPath);
        
        File videosDir = new File(videosPath);
        if (!videosDir.exists()) {
            videosDir.mkdirs();
            logger.info("Created videos directory: " + videosPath);
        }
        
        // Get all video files grouped by movie name
        Map<String, List<File>> movieFiles = groupFilesByMovieName(videosDir);
        
        for (String movieName : movieFiles.keySet()) {
            List<File> files = movieFiles.get(movieName);
            processMovieFiles(movieName, files, videosPath);
        }
    }
    
    private static Map<String, List<File>> groupFilesByMovieName(File dir) {
        Map<String, List<File>> movieFiles = new HashMap<>();
        
        File[] files = dir.listFiles((f, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv");
        });
        
        if (files != null) {
            for (File file : files) {
                String movieName = extractMovieName(file.getName());
                movieFiles.computeIfAbsent(movieName, k -> new ArrayList<>()).add(file);
            }
        }
        
        return movieFiles;
    }
    
    private static String extractMovieName(String filename) {
        // Extract movie name from filename like "Forrest_Gump-720p.mkv"
        int dashIndex = filename.lastIndexOf('-');
        if (dashIndex > 0) {
            return filename.substring(0, dashIndex);
        }
        // If no dash found, remove extension
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
    
    private static void processMovieFiles(String movieName, java.util.List<File> existingFiles, String videosPath) {
        logger.info("Processing movie: " + movieName);
        
        // Find the highest resolution available
        String maxResolution = findMaxResolution(existingFiles);
        if (maxResolution == null) {
            logger.warning("No valid resolution found for movie: " + movieName);
            return;
        }
        
        // Find a source file with the maximum resolution
        File sourceFile = findSourceFile(existingFiles, maxResolution);
        if (sourceFile == null) {
            logger.warning("No source file found for movie: " + movieName);
            return;
        }
        
        logger.info("Using source file: " + sourceFile.getName() + " with max resolution: " + maxResolution);
        
        // Generate all missing combinations
        generateMissingFiles(movieName, sourceFile, maxResolution, existingFiles, videosPath);
    }
    
    private static String findMaxResolution(java.util.List<File> files) {
        String[] resolutions = {"1080p", "720p", "480p", "360p", "240p"};
        
        for (String resolution : resolutions) {
            for (File file : files) {
                if (file.getName().contains("-" + resolution + ".")) {
                    return resolution;
                }
            }
        }
        return null;
    }
    
    private static File findSourceFile(java.util.List<File> files, String maxResolution) {
        for (File file : files) {
            if (file.getName().contains("-" + maxResolution + ".")) {
                return file;
            }
        }
        return null;
    }
    
    private static void generateMissingFiles(String movieName, File sourceFile, String maxResolution, List<File> existingFiles, String videosPath) {
        
        Set<String> existingCombinations = new HashSet<>();
        for (File file : existingFiles) {
            existingCombinations.add(file.getName());
        }
        
        // Get available resolutions (up to max resolution)
        List<String> availableResolutions = getAvailableResolutions(maxResolution);
        
        for (String format : SUPPORTED_FORMATS) {
            for (String resolution : availableResolutions) {
                String targetFileName = movieName + "-" + resolution + "." + format;
                
                if (!existingCombinations.contains(targetFileName)) {
                    String targetPath = videosPath + File.separator + targetFileName;
                    convertVideo(sourceFile.getAbsolutePath(), targetPath, resolution, format);
                }
            }
        }
    }
    
    private static List<String> getAvailableResolutions(String maxResolution) {
        List<String> available = new ArrayList<>();
        String[] allResolutions = {"240p", "360p", "480p", "720p", "1080p"};
        
        for (String resolution : allResolutions) {
            available.add(resolution);
            if (resolution.equals(maxResolution)) {
                break;
            }
        }
        
        return available;
    }
    
    private static void convertVideo(String sourcePath, String targetPath, String resolution, String format) {
		try {
			logger.info("Converting (via wrapper): " + sourcePath + " -> " + targetPath);

			String dimensions = getResolutionDimensions(resolution);

			FFmpeg ffmpeg = new FFmpeg("/usr/bin/ffmpeg");
			FFprobe ffprobe = new FFprobe("/usr/bin/ffprobe");

			FFmpegBuilder builder = new FFmpegBuilder()
					.setInput(sourcePath)
					.overrideOutputFiles(true)
					.addOutput(targetPath)
					.setFormat(format)
					.setVideoCodec("libx264")
					.setAudioCodec("aac")
					.addExtraArgs("-vf", "scale=" + dimensions)
					.done();

			FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
			executor.createJob(builder).run();

			logger.info("Finished conversion: " + targetPath);

		} catch (IOException e) {
			logger.severe("Wrapper error: " + e.getMessage());
		}
	}
    
    private static String getResolutionDimensions(String resolution) {
        switch (resolution) {
            case "240p": return "426:240";
            case "360p": return "640:360";
            case "480p": return "854:480";
            case "720p": return "1280:720";
            case "1080p": return "1920:1080";
            default: return "854:480"; // Default to 480p
        }
    }
}

// Server GUI Class
class ServerGUI extends JFrame {
    private JTextArea logArea;
    private JButton startButton, stopButton;
    private JLabel statusLabel;
    private StreamingServer server;
    private boolean isRunning = false;
    
    public ServerGUI() {
        initializeGUI();
    }
    
    private void initializeGUI() {
        setTitle("Streaming Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout());
        statusLabel = new JLabel("Server Status: Stopped");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);
        
        // Log area
        logArea = new JTextArea(20, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Courier", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Server Logs"));
        add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);
        
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
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
    
    private void startServer() {
        try {
            server = new StreamingServer(7173);
            new Thread(() -> server.start()).start();
            
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            statusLabel.setText("Server Status: Running on port 7173");
            statusLabel.setForeground(Color.GREEN);
            isRunning = true;
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to start server: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void stopServer() {
        if (server != null) {
            server.stop();
        }
        
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Server Status: Stopped");
        statusLabel.setForeground(Color.RED);
        isRunning = false;
    }
}

// Main Streaming Server Class
public class StreamingServer {
    private static final Logger logger = Logger.getLogger(StreamingServer.class.getName());
    private static final String VIDEOS_DIRECTORY = "videos";
    
    private ServerSocket serverSocket;
    private java.util.List<VideoInfo> availableVideos;
    private boolean isRunning = false;
    private ExecutorService executorService;
    
    // Store active streaming processes
    private Map<String, Process> activeStreams = new ConcurrentHashMap<>();
    
    // Bitrate limits for resolutions (in Kbps)
    private static final Map<String, Integer> RESOLUTION_BITRATES = new HashMap<>();
    static {
        RESOLUTION_BITRATES.put("240p", 700);
        RESOLUTION_BITRATES.put("360p", 1000);
        RESOLUTION_BITRATES.put("480p", 2000);
        RESOLUTION_BITRATES.put("720p", 4000);
        RESOLUTION_BITRATES.put("1080p", 6000);
    }
    
    public StreamingServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        availableVideos = new ArrayList<>();
        executorService = Executors.newCachedThreadPool();
        
        // Setup logging
        setupLogging();
        
        // Process videos and build available list
        initializeVideoLibrary();
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
	
	private volatile boolean clientConnected = false;
    
    public void start() {
		isRunning = true;
		logger.info("Streaming Server started on port " + serverSocket.getLocalPort());
		
		while (isRunning) {
			try {
				Socket clientSocket = serverSocket.accept();
				if (clientConnected) {
					logger.warning("Another client tried to connect but one is already connected.");
					clientSocket.close();
					continue;
				}
				clientConnected = true;
				logger.info("New client connected: " + clientSocket.getInetAddress());

				new Thread(new ClientHandler(clientSocket)).start();
				

			} catch (IOException e) {
				if (isRunning) {
					logger.severe("Error accepting client connection: " + e.getMessage());
				}
			}
		}
	}
    
    public void stop() {
        isRunning = false;
        
        // Stop all active streams
        for (Process process : activeStreams.values()) {
            if (process.isAlive()) {
                process.destroy();
            }
        }
        activeStreams.clear();
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            logger.info("Streaming Server stopped");
        } catch (IOException e) {
            logger.severe("Error stopping server: " + e.getMessage());
        }
    }
    
    private void initializeVideoLibrary() {
        logger.info("Initializing video library...");
        
        // Process videos with FFMPEG
        VideoProcessor.processVideosInDirectory(VIDEOS_DIRECTORY);
        
        // Build available videos list
        buildAvailableVideosList();
        
        logger.info("Video library initialized with " + availableVideos.size() + " videos");
    }
    
    private void buildAvailableVideosList() {
        availableVideos.clear();
        
        File videosDir = new File(VIDEOS_DIRECTORY);
        if (!videosDir.exists()) {
            logger.warning("Videos directory does not exist: " + VIDEOS_DIRECTORY);
            return;
        }
        
        File[] videoFiles = videosDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv");
        });
        
        if (videoFiles != null) {
            for (File file : videoFiles) {
                String filename = file.getName();
                String movieName = extractMovieName(filename);
                String format = extractFormat(filename);
                String resolution = extractResolution(filename);
                
                if (movieName != null && format != null && resolution != null) {
                    availableVideos.add(new VideoInfo(movieName, format, resolution, file.getAbsolutePath()));
                }
            }
        }
        
        logger.info("Found " + availableVideos.size() + " video files");
    }
    
    private String extractMovieName(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        return dashIndex > 0 ? filename.substring(0, dashIndex) : null;
    }
    
    private String extractFormat(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1) : null;
    }
    
    private String extractResolution(String filename) {
        int dashIndex = filename.lastIndexOf('-');
        int dotIndex = filename.lastIndexOf('.');
        if (dashIndex > 0 && dotIndex > dashIndex) {
            return filename.substring(dashIndex + 1, dotIndex);
        }
        return null;
    }
    
    // Client Handler Class - Now maintains persistent connection
    private class ClientHandler implements Runnable {
			private Socket clientSocket;
			private BufferedReader in;
			private PrintWriter out;
			private String clientId;
			
			public ClientHandler(Socket socket) {
				this.clientSocket = socket;
				this.clientId = socket.getInetAddress().toString() + ":" + socket.getPort();
			}
			
			@Override
			public void run() {
				try {
					in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					out = new PrintWriter(clientSocket.getOutputStream(), true);
					
					logger.info("Client handler started for: " + clientId);
					
					// Keep handling client requests until connection is closed
					handleClientPersistent();
					
				} catch (IOException e) {
					logger.warning("Error handling client " + clientId + ": " + e.getMessage());
				} finally {
					cleanup();
				}
			}
			
		   private void handleClientPersistent() throws IOException {
			String line;
			while ((line = in.readLine()) != null && !clientSocket.isClosed()) {
				logger.info("Received from client " + clientId + ": " + line);

				try {
					// Parse the connection speed (first line)
					double connectionSpeed = Double.parseDouble(line);

					// Parse the format preference (second line)
					String formatLine = in.readLine();
					if (formatLine == null) break;

					String preferredFormat = formatLine;

					logger.info("Client " + clientId + " - Speed: " + connectionSpeed + " Mbps, Format: " + preferredFormat);

					// Filter and send video list
					List<VideoInfo> filteredVideos = filterVideos(connectionSpeed, preferredFormat);
					sendVideoList(filteredVideos);

					// Wait for video selection
					String selectedVideo = in.readLine();
					String selectedProtocol = in.readLine();

					if (selectedVideo != null && selectedProtocol != null) {
						logger.info("Client " + clientId + " selected: " + selectedVideo + " with protocol: " + selectedProtocol);

						// Stop any existing stream for this client
						stopExistingStream();

						// Find and start streaming the selected video
						VideoInfo videoInfo = findVideoByName(selectedVideo);
						if (videoInfo != null) {
							startStreaming(videoInfo, selectedProtocol);
						} else {
							logger.warning("Selected video not found: " + selectedVideo);
							out.println("ERROR");
						}
					}
				} catch (NumberFormatException e) {
					logger.warning("Invalid speed format from client " + clientId + ": " + line);
					break;
				}
			}
	}
        
        private void sendVideoList(List<VideoInfo> videos) {
            out.println(videos.size());
            for (VideoInfo video : videos) {
                out.println(video.toString());
            }
        }
        
        private List<VideoInfo> filterVideos(double connectionSpeedMbps, String format) {
            List<VideoInfo> filtered = new ArrayList<>();
            int connectionSpeedKbps = (int) (connectionSpeedMbps * 1000);
            
            for (VideoInfo video : availableVideos) {
                if (video.getFormat().equals(format)) {
                    int requiredBitrate = RESOLUTION_BITRATES.getOrDefault(video.getResolution(), 2000);
                    if (connectionSpeedKbps >= requiredBitrate) {
                        filtered.add(video);
                    }
                }
            }
            
            return filtered;
        }
        
        private VideoInfo findVideoByName(String videoName) {
            for (VideoInfo video : availableVideos) {
                if (video.toString().equals(videoName)) {
                    return video;
                }
            }
            return null;
        }
        
        private void stopExistingStream() {
            Process existingProcess = activeStreams.get(clientId);
            if (existingProcess != null && existingProcess.isAlive()) {
                logger.info("Stopping existing stream for client: " + clientId);
                existingProcess.destroy();
                activeStreams.remove(clientId);
            }
        }
        
        private void startStreaming(VideoInfo video, String protocol) {
            logger.info("Starting streaming for client " + clientId + ": " + video.toString() + " using " + protocol);
            
            // Notify client that streaming is starting
            out.println("STREAMING_START");
            out.println(getStreamingPort(protocol));
            
            // Start FFMPEG streaming process
            startFFMPEGStreaming(video, protocol);
        }
        
        private int getStreamingPort(String protocol) {
            // Return different base ports for different protocols
            // Add client port offset to avoid conflicts
            int basePort;
            switch (protocol.toUpperCase()) {
                case "UDP": basePort = 5000; break;
                case "TCP": basePort = 5100; break;
                case "RTP/UDP": basePort = 5200; break;
                default: basePort = 5000;
            }
            
            // Use client port as offset to ensure unique ports per client
            return basePort + (clientSocket.getPort() % 100);
        }
        
        private void startFFMPEGStreaming(VideoInfo video, String protocol) {
            try {
                java.util.List<String> command = new ArrayList<>();
                command.add("ffmpeg");
                command.add("-re"); // Read input at native frame rate
                command.add("-i");
                command.add(video.getFilePath());
                
                int port = getStreamingPort(protocol);
                
                switch (protocol.toUpperCase()) {
					case "UDP":
						command.add("-f");
						command.add("mpegts");
						command.add("udp://127.0.0.1:" + port);
						break;
					case "TCP":
						command.add("-f");
						command.add("mpegts");
						command.add("tcp://127.0.0.1:" + port + "?listen");
						break;
					case "RTP/UDP":
						// Build SDP before the command
						createSDPFile(video.getFilePath(), port);

						// we are sending only the video stream!
						command.add("-map");
						command.add("0:v:0"); // only the first video stream

						command.add("-c:v");
						command.add("copy");

						command.add("-f");
						command.add("rtp");

						command.add("rtp://127.0.0.1:" + port);
						break;

				}

                
                logger.info("Starting FFMPEG for client " + clientId + " with command: " + String.join(" ", command));
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Store the process for this client
                activeStreams.put(clientId, process);
                
                // Monitor process output
                new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logger.fine("FFMPEG (" + clientId + "): " + line);
                        }
                    } catch (IOException e) {
                        logger.warning("Error reading FFMPEG output for client " + clientId + ": " + e.getMessage());
                    } finally {
                        // Remove from active streams when process ends
                        activeStreams.remove(clientId);
                        logger.info("FFMPEG process ended for client: " + clientId);
                    }
                }).start();
                
            } catch (IOException e) {
                logger.severe("Error starting FFMPEG streaming for client " + clientId + ": " + e.getMessage());
                out.println("ERROR");
            }
        }
        
        private void cleanup() {
			try {
				// Stop any active stream for this client
				stopExistingStream();
				
				// Close socket
				if (clientSocket != null && !clientSocket.isClosed()) {
					clientSocket.close();
				}
				
				// Free flag
				clientConnected = false;
				
				logger.info("Client " + clientId + " disconnected and cleaned up");
				
			} catch (IOException e) {
				logger.warning("Error cleaning up client " + clientId + ": " + e.getMessage());
			}
		}
    }
	
	private void createSDPFile(String fileName, int port) {
		try {
			String sdpContent = "v=0\n"
					+ "o=- 0 0 IN IP4 127.0.0.1\n"
					+ "s=Streaming\n"
					+ "c=IN IP4 127.0.0.1\n"
					+ "t=0 0\n"
					+ "m=video " + port + " RTP/AVP 96\n"
					+ "a=rtpmap:96 H264/90000\n";

			Path sdpPath = Paths.get("videos/stream.sdp");
			Files.write(sdpPath, sdpContent.getBytes());
			logger.info("SDP file created at: " + sdpPath.toAbsolutePath());
		} catch (IOException e) {
			logger.severe("Error creating SDP file: " + e.getMessage());
		}
	}

    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                // Use default look and feel
            }
            
            new ServerGUI().setVisible(true);
        });
    }
}