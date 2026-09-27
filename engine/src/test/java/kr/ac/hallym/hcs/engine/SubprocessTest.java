/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/**
 * 실제 하위 프로세스({@code java -jar hcs-engine.jar}, hcs-mips.jar는 옆에서 찾는다): 왕복, 종료, 시작 시간과
 * 메모리, 메모리 전용 환경설정(임시 HOME에 아무것도 남기지 않고 원조 설정을 읽지 않는다).
 */
class SubprocessTest {
    static final File STAGE = new File(System.getProperty("hcs.engineStage"));
    static final String JAVA = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath();

    @TempDir
    Path tmp;

    /** 엔진 하위 프로세스. */
    static final class Proc implements AutoCloseable {
        final Process process;
        final Client client;
        final long startedNanos;
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();

        Proc(File home, List<String> jvmArgs) throws IOException {
            List<String> cmd = new ArrayList<>();
            cmd.add(JAVA);
            cmd.add("-Duser.home=" + home.getAbsolutePath());
            cmd.addAll(jvmArgs);
            cmd.add("-jar");
            cmd.add(new File(STAGE, "hcs-engine.jar").getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("HOME", home.getAbsolutePath());
            pb.environment().put("XDG_CONFIG_HOME", new File(home, ".config").getAbsolutePath());
            pb.environment().put("APPDATA", new File(home, "AppData").getAbsolutePath());
            pb.directory(home);
            startedNanos = System.nanoTime();
            process = pb.start();
            Thread drain = new Thread(() -> copy(process.getErrorStream()), "engine-stderr");
            drain.setDaemon(true);
            drain.start();
            client = new Client(process.getInputStream(), process.getOutputStream());
        }

        private void copy(InputStream in) {
            byte[] buf = new byte[4096];
            try {
                for (int n; (n = in.read(buf)) > 0;) {
                    synchronized (err) {
                        err.write(buf, 0, n);
                    }
                }
            } catch (IOException e) {
                // 끝
            }
        }

        String stderr() {
            synchronized (err) {
                return err.toString(StandardCharsets.UTF_8);
            }
        }

        /** 끝날 때까지 기다리고 종료 코드. */
        int awaitExit() throws InterruptedException {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "the engine exits; stderr: " + stderr());
            return process.exitValue();
        }

        /** Linux: 상주 메모리(kB). 없으면 -1. */
        long rssKb() throws IOException {
            File status = new File("/proc/" + process.pid() + "/status");
            if (!status.canRead()) {
                return -1;
            }
            for (String line : Files.readAllLines(status.toPath())) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
            return -1;
        }

        @Override
        public void close() {
            process.destroyForcibly();
        }
    }

    @Test
    void roundTripAndShutdownThroughARealProcess() throws Exception {
        try (Proc p = new Proc(tmp.toFile(), List.of())) {
            JsonObject hello = p.client.callObject("engine.hello", params("client", "test", "version", "0"));
            assertEquals("hcs-engine", hello.get("engine").getAsString());
            assertEquals("0.1.0", hello.get("version").getAsString(), "the jar manifest carries the version");
            JsonObject opened = p.client.callObject("file.open", params("path", Fixtures.REF_MIPS.getPath()));
            assertEquals(new com.google.gson.JsonArray(), opened.getAsJsonArray("messages"),
                    "hcs-mips.jar is found next to hcs-engine.jar");
            JsonObject snap = p.client.callObject("model.circuit", params("fileId", opened.get("fileId").getAsString(),
                    "circuitId", opened.get("main").getAsString()));
            assertTrue(snap.getAsJsonArray("components").size() > 700);
            assertEquals(new JsonObject(), p.client.call("engine.shutdown", params()));
            assertEquals(0, p.awaitExit());
            assertTrue(p.client.awaitEof(10_000));
            assertTrue(p.stderr().contains("exit (shutdown)"), p.stderr());
        }
    }

    /** 배포 jar: 실행 클래스, Gson과 그 라이선스, NOTICE, GPL 전문. 화면 전용 짐(FlatLaf, 도움말, 글꼴)은 없다. */
    @Test
    void theEngineJarCarriesItsLicensesAndNoSwingOnlyBaggage() throws Exception {
        File jar = new File(STAGE, "hcs-engine.jar");
        assertTrue(new File(STAGE, "hcs-mips.jar").isFile(), "hcs-mips.jar sits next to the engine jar");
        try (java.util.jar.JarFile j = new java.util.jar.JarFile(jar)) {
            assertEquals("kr.ac.hallym.hcs.engine.Main", j.getManifest().getMainAttributes().getValue("Main-Class"));
            for (String must : new String[] {"com/google/gson/Gson.class", "META-INF/LICENSE-Gson.txt", "META-INF/NOTICE",
                "COPYING.TXT", "com/cburch/logisim/file/Loader.class", "resources/logisim/default.templ"}) {
                assertTrue(j.getEntry(must) != null, "has " + must);
            }
            String notice = new String(j.getInputStream(j.getEntry("META-INF/NOTICE")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(notice.contains("Gson") && notice.contains("Apache License, Version 2.0"));
            for (java.util.Enumeration<java.util.jar.JarEntry> en = j.entries(); en.hasMoreElements();) {
                String name = en.nextElement().getName();
                assertFalse(name.startsWith("com/formdev/") || name.startsWith("doc/")
                        || name.startsWith("kr/ac/hallym/hcs/app/fonts/") || name.equals("kr/ac/hallym/hcs/mips/MipsLibrary.class")
                        || name.endsWith("module-info.class"), name);
            }
        }
        assertTrue(jar.length() < 8L * 1024 * 1024, "the engine jar stays small: " + jar.length());
    }

    @Test
    void closingStdinEndsTheProcess() throws Exception {
        try (Proc p = new Proc(tmp.toFile(), List.of())) {
            p.client.call("engine.hello", params());
            p.client.call("file.new", params());
            p.client.closeInput();
            assertEquals(0, p.awaitExit());
            assertTrue(p.stderr().contains("exit (stdin closed)"), p.stderr());
        }
    }

    @Test
    void stdoutCarriesOnlyProtocolLines() throws Exception {
        try (Proc p = new Proc(tmp.toFile(), List.of())) {
            p.client.call("engine.hello", params());
            JsonObject opened = p.client.callObject("file.open", params("path",
                    new File(Fixtures.CIRC_DIR, "gates.circ").getPath()));
            p.client.call("sim.watch", params("fileId", opened.get("fileId").getAsString(), "circuitId",
                    opened.get("main").getAsString()));
            p.client.call("engine.shutdown", params());
            p.awaitExit();
            for (String line : p.client.rawLines()) {
                assertTrue(line.startsWith("{\"jsonrpc\":\"2.0\""), line);
            }
        }
    }

    /**
     * 임시 HOME에 원조 Logisim 설정(틱 64 Hz)을 JDK 기본 방식으로 써 두고 엔진을 돌린다. 엔진은 그 값을 읽지 않고(hz 1),
     * 끝난 뒤 HOME에 새로 생기거나 바뀐 파일이 없다. 단 JDK 글꼴 목록 캐시({@code .java/fonts}, Linux fontconfig
     * 전용이고 환경설정이 아니다)는 뺀다(D-134).
     */
    @Test
    void preferencesStayInMemory() throws Exception {
        File home = tmp.resolve("home").toFile();
        assertTrue(home.mkdirs());
        String cp = new File(PrefsTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        run(home, JAVA, "-Duser.home=" + home, "-cp", cp, PrefsTool.class.getName(), "write", "64");
        assertEquals("64.0", run(home, JAVA, "-Duser.home=" + home, "-cp", cp, PrefsTool.class.getName(), "read")
                .trim(), "a plain JVM reads the original Logisim preference");
        Map<String, String> before = tree(home);
        assertTrue(before.keySet().stream().anyMatch(k -> k.contains(".userPrefs/com/cburch/logisim")), "" + before);

        File work = tmp.resolve("work").toFile();
        assertTrue(work.mkdirs());
        try (Proc p = new Proc(home, List.of())) {
            JsonObject created = p.client.callObject("file.new", params());
            String fileId = created.get("fileId").getAsString();
            JsonObject st = p.client.callObject("sim.state", params("fileId", fileId));
            assertEquals(1.0, st.get("hz").getAsDouble(), "the original tick frequency (64) is not read");
            p.client.call("edit.addComponent", params("fileId", fileId, "circuitId", created.get("main").getAsString(),
                    "lib", "Gates", "name", "AND Gate", "loc", xy(200, 200)));
            p.client.call("sim.run", params("fileId", fileId, "on", true, "hz", 16));
            p.client.call("sim.run", params("fileId", fileId, "on", false));
            p.client.call("file.save", params("fileId", fileId, "path", new File(work, "a.circ").getPath()));
            JsonObject opened = p.client.callObject("file.open", params("path", Fixtures.REF_MIPS.getPath()));
            p.client.call("model.library", params("fileId", opened.get("fileId").getAsString()));
            p.client.call("engine.shutdown", params());
            assertEquals(0, p.awaitExit());
        }
        Map<String, String> after = tree(home);
        after.keySet().removeIf(k -> k.startsWith(".java/fonts/") || k.equals(".java/fonts"));
        assertEquals(before, after, "nothing but the JDK font cache is written under HOME");
    }

    /** 시작 시간(프로세스 시작 → engine.hello 응답)과 ref-mips를 연 뒤의 상주 메모리를 잰다. */
    @Test
    @Tag("timing")
    void measureStartTimeAndMemory() throws Exception {
        StringBuilder report = new StringBuilder();
        for (List<String> flags : List.of(List.<String>of(), List.of("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1"))) {
            List<Long> starts = new ArrayList<>();
            long rss = -1;
            long rssIdle = -1;
            for (int i = 0; i < 3; i++) {
                try (Proc p = new Proc(tmp.toFile(), flags)) {
                    p.client.call("engine.hello", params("client", "measure", "version", "0"));
                    starts.add((System.nanoTime() - p.startedNanos) / 1_000_000);
                    if (i == 0) {
                        rssIdle = p.rssKb();
                        JsonObject opened = p.client.callObject("file.open", params("path",
                                Fixtures.REF_MIPS.getPath()));
                        String fileId = opened.get("fileId").getAsString();
                        p.client.call("model.circuit", params("fileId", fileId, "circuitId",
                                opened.get("main").getAsString()));
                        int mark = p.client.mark();
                        p.client.call("sim.watch", params("fileId", fileId, "circuitId",
                                opened.get("main").getAsString()));
                        p.client.awaitNotificationAfter(mark, "sim.values", v -> true);
                        Thread.sleep(300);
                        rss = p.rssKb();
                    }
                    p.client.call("engine.shutdown", params());
                    p.awaitExit();
                }
            }
            starts.sort(null);
            report.append(String.format("flags=%s start(ms) median=%d all=%s rssIdle(MB)=%.1f rssRefMips(MB)=%.1f%n",
                    flags, starts.get(1), starts, rssIdle / 1024.0, rss / 1024.0));
            assertTrue(starts.get(1) < 20_000, "starts within 20 s");
            assertTrue(rss < 0 || rss < 1024 * 1024, "stays under 1 GiB");
        }
        System.out.print(report);
        String out = System.getProperty("hcs.measureFile");
        if (out != null) {
            Files.write(new File(out).toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        }
        assertFalse(report.length() == 0);
    }

    static String run(File dir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    /** 폴더 안 파일 경로 → 크기와 SHA-256. */
    static Map<String, String> tree(File root) throws Exception {
        Map<String, String> ret = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root.toPath())) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String rel = root.toPath().relativize(p).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(p)) {
                    ret.put(rel + "/", "dir");
                } else {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    ret.put(rel, Files.size(p) + ":" + java.util.HexFormat.of().formatHex(md.digest(Files.readAllBytes(p))));
                }
            }
        }
        ret.remove("/");
        return ret;
    }
}
