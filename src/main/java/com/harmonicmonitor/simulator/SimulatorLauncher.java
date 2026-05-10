package com.harmonicmonitor.simulator;

import com.sun.net.httpserver.*;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;

/**
 * Servidor HTTP mínimo que sirve dashboard.html y gestiona los procesos
 * simuladores como subprocesos del JVM.
 *
 * Endpoints:
 *   GET  /              → dashboard.html
 *   GET  /status        → JSON con estado de cada IED
 *   POST /launch        → lanza un simulador
 *   POST /stop          → detiene un simulador
 *
 * Uso:
 *   java -cp "classes;lib/*" com.harmonicmonitor.simulator.SimulatorLauncher
 *   → Abre http://localhost:8765 en el navegador automáticamente
 */
public class SimulatorLauncher {

    static final int HTTP_PORT = 8765;
    static final Logger LOG = Logger.getLogger(SimulatorLauncher.class.getName());

    // IED-name → proceso vivo
    static final Map<String, Process>      procs    = new ConcurrentHashMap<>();
    // IED-name → stdin del proceso (para enviar comandos en caliente)
    static final Map<String, OutputStream> stdinMap = new ConcurrentHashMap<>();
    // IED-name → puerto MMS (para poder matar procesos huérfanos por puerto)
    static final Map<String, Integer>      iedPorts = new ConcurrentHashMap<>();
    // IED-name → últimas N líneas de log
    static final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();
    static final int MAX_LOG_LINES = 40;

    // Heartbeat: timestamp del último ping recibido del browser
    static volatile long lastPingMs = System.currentTimeMillis();
    static final int PING_TIMEOUT_SEC = 300;  // 5 min — browsers throttle background tabs

    public static void main(String[] args) throws Exception {
        // Logging a consola
        Logger root = Logger.getLogger("");
        Arrays.stream(root.getHandlers()).forEach(root::removeHandler);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.INFO);
        root.addHandler(ch); root.setLevel(Level.INFO);

        // Limpiar procesos huérfanos de sesiones anteriores en los puertos estándar
        LOG.info("Limpiando procesos en puertos 10102-10105...");
        for (int p = 10102; p <= 10105; p++) killProcessOnPort(p);

        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 16);

        server.createContext("/",        SimulatorLauncher::handleRoot);
        server.createContext("/status",  SimulatorLauncher::handleStatus);
        server.createContext("/launch",  SimulatorLauncher::handleLaunch);
        server.createContext("/stop",    SimulatorLauncher::handleStop);
        server.createContext("/ping",       SimulatorLauncher::handlePing);
        server.createContext("/setprofile", SimulatorLauncher::handleSetProfile);

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        String url = "http://localhost:" + HTTP_PORT;
        LOG.info("Dashboard disponible en " + url);
        LOG.info("Ctrl+C para detener todo");

        openBrowser(url);

        // Shutdown hook: matar todos los procesos hijos
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Deteniendo simuladores...");
            procs.values().forEach(p -> { if (p.isAlive()) p.destroyForcibly(); });
            server.stop(1);
        }));

        // Watchdog: si no hay ping del browser en PING_TIMEOUT_SEC, cerrar todo
        Thread watchdog = new Thread(() -> {
            while (true) {
                try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
                long elapsed = (System.currentTimeMillis() - lastPingMs) / 1000;
                if (elapsed > PING_TIMEOUT_SEC) {
                    LOG.info("Sin actividad del browser por " + elapsed + "s — cerrando servidor");
                    procs.values().forEach(p -> { if (p.isAlive()) p.destroyForcibly(); });
                    server.stop(0);
                    System.exit(0);
                }
            }
        });
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    static void handleRoot(HttpExchange ex) throws IOException {
        Path html = Path.of("simulator/dashboard.html");
        if (!Files.exists(html)) {
            respond(ex, 404, "text/plain", "dashboard.html not found");
            return;
        }
        byte[] body = Files.readAllBytes(html);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        cors(ex);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    static void handleStatus(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204,-1); return; }

        StringBuilder sb = new StringBuilder("{");
        procs.forEach((ied, p) -> {
            boolean alive = p.isAlive();
            long pid = p.pid();
            Deque<String> q = logs.getOrDefault(ied, new ArrayDeque<>());
            String logJson = "[" + String.join(",",
                q.stream().map(l -> "\"" + l.replace("\\","\\\\")
                    .replace("\"","\\\"").replace("\n","") + "\"")
                 .toList()) + "]";
            sb.append("\"").append(ied).append("\":{")
              .append("\"running\":").append(alive)
              .append(",\"pid\":").append(alive ? pid : -1)
              .append(",\"log\":").append(logJson)
              .append("},");
        });
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("}");
        respond(ex, 200, "application/json", sb.toString());
    }

    static void handleLaunch(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204,-1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"text/plain","Method Not Allowed"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ied      = jstr(body, "ied",      "SIM1");
        int    port     = jint(body, "port",      10102);
        String profile  = jstr(body, "profile",  "crypto_mining");
        String noise    = jstr(body, "noise",    "0.03");
        int    interval = jint(body, "interval",  5000);

        // Matar proceso existente con el mismo IED (por mapa y por puerto)
        Process old = procs.get(ied);
        if (old != null && old.isAlive()) old.destroyForcibly();
        // Matar cualquier proceso huérfano que ocupe el puerto (e.g. de sesión anterior)
        killProcessOnPort(port);
        logs.put(ied, new ArrayDeque<>());

        // Detectar java ejecutable actual
        String javaExe = ProcessHandle.current().info().command().orElse("java");

        // Classpath relativo al cwd (HarmonicMonitor/)
        String sep = File.pathSeparator;
        String cp  = "classes" + sep + "lib" + File.separator + "*";

        // CID como ruta absoluta para que IonSimServer encuentre templates/
        // independientemente del directorio de trabajo del subproceso
        String cidAbsolute = new File(System.getProperty("user.dir"),
                "simulator" + File.separator + "generic_meter_sim.cid").getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
            javaExe, "-cp", cp,
            "com.harmonicmonitor.simulator.SimulatorMain",
            "--ied",      ied,
            "--port",     String.valueOf(port),
            "--profile",  profile,
            "--noise",    noise,
            "--interval", String.valueOf(interval),
            "--cid",      cidAbsolute
        );
        pb.redirectErrorStream(true);
        pb.directory(new File(System.getProperty("user.dir")));

        try {
            Process p = pb.start();
            procs.put(ied, p);
            iedPorts.put(ied, port);
            stdinMap.put(ied, p.getOutputStream());
            // Hilo que drena stdout y acumula log
            Thread drain = new Thread(() -> drainOutput(ied, p));
            drain.setDaemon(true);
            drain.start();
            LOG.info("Lanzado " + ied + " en puerto " + port + " (pid=" + p.pid() + ")");
            respond(ex, 200, "application/json",
                    "{\"ok\":true,\"pid\":" + p.pid() + "}");
        } catch (IOException e) {
            LOG.warning("Error al lanzar " + ied + ": " + e.getMessage());
            respond(ex, 500, "application/json",
                    "{\"ok\":false,\"error\":\"" + e.getMessage().replace("\"","'") + "\"}");
        }
    }

    static void handlePing(HttpExchange ex) throws IOException {
        cors(ex);
        lastPingMs = System.currentTimeMillis();
        respond(ex, 200, "application/json", "{\"ok\":true}");
    }

    static void handleSetProfile(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204,-1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"text/plain","Method Not Allowed"); return; }

        String body    = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ied     = jstr(body, "ied",     "");
        String profile = jstr(body, "profile", "");

        if (ied.isEmpty() || profile.isEmpty()) {
            respond(ex, 400, "application/json", "{\"ok\":false,\"error\":\"ied y profile requeridos\"}");
            return;
        }

        OutputStream stdin = stdinMap.get(ied);
        Process p = procs.get(ied);
        if (stdin == null || p == null || !p.isAlive()) {
            respond(ex, 404, "application/json", "{\"ok\":false,\"error\":\"simulador no está corriendo\"}");
            return;
        }

        try {
            String cmd = "SET_PROFILE:" + profile + "\n";
            stdin.write(cmd.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            LOG.info("Perfil enviado a " + ied + ": " + profile);
            respond(ex, 200, "application/json", "{\"ok\":true}");
        } catch (IOException e) {
            respond(ex, 500, "application/json", "{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    static void handleStop(HttpExchange ex) throws IOException {
        cors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204,-1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"text/plain","Method Not Allowed"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ied = jstr(body, "ied", "");
        boolean stopped = false;

        // Matar por referencia directa
        Process p = procs.get(ied);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            stopped = true;
        }

        // Matar por puerto (captura procesos huérfanos de sesiones anteriores)
        Integer iPort = iedPorts.get(ied);
        if (iPort != null) {
            killProcessOnPort(iPort);
            stopped = true;
        }

        if (stopped) {
            appendLog(ied, "[ detenido por el usuario ]");
            LOG.info("Detenido simulador " + ied);
            respond(ex, 200, "application/json", "{\"ok\":true}");
        } else {
            respond(ex, 200, "application/json", "{\"ok\":false,\"msg\":\"no estaba corriendo\"}");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Mata cualquier proceso que esté usando el puerto TCP dado.
     * Usa netstat + ProcessHandle para no depender de taskkill.
     */
    static void killProcessOnPort(int port) {
        try {
            Process netstat = new ProcessBuilder(
                "cmd", "/c", "netstat -aon")
                .redirectErrorStream(true)
                .start();
            String out = new String(netstat.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            netstat.waitFor(3, TimeUnit.SECONDS);

            String target = ":" + port + " ";
            for (String line : out.split("\r?\n")) {
                if (!line.contains(target)) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 1) continue;
                String pidStr = parts[parts.length - 1].trim();
                try {
                    long pid = Long.parseLong(pidStr);
                    if (pid <= 0) continue;
                    ProcessHandle.of(pid).ifPresent(ph -> {
                        ph.destroyForcibly();
                        LOG.info("Proceso huérfano PID=" + pid + " en puerto " + port + " eliminado");
                    });
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            LOG.warning("killProcessOnPort(" + port + "): " + e.getMessage());
        }
    }

    static void drainOutput(String ied, Process p) {
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[" + ied + "] " + line);
                appendLog(ied, line);
            }
        } catch (IOException ignored) {}
        appendLog(ied, "[ proceso terminado ]");
    }

    static void appendLog(String ied, String line) {
        Deque<String> q = logs.computeIfAbsent(ied, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(line);
            while (q.size() > MAX_LOG_LINES) q.pollFirst();
        }
    }

    static void respond(HttpExchange ex, int code, String ct, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct + "; charset=UTF-8");
        cors(ex);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static void cors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin",  "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            LOG.info("Abrir manualmente: " + url);
        }
    }

    // ── Mini parseador JSON (solo para el formato enviado por el dashboard) ──

    static String jstr(String json, String key, String def) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : def;
    }

    static int jint(String json, String key, int def) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }
}
