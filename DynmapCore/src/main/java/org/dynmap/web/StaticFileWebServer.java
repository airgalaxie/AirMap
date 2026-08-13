package org.dynmap.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.PlayerFaces;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.utils.BufferInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Internal static web server for Dynmap web files and storage-backed endpoints.
 */
public class StaticFileWebServer {
    private static final int DEFAULT_PORT = 8123;
    private static final int DEFAULT_MAX_SESSIONS = 30;
    private static final Map<String, String> MIME_TYPES = new HashMap<String, String>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("xml", "application/xml; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("map", "application/json; charset=utf-8");
        MIME_TYPES.put("php", "application/x-httpd-php; charset=utf-8");
    }

    private final DynmapCore core;
    private final ConfigurationNode configuration;
    private final File dataDirectory;
    private final Path webRoot;
    private final Path normalizedWebRoot;
    private final Path realWebRoot;
    private final boolean allowSymlinks;
    private HttpServer httpServer;
    private HttpServer httpsServer;
    private ExecutorService executor;

    /**
     * Create internal web server instance.
     * @param core - dynmap core
     * @param configuration - dynmap configuration
     * @param dataDirectory - dynmap data directory
     * @param webpath - web root path
     * @throws IOException if web root path cannot be resolved
     */
    public StaticFileWebServer(DynmapCore core, ConfigurationNode configuration, File dataDirectory, String webpath) throws IOException {
        this.core = core;
        this.configuration = configuration;
        this.dataDirectory = dataDirectory;
        this.webRoot = resolvePath(dataDirectory, webpath).toPath().toAbsolutePath().normalize();
        this.normalizedWebRoot = webRoot.normalize();
        this.realWebRoot = Files.exists(webRoot) ? webRoot.toRealPath() : webRoot;
        this.allowSymlinks = configuration.getBoolean("allow-symlinks", true);
    }

    /**
     * Start HTTP and optional HTTPS listeners.
     * @throws IOException if HTTP listener cannot be started
     */
    public void start() throws IOException {
        if (configuration.getBoolean("disable-webserver", true)) {
            Log.info("Internal webserver is disabled");
            return;
        }
        if (!Files.isDirectory(webRoot)) {
            throw new IOException("Web path is not a directory: " + webRoot);
        }

        int maxSessions = Math.max(1, configuration.getInteger("max-sessions", DEFAULT_MAX_SESSIONS));
        executor = Executors.newFixedThreadPool(maxSessions, r -> {
            Thread thread = new Thread(r, "DynmapWebServer");
            thread.setDaemon(true);
            return thread;
        });

        InetSocketAddress address = buildAddress("webserver-bindaddress", "webserver-port", DEFAULT_PORT);
        httpServer = HttpServer.create(address, maxSessions);
        configureServer(httpServer);
        httpServer.start();
        Log.info("Internal webserver started on http://" + printableHost(address) + ":" + address.getPort() + "/");

        if (configuration.getBoolean("webserver-ssl-enabled", false)) {
            try {
                InetSocketAddress sslAddress = buildAddress("webserver-ssl-bindaddress", "webserver-ssl-port", address.getPort() + 1);
                httpsServer = HttpsServer.create(sslAddress, maxSessions);
                ((HttpsServer) httpsServer).setHttpsConfigurator(new HttpsConfigurator(createSslContext()));
                configureServer(httpsServer);
                httpsServer.start();
                Log.info("Internal HTTPS webserver started on https://" + printableHost(sslAddress) + ":" + sslAddress.getPort() + "/");
            } catch (IOException | RuntimeException ex) {
                stopHttpsOnly();
                Log.severe("Failed to start internal HTTPS webserver", ex);
            }
        }
    }

    /**
     * Stop all active listeners and worker threads.
     */
    public void stop() {
        if (httpsServer != null) {
            httpsServer.stop(0);
            httpsServer = null;
        }
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Register request handlers and executor.
     * @param server - HTTP server
     */
    private void configureServer(HttpServer server) {
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(executor);
    }

    /**
     * Build SSL context from configured keystore.
     * @return initialized SSL context
     * @throws IOException if SSL context cannot be initialized
     */
    private SSLContext createSslContext() throws IOException {
        String keyStorePath = configuration.getString("webserver-ssl-keystore", null);
        String keyStorePassword = configuration.getString("webserver-ssl-keystore-password", "");
        String keyPassword = configuration.getString("webserver-ssl-key-password", keyStorePassword);
        String keyStoreType = configuration.getString("webserver-ssl-keystore-type", KeyStore.getDefaultType());

        if (keyStorePath == null || keyStorePath.isBlank()) {
            throw new IOException("webserver-ssl-keystore must be set when webserver-ssl-enabled is true");
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            try (InputStream in = Files.newInputStream(resolvePath(dataDirectory, keyStorePath).toPath(), StandardOpenOption.READ)) {
                keyStore.load(in, keyStorePassword.toCharArray());
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyPassword.toCharArray());

            SSLContext sslContext = SSLContext.getInstance(configuration.getString("webserver-ssl-context", "TLS"));
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (Exception ex) {
            throw new IOException("Could not initialize webserver SSL context", ex);
        }
    }

    /**
     * Build bind address from configuration.
     * @param bindKey - configuration key for bind address
     * @param portKey - configuration key for port
     * @param defaultPort - fallback port
     * @return socket address
     * @throws IOException if bind address cannot be resolved
     */
    private InetSocketAddress buildAddress(String bindKey, String portKey, int defaultPort) throws IOException {
        String bindAddress = configuration.getString(bindKey, configuration.getString("webserver-bindaddress", null));
        int port = configuration.getInteger(portKey, defaultPort);
        if (bindAddress == null || bindAddress.isBlank()) {
            return new InetSocketAddress(port);
        }
        return new InetSocketAddress(InetAddress.getByName(bindAddress), port);
    }

    /**
     * Get host string for startup log.
     * @param address - socket address
     * @return printable host
     */
    private String printableHost(InetSocketAddress address) {
        InetAddress inetAddress = address.getAddress();
        if (inetAddress == null || inetAddress.isAnyLocalAddress()) {
            return "localhost";
        }
        return inetAddress.getHostAddress();
    }

    /**
     * Stop HTTPS listener without affecting HTTP.
     */
    private void stopHttpsOnly() {
        if (httpsServer != null) {
            httpsServer.stop(0);
            httpsServer = null;
        }
    }

    /**
     * Resolve relative paths against parent directory.
     * @param parent - parent directory
     * @param path - absolute or relative path
     * @return resolved file
     */
    private static File resolvePath(File parent, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(parent, path);
    }

    private class StaticFileHandler implements HttpHandler {
        /**
         * Handle static file and storage endpoint requests.
         * @param exchange - HTTP exchange
         * @throws IOException if response cannot be sent
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    send(exchange, 405, "Method Not Allowed");
                    return;
                }

                if (handleStorageRequest(exchange)) {
                    return;
                }

                Path file = resolveRequestPath(exchange.getRequestURI());
                if (file == null || !Files.isRegularFile(file) || !Files.isReadable(file)) {
                    send(exchange, 404, "Not Found");
                    return;
                }

                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", contentType(file));
                headers.set("X-Content-Type-Options", "nosniff");
                long length = Files.size(file);
                exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : length);
                if (!"HEAD".equals(exchange.getRequestMethod())) {
                    try (OutputStream out = exchange.getResponseBody(); InputStream in = Files.newInputStream(file, StandardOpenOption.READ)) {
                        in.transferTo(out);
                    }
                } else {
                    exchange.close();
                }
            } catch (IllegalArgumentException ex) {
                send(exchange, 400, "Bad Request");
            } catch (IOException ex) {
                send(exchange, 500, "Internal Server Error");
            } finally {
                exchange.close();
            }
        }

        /**
         * Handle storage-backed API routes.
         * @param exchange - HTTP exchange
         * @return true if request was handled
         * @throws IOException if response cannot be sent
         */
        private boolean handleStorageRequest(HttpExchange exchange) throws IOException {
            if (!configuration.getBoolean("webserver-storage-endpoints", true)) {
                return false;
            }

            String requestPath = decodePath(exchange.getRequestURI().getRawPath());
            if (requestPath == null || requestPath.indexOf('\0') >= 0) {
                send(exchange, 400, "Bad Request");
                return true;
            }

            if (requestPath.equals("/up/configuration")) {
                sendStandaloneFile(exchange, "dynmap_config.json");
                return true;
            }
            if (requestPath.startsWith("/up/world/")) {
                String[] parts = requestPath.substring("/up/world/".length()).split("/");
                if (parts.length < 1 || !isSafePathPart(parts[0])) {
                    send(exchange, 400, "Bad Request");
                    return true;
                }
                sendStandaloneFile(exchange, "dynmap_" + parts[0] + ".json");
                return true;
            }
            if (requestPath.startsWith("/storage/tiles/")) {
                sendStorageTile(exchange, requestPath.substring("/storage/tiles/".length()));
                return true;
            }
            if (requestPath.startsWith("/storage/markers/")) {
                sendStorageMarker(exchange, requestPath.substring("/storage/markers/".length()));
                return true;
            }
            return false;
        }

        /**
         * Send standalone JSON file from storage.
         * @param exchange - HTTP exchange
         * @param fileid - standalone file id
         * @throws IOException if response cannot be sent
         */
        private void sendStandaloneFile(HttpExchange exchange, String fileid) throws IOException {
            MapStorage storage = core.getDefaultMapStorage();
            if (storage == null) {
                send(exchange, 503, "Storage Unavailable");
                return;
            }
            BufferInputStream content = storage.getStandaloneFile(fileid);
            if (content == null) {
                send(exchange, 404, "Not Found");
                return;
            }
            sendBuffer(exchange, 200, "application/json; charset=utf-8", content, -1, null);
        }

        /**
         * Send map tile image from storage.
         * @param exchange - HTTP exchange
         * @param tilePath - storage tile path
         * @throws IOException if response cannot be sent
         */
        private void sendStorageTile(HttpExchange exchange, String tilePath) throws IOException {
            String normalized = normalizeStoragePath(tilePath);
            if (normalized == null) {
                send(exchange, 400, "Bad Request");
                return;
            }

            int slash = normalized.indexOf('/');
            if (slash <= 0 || slash + 1 >= normalized.length()) {
                send(exchange, 404, "Not Found");
                return;
            }

            String worldId = normalized.substring(0, slash);
            DynmapWorld world = getWorld(worldId);
            if (world == null) {
                world = core.mapManager.getWorldByStorageId(worldId);
            }
            if (world == null) {
                send(exchange, 404, "Not Found");
                return;
            }

            MapStorage storage = core.getDefaultMapStorage();
            if (storage == null) {
                send(exchange, 503, "Storage Unavailable");
                return;
            }
            MapStorageTile tile = storage.getTile(world, normalized.substring(slash + 1));
            if (tile == null) {
                sendBlankTile(exchange);
                return;
            }

            MapStorageTile.TileRead read = tile.read();
            if (read == null || read.image == null) {
                sendBlankTile(exchange);
                return;
            }
            String contentType = tileContentType(read, normalized);
            sendBuffer(exchange, 200, contentType, read.image, read.lastModified, read.hashCode >= 0 ? Long.toHexString(read.hashCode) : null);
        }

        /**
         * Send marker JSON, marker image, or player face from storage.
         * @param exchange - HTTP exchange
         * @param markerPath - storage marker path
         * @throws IOException if response cannot be sent
         */
        private void sendStorageMarker(HttpExchange exchange, String markerPath) throws IOException {
            String normalized = normalizeStoragePath(markerPath);
            if (normalized == null) {
                send(exchange, 400, "Bad Request");
                return;
            }

            MapStorage storage = core.getDefaultMapStorage();
            if (storage == null) {
                send(exchange, 503, "Storage Unavailable");
                return;
            }

            if (normalized.startsWith("_markers_/marker_") && normalized.endsWith(".json")) {
                String world = normalized.substring("_markers_/marker_".length(), normalized.length() - ".json".length());
                if (!isSafePathPart(world)) {
                    send(exchange, 400, "Bad Request");
                    return;
                }
                String content = storage.getMarkerFile(world);
                if (content == null) {
                    content = "{ }";
                }
                sendBytes(exchange, 200, "application/json; charset=utf-8", content.getBytes(StandardCharsets.UTF_8), -1, null);
                return;
            }

            if (normalized.startsWith("_markers_/") && normalized.endsWith(".png")) {
                String markerid = normalized.substring("_markers_/".length(), normalized.length() - ".png".length());
                if (!isSafeMarkerId(markerid)) {
                    send(exchange, 400, "Bad Request");
                    return;
                }
                BufferInputStream image = storage.getMarkerImage(markerid);
                if (image == null) {
                    sendBlankTile(exchange);
                    return;
                }
                sendBuffer(exchange, 200, "image/png", image, -1, null);
                return;
            }

            if (normalized.startsWith("faces/") && normalized.endsWith(".png")) {
                String[] parts = normalized.split("/");
                if (parts.length != 3) {
                    send(exchange, 404, "Not Found");
                    return;
                }
                PlayerFaces.FaceType faceType = PlayerFaces.FaceType.byID(parts[1]);
                String playerName = parts[2].substring(0, parts[2].length() - ".png".length());
                if (faceType == null || !isSafePathPart(playerName)) {
                    send(exchange, 400, "Bad Request");
                    return;
                }
                BufferInputStream image = storage.getPlayerFaceImage(playerName, faceType);
                if (image == null) {
                    sendBlankTile(exchange);
                    return;
                }
                sendBuffer(exchange, 200, "image/png", image, -1, null);
                return;
            }

            send(exchange, 404, "Not Found");
        }

        /**
         * Find loaded world by name.
         * @param worldName - world name
         * @return world, or null if not found
         */
        private DynmapWorld getWorld(String worldName) {
            if (core.mapManager == null) {
                return null;
            }
            return core.mapManager.getWorld(worldName);
        }

        /**
         * Normalize storage path and reject traversal.
         * @param path - request path
         * @return normalized path, or null if invalid
         */
        private String normalizeStoragePath(String path) {
            if (path == null || path.isBlank()) {
                return null;
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.indexOf('\0') >= 0 || path.contains("..") || path.contains("\\")) {
                return null;
            }
            return path;
        }

        /**
         * Test if path segment is safe.
         * @param value - path segment
         * @return true if safe
         */
        private boolean isSafePathPart(String value) {
            return value != null && !value.isEmpty() && value.indexOf('/') < 0 && value.indexOf('\\') < 0 && !value.contains("..");
        }

        /**
         * Test if marker id is safe.
         * @param value - marker id
         * @return true if safe
         */
        private boolean isSafeMarkerId(String value) {
            return value != null && !value.isEmpty() && value.indexOf('/') < 0 && value.indexOf('\\') < 0 && !value.contains("..");
        }

        /**
         * Send fallback blank tile.
         * @param exchange - HTTP exchange
         * @throws IOException if response cannot be sent
         */
        private void sendBlankTile(HttpExchange exchange) throws IOException {
            Path blank = normalizedWebRoot.resolve("images/blank.png").normalize();
            if (blank.startsWith(normalizedWebRoot) && Files.isRegularFile(blank) && Files.isReadable(blank)) {
                sendBytes(exchange, 200, "image/png", Files.readAllBytes(blank), Files.getLastModifiedTime(blank).toMillis(), null);
            } else {
                send(exchange, 404, "Not Found");
            }
        }

        /**
         * Resolve URI to safe file path under web root.
         * @param uri - request URI
         * @return file path, or null if invalid
         * @throws IOException if real path cannot be resolved
         */
        private Path resolveRequestPath(URI uri) throws IOException {
            String requestPath = decodePath(uri.getRawPath());
            if (requestPath == null || requestPath.indexOf('\0') >= 0) {
                return null;
            }
            if (requestPath.equals("/") || requestPath.isEmpty()) {
                requestPath = "/index.html";
            }
            if (requestPath.endsWith("/")) {
                requestPath = requestPath + "index.html";
            }

            while (requestPath.startsWith("/")) {
                requestPath = requestPath.substring(1);
            }
            Path normalized = normalizedWebRoot.resolve(requestPath).normalize();
            if (!normalized.startsWith(normalizedWebRoot)) {
                return null;
            }
            if (!allowSymlinks && Files.exists(normalized)) {
                Path real = normalized.toRealPath();
                if (!real.startsWith(realWebRoot)) {
                    return null;
                }
                return real;
            }
            return normalized;
        }

        /**
         * Decode percent-encoded UTF-8 path.
         * @param rawPath - raw request path
         * @return decoded path
         */
        private String decodePath(String rawPath) {
            if (rawPath == null) {
                return null;
            }
            byte[] bytes = new byte[rawPath.length()];
            int count = 0;
            for (int i = 0; i < rawPath.length(); i++) {
                char ch = rawPath.charAt(i);
                if (ch == '%' && i + 2 < rawPath.length()) {
                    if (rawPath.charAt(i + 1) == 'u' && i + 5 < rawPath.length()) {
                        int value = 0;
                        for (int j = 2; j < 6; j++) {
                            int digit = Character.digit(rawPath.charAt(i + j), 16);
                            if (digit < 0) {
                                throw new IllegalArgumentException("Invalid encoded path");
                            }
                            value = (value << 4) + digit;
                        }
                        byte[] encoded = String.valueOf((char) value).getBytes(StandardCharsets.UTF_8);
                        System.arraycopy(encoded, 0, bytes, count, encoded.length);
                        count += encoded.length;
                        i += 5;
                        continue;
                    }
                    int hi = Character.digit(rawPath.charAt(i + 1), 16);
                    int lo = Character.digit(rawPath.charAt(i + 2), 16);
                    if (hi < 0 || lo < 0) {
                        throw new IllegalArgumentException("Invalid encoded path");
                    }
                    bytes[count++] = (byte) ((hi << 4) + lo);
                    i += 2;
                } else {
                    byte[] encoded = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                    System.arraycopy(encoded, 0, bytes, count, encoded.length);
                    count += encoded.length;
                }
            }
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, count)).toString();
            } catch (CharacterCodingException ex) {
                throw new IllegalArgumentException("Invalid UTF-8 path", ex);
            }
        }

        /**
         * Resolve content type for static file.
         * @param file - file path
         * @return content type
         */
        private String contentType(Path file) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < name.length()) {
                String type = MIME_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
                if (type != null) {
                    return type;
                }
            }
            try {
                String detected = Files.probeContentType(file);
                if (detected != null) {
                    return detected;
                }
            } catch (IOException ignored) {
            }
            return "application/octet-stream";
        }

        /**
         * Resolve content type for storage tile.
         * @param read - tile read result
         * @param tilePath - requested tile path
         * @return content type
         */
        private String tileContentType(MapStorageTile.TileRead read, String tilePath) {
            if (read.format != null) {
                return read.format.getContentType();
            }

            String fromPath = imageContentTypeFromPath(tilePath);
            if (fromPath != null) {
                return fromPath;
            }

            String fromBytes = imageContentTypeFromBytes(read.image);
            return fromBytes != null ? fromBytes : "application/octet-stream";
        }

        /**
         * Resolve image content type from file extension.
         * @param path - image path
         * @return content type, or null if unknown
         */
        private String imageContentTypeFromPath(String path) {
            int dot = path.lastIndexOf('.');
            if (dot < 0 || dot + 1 >= path.length()) {
                return null;
            }
            String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
            if ("png".equals(ext)) {
                return "image/png";
            }
            if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                return "image/jpeg";
            }
            if ("webp".equals(ext)) {
                return "image/webp";
            }
            if ("gif".equals(ext)) {
                return "image/gif";
            }
            return null;
        }

        /**
         * Resolve image content type from magic bytes.
         * @param image - image bytes
         * @return content type, or null if unknown
         */
        private String imageContentTypeFromBytes(BufferInputStream image) {
            if (image == null || image.length() < 4) {
                return null;
            }
            byte[] data = image.buffer();
            if (image.length() >= 8
                    && (data[0] & 0xFF) == 0x89
                    && data[1] == 0x50
                    && data[2] == 0x4E
                    && data[3] == 0x47
                    && data[4] == 0x0D
                    && data[5] == 0x0A
                    && data[6] == 0x1A
                    && data[7] == 0x0A) {
                return "image/png";
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if (image.length() >= 12
                    && data[0] == 'R'
                    && data[1] == 'I'
                    && data[2] == 'F'
                    && data[3] == 'F'
                    && data[8] == 'W'
                    && data[9] == 'E'
                    && data[10] == 'B'
                    && data[11] == 'P') {
                return "image/webp";
            }
            if (image.length() >= 6
                    && data[0] == 'G'
                    && data[1] == 'I'
                    && data[2] == 'F'
                    && data[3] == '8'
                    && (data[4] == '7' || data[4] == '9')
                    && data[5] == 'a') {
                return "image/gif";
            }
            return null;
        }

        /**
         * Send plain text response.
         * @param exchange - HTTP exchange
         * @param status - HTTP status
         * @param message - response message
         * @throws IOException if response cannot be sent
         */
        private void send(HttpExchange exchange, int status, String message) throws IOException {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            sendBytes(exchange, status, "text/plain; charset=utf-8", data, -1, null);
        }

        /**
         * Send buffer response.
         * @param exchange - HTTP exchange
         * @param status - HTTP status
         * @param contentType - response content type
         * @param content - response body
         * @param lastModified - last modified timestamp
         * @param etag - entity tag
         * @throws IOException if response cannot be sent
         */
        private void sendBuffer(HttpExchange exchange, int status, String contentType, BufferInputStream content, long lastModified, String etag) throws IOException {
            sendBytes(exchange, status, contentType, content.buffer(), content.length(), lastModified, etag);
        }

        /**
         * Send byte array response.
         * @param exchange - HTTP exchange
         * @param status - HTTP status
         * @param contentType - response content type
         * @param data - response body
         * @param lastModified - last modified timestamp
         * @param etag - entity tag
         * @throws IOException if response cannot be sent
         */
        private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] data, long lastModified, String etag) throws IOException {
            sendBytes(exchange, status, contentType, data, data.length, lastModified, etag);
        }

        /**
         * Send byte array response with explicit length.
         * @param exchange - HTTP exchange
         * @param status - HTTP status
         * @param contentType - response content type
         * @param data - response body
         * @param length - response body length
         * @param lastModified - last modified timestamp
         * @param etag - entity tag
         * @throws IOException if response cannot be sent
         */
        private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] data, int length, long lastModified, String etag) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            headers.set("X-Content-Type-Options", "nosniff");
            if (lastModified >= 0) {
                headers.set("Last-Modified", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        java.time.Instant.ofEpochMilli(lastModified).atZone(java.time.ZoneOffset.UTC)));
            }
            if (etag != null) {
                headers.set("ETag", "\"" + etag + "\"");
            }
            exchange.sendResponseHeaders(status, "HEAD".equals(exchange.getRequestMethod()) ? -1 : length);
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.close();
                return;
            }
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data, 0, length);
            }
        }
    }
}
