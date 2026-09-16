package com.barclub.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Backup automático de la base de datos con mysqldump.
 * - Corre solo todos los días (cron configurable, por defecto 04:30).
 * - Guarda los archivos en la carpeta configurada y borra los más viejos
 *   que la retención (por defecto 14 días).
 * - También se puede disparar a mano desde el panel (POST /api/backups/ejecutar).
 */
@Slf4j
@Service
public class BackupService {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPass;

    @Value("${app.backup.enabled:true}")
    private boolean enabled;

    @Value("${app.backup.dir:backups}")
    private String dir;

    @Value("${app.backup.retention-days:14}")
    private int retentionDays;

    @Value("${app.backup.mysqldump-path:}")
    private String mysqldumpPath;

    /** Rutas típicas donde MySQL/MariaDB instalan mysqldump en Windows, Mac y Linux. */
    private static final String[] RUTAS_TIPICAS = {
        "C:/Program Files/MySQL/MySQL Server 8.4/bin/mysqldump.exe",
        "C:/Program Files/MySQL/MySQL Server 8.3/bin/mysqldump.exe",
        "C:/Program Files/MySQL/MySQL Server 8.2/bin/mysqldump.exe",
        "C:/Program Files/MySQL/MySQL Server 8.1/bin/mysqldump.exe",
        "C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe",
        "C:/Program Files/MySQL/MySQL Server 5.7/bin/mysqldump.exe",
        "C:/xampp/mysql/bin/mysqldump.exe",
        "C:/wamp64/bin/mysql/mysql8.0.31/bin/mysqldump.exe",
        "C:/laragon/bin/mysql/mysql-8.0.30-winx64/bin/mysqldump.exe",
        "C:/Program Files/MariaDB 11.4/bin/mysqldump.exe",
        "C:/Program Files/MariaDB 10.11/bin/mysqldump.exe",
        "/usr/bin/mysqldump",
        "/usr/local/bin/mysqldump",
        "/opt/homebrew/bin/mysqldump",
        "/usr/local/mysql/bin/mysqldump"
    };

    /**
     * Encuentra mysqldump sin que el usuario tenga que configurar nada:
     * 1) la ruta configurada a mano (si existe),
     * 2) el PATH del sistema,
     * 3) las rutas típicas de instalación,
     * 4) una búsqueda dentro de Program Files por si la versión es otra.
     */
    private String resolverMysqldump() {
        if (mysqldumpPath != null && !mysqldumpPath.isBlank()) {
            Path p = Paths.get(mysqldumpPath);
            if (Files.isExecutable(p) || !mysqldumpPath.contains("/")) return mysqldumpPath;
            if (!Files.exists(p)) {
                throw new IllegalStateException("La ruta configurada de mysqldump no existe: " + mysqldumpPath);
            }
            return mysqldumpPath;
        }
        if (enElPath()) return "mysqldump";
        for (String r : RUTAS_TIPICAS) {
            if (Files.isRegularFile(Paths.get(r))) {
                log.info("mysqldump encontrado automáticamente en: {}", r);
                return r;
            }
        }
        String buscado = buscarEnProgramFiles();
        if (buscado != null) {
            log.info("mysqldump encontrado automáticamente en: {}", buscado);
            return buscado;
        }
        throw new IllegalStateException(
            "No encontré mysqldump en esta computadora. Viene incluido con MySQL: si MySQL está instalado, " +
            "indicá la ruta del archivo mysqldump.exe en app.backup.mysqldump-path (application.properties).");
    }

    private boolean enElPath() {
        try {
            Process p = new ProcessBuilder("mysqldump", "--version")
                    .redirectErrorStream(true).start();
            boolean ok = p.waitFor(8, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (!ok) p.destroyForcibly();
            return ok;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Recorre Program Files buscando cualquier versión de MySQL/MariaDB instalada. */
    private String buscarEnProgramFiles() {
        String[] bases = {"C:/Program Files", "C:/Program Files (x86)"};
        for (String base : bases) {
            Path raiz = Paths.get(base);
            if (!Files.isDirectory(raiz)) continue;
            try (Stream<Path> st = Files.walk(raiz, 4)) {
                java.util.Optional<Path> hit = st
                        .filter(f -> f.getFileName() != null
                                && f.getFileName().toString().equalsIgnoreCase("mysqldump.exe")
                                && Files.isRegularFile(f))
                        .findFirst();
                if (hit.isPresent()) return hit.get().toString();
            } catch (Exception ignore) {}
        }
        return null;
    }

    private volatile String ultimoResultado = "Todavía no se hizo ningún backup en esta sesión.";

    // Antes corría 1 sola vez por día (04:30) — si el servidor se caía o se
    // cortaba la luz en cualquier otro momento del día, se podía perder hasta
    // 24hs de pedidos/ventas sin ningún respaldo. Ahora corre cada 3 horas por
    // defecto (8 backups/día), configurable con BACKUP_CRON si hace falta
    // ajustarlo.
    @Scheduled(cron = "${app.backup.cron:0 0 */3 * * *}")
    public void backupProgramado() {
        if (!enabled) return;
        log.info("Iniciando backup programado de la base de datos...");
        ejecutar();
    }

    // Backup apenas arranca el servidor: cubre el caso de un reinicio/redeploy
    // (o una caída y reinicio tras un corte de luz) que ocurra bastante antes
    // de que toque el próximo backup programado — así siempre queda una foto
    // reciente del momento justo antes de reiniciar, sin depender del reloj.
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void backupAlArrancar() {
        if (!enabled) return;
        log.info("Backup al arrancar el servidor (protección extra ante reinicios inesperados)...");
        try { ejecutar(); } catch (Exception e) { log.warn("Backup al arrancar falló (no es crítico, el programado sigue en pie): {}", e.getMessage()); }
    }

    public synchronized Map<String, Object> ejecutar() {
        Map<String, Object> r = new HashMap<>();
        Path destino = null;
        Path errFile = null;
        try {
            Matcher m = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)").matcher(dbUrl);
            if (!m.find()) throw new IllegalStateException("No pude interpretar la URL de la base de datos");
            String host = m.group(1);
            String port = m.group(2) != null ? m.group(2) : "3306";
            String db = m.group(3);

            Files.createDirectories(Paths.get(dir));
            String nombre = "backup-" + db + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".sql";
            destino = Paths.get(dir, nombre);
            errFile = Paths.get(dir, nombre + ".err");

            String exe = resolverMysqldump();

            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.add("-h"); cmd.add(host);
            cmd.add("-P"); cmd.add(port);
            cmd.add("-u"); cmd.add(dbUser);
            cmd.add("--single-transaction");
            cmd.add("--routines");
            cmd.add("--triggers");
            cmd.add(db);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            // La contraseña va por variable de entorno: no queda visible en la lista de procesos
            pb.environment().put("MYSQL_PWD", dbPass);
            pb.redirectOutput(destino.toFile());
            pb.redirectError(errFile.toFile());

            Process p = pb.start();
            boolean termino = p.waitFor(180, TimeUnit.SECONDS);
            if (!termino) {
                p.destroyForcibly();
                throw new IllegalStateException("mysqldump tardó demasiado y fue cancelado");
            }
            String err = Files.exists(errFile)
                    ? new String(Files.readAllBytes(errFile), StandardCharsets.UTF_8).trim()
                    : "";
            if (p.exitValue() != 0) {
                throw new IllegalStateException("mysqldump falló: " + (err.isBlank()
                        ? "revisá que el usuario y la contraseña de la base sean correctos"
                        : err));
            }

            long kb = Files.size(destino) / 1024;
            if (kb == 0) throw new IllegalStateException("el archivo de backup quedó vacío");

            limpiarViejos();
            String carpeta = Paths.get(dir).toAbsolutePath().toString();
            ultimoResultado = "Último backup OK: " + nombre + " (" + kb + " KB) · " + carpeta;
            log.info("Backup OK: {} ({} KB)", destino.toAbsolutePath(), kb);
            r.put("ok", true);
            r.put("archivo", nombre);
            r.put("tamanoKb", kb);
            r.put("carpeta", carpeta);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ultimoResultado = "Último backup: ERROR (interrumpido)";
            r.put("ok", false);
            r.put("error", "El backup fue interrumpido");
        } catch (Exception e) {
            try { if (destino != null) Files.deleteIfExists(destino); } catch (Exception ignore) {}
            ultimoResultado = "Último backup: ERROR · " + e.getMessage();
            log.error("Backup falló: {}", e.getMessage());
            r.put("ok", false);
            r.put("error", e.getMessage());
        } finally {
            try { if (errFile != null) Files.deleteIfExists(errFile); } catch (Exception ignore) {}
        }
        return r;
    }

    /** Lista los backups guardados, del más nuevo al más viejo. */
    public List<Map<String, Object>> listar() {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Stream<Path> st = Files.list(Paths.get(dir))) {
            List<Path> archivos = st
                    .filter(f -> f.getFileName().toString().startsWith("backup-")
                            && f.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
            for (Path f : archivos) {
                Map<String, Object> m = new HashMap<>();
                m.put("archivo", f.getFileName().toString());
                m.put("tamanoKb", Files.size(f) / 1024);
                m.put("fecha", Files.getLastModifiedTime(f).toInstant().toString());
                out.add(m);
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** Devuelve el contenido de un backup para descargarlo (solo de la carpeta de backups). */
    public byte[] leer(String archivo) throws java.io.IOException {
        if (archivo == null || !archivo.startsWith("backup-") || !archivo.endsWith(".sql")
                || archivo.contains("/") || archivo.contains("\\") || archivo.contains("..")) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }
        Path f = Paths.get(dir).resolve(archivo).normalize();
        if (!f.startsWith(Paths.get(dir).toAbsolutePath().normalize())
                && !f.startsWith(Paths.get(dir).normalize())) {
            throw new IllegalArgumentException("Ruta no permitida");
        }
        if (!Files.isRegularFile(f)) throw new IllegalArgumentException("Ese backup no existe");
        return Files.readAllBytes(f);
    }

    public Map<String, Object> estado() {
        Map<String, Object> m = new HashMap<>();
        m.put("ultimo", ultimoResultado);
        m.put("carpeta", Paths.get(dir).toAbsolutePath().toString());
        long cantidad = 0;
        try (Stream<Path> st = Files.list(Paths.get(dir))) {
            cantidad = st.filter(f -> f.getFileName().toString().startsWith("backup-")
                    && f.getFileName().toString().endsWith(".sql")).count();
        } catch (Exception ignore) {}
        m.put("cantidad", cantidad);
        return m;
    }

    private void limpiarViejos() {
        Instant limite = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try (Stream<Path> st = Files.list(Paths.get(dir))) {
            st.filter(f -> f.getFileName().toString().startsWith("backup-")
                            && f.getFileName().toString().endsWith(".sql"))
              .filter(f -> {
                  try { return Files.getLastModifiedTime(f).toInstant().isBefore(limite); }
                  catch (Exception e) { return false; }
              })
              .forEach(f -> {
                  try {
                      Files.delete(f);
                      log.info("Backup viejo eliminado: {}", f.getFileName());
                  } catch (Exception ignore) {}
              });
        } catch (Exception ignore) {}
    }
}
