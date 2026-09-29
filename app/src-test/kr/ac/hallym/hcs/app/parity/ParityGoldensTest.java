/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 편집 동등성 골든(N-01, D-136)의 화면 없는 검사. 골든은 v1 Swing판이 만든 기준이고 그대로 둔다(Swing 재생기는 N-27에서
 * 지웠다, D-163: 엔진이 같은 결과를 내는지는 EngineParityReplayTest가 본다). 의도 파일이 모두 읽히고 장면마다 골든이 있다. 원조 Logisim
 * 2.7.1이 골든을 모두 연다({@code -tty stats} 종료 코드 0, MIPS 부품을 쓰는 골든은 원조가 찾는 자리인 .circ 옆에
 * hcs-mips.jar를 둔다). README 표가 장면·의도 수·원조 결과와 맞고, docs/engine-api.md가 쓰인 메서드를 모두 적는다.
 */
class ParityGoldensTest {
    @TempDir
    Path tmp;

    static Path parityDir() {
        return Paths.get(System.getProperty("hcs.testsDir", "../tests"), "parity");
    }

    static List<IntentScript> scripts() throws IOException {
        List<IntentScript> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(parityDir())) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.getFileName().toString().endsWith(".intents")).sorted()::iterator) {
                out.add(IntentScript.read(p));
            }
        }
        return out;
    }

    @Test
    void everyScenarioParsesAndHasAGolden() throws IOException {
        List<IntentScript> scripts = scripts();
        assertTrue(scripts.size() >= 12, "at least a dozen scenarios");
        TreeSet<String> names = new TreeSet<>();
        for (IntentScript s : scripts) {
            names.add(s.name);
            assertTrue(Files.exists(parityDir().resolve(s.name + ".circ")), s.name + " has a golden");
            assertTrue(s.editCount() > 0, s.name + " edits something");
        }
        try (Stream<Path> files = Files.list(parityDir())) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".circ")).forEach(n ->
                    assertTrue(names.contains(n.substring(0, n.length() - 5)), n + " has intents"));
        }
    }

    @Test
    void engineApiDocumentsEveryMethodTheScenariosUse() throws IOException {
        String doc = new String(Files.readAllBytes(parityDir().resolve("../../docs/engine-api.md").normalize()),
                StandardCharsets.UTF_8);
        for (IntentScript s : scripts()) {
            for (Intent i : s.intents) {
                assertTrue(doc.contains("`" + i.method + "`"), "docs/engine-api.md lists " + i.method);
            }
        }
        for (String m : IntentScript.METHODS.keySet()) {
            assertTrue(doc.contains("`" + m + "`"), "docs/engine-api.md lists " + m);
        }
    }

    /** README 표의 한 줄: | `장면` | 의도 수 | … | 원조 결과 | */
    private static final Pattern ROW = Pattern.compile("^\\| `([^`]+)` \\| (\\d+) \\|.*\\| ([^|]+) \\|\\s*$");

    @Test
    void goldensOpenInTheOriginalAndTheReadmeSaysSo() throws Exception {
        Path dir = tmp.resolve("parity");
        copyTree(parityDir(), dir);
        String jar = System.getProperty("hcs.mipsJar");
        Files.copy(new File(jar).toPath(), dir.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        List<IntentScript> scripts = scripts();
        Map<String, String> results = new TreeMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Map<String, Future<String>> jobs = new TreeMap<>();
            for (IntentScript s : scripts) {
                jobs.put(s.name, pool.submit(() -> stats(dir, s.name)));
            }
            for (Map.Entry<String, Future<String>> e : jobs.entrySet()) {
                results.put(e.getKey(), e.getValue().get(120, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        Map<String, String[]> readme = new TreeMap<>();
        for (String line : Files.readAllLines(parityDir().resolve("README.md"), StandardCharsets.UTF_8)) {
            Matcher m = ROW.matcher(line);
            if (m.matches()) {
                readme.put(m.group(1), new String[] {m.group(2), m.group(3).trim()});
            }
        }
        // 틀린 줄을 한 번에 모두 알린다(README 표를 고칠 때 한 번에 맞출 수 있게)
        List<String> wrong = new ArrayList<>();
        for (IntentScript s : scripts) {
            String[] row = readme.get(s.name);
            String want = "| `" + s.name + "` | " + s.editCount() + " | … | " + results.get(s.name) + " |";
            if (row == null || !row[0].equals(Integer.toString(s.editCount())) || !row[1].equals(results.get(s.name))) {
                wrong.add(want);
            }
        }
        assertTrue(wrong.isEmpty(), "README.md rows should read:\n" + String.join("\n", wrong));
        assertEquals(new TreeSet<>(results.keySet()), new TreeSet<>(readme.keySet()), "README rows = scenarios");
    }

    /**
     * 원조 2.7.1로 {@code -tty stats}를 돌린 결과 글: "열림 · 부품 N"(N은 마지막 TOTAL 줄의 첫 수). 종료 코드가 0이
     * 아니거나 TOTAL 줄이 없거나 표준 오류에 오류가 찍히면 실패.
     */
    static String stats(Path dir, String name) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                new File(new File(System.getProperty("java.home"), "bin"), "java").getPath(),
                "-Djava.awt.headless=true", "-jar", System.getProperty("hcs.logisimJar"), name + ".circ", "-tty",
                "stats"));
        // 원조는 환경설정(최근 파일 등)을 쓴다. 장면마다 따로 둔다: 개발자 PC의 설정을 건드리지 않고, 함께 도는 JVM들이
        // 한 설정 파일의 잠금을 다투지 않게(다투면 "Couldn't flush user prefs" 경고가 난다)
        Path prefs = dir.resolveSibling("prefs-" + name);
        Files.createDirectories(prefs);
        cmd.add(1, "-Djava.util.prefs.userRoot=" + prefs);
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread a = new Thread(() -> copy(p.getInputStream(), out));
        Thread b = new Thread(() -> copy(p.getErrorStream(), err));
        a.start();
        b.start();
        if (!p.waitFor(90, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError(name + ": the original did not finish");
        }
        a.join();
        b.join();
        String stdout = out.toString(StandardCharsets.UTF_8.name());
        String stderr = err.toString(StandardCharsets.UTF_8.name());
        assertEquals(0, p.exitValue(), name + ": original exit code\n" + stdout + stderr);
        for (String line : stderr.split("\n")) {
            String l = line.toLowerCase();
            if (l.contains("java.util.prefs") || l.contains("user prefs")) {
                continue; // JVM 환경설정 저장 경고는 파일을 여는 것과 무관하다
            }
            assertTrue(!l.contains("error") && !l.contains("exception"), name + ": original stderr: " + line);
        }
        String total = null;
        for (String line : stdout.split("\n")) {
            if (line.contains("TOTAL")) {
                total = line.trim().split("\\s+")[0];
            }
        }
        assertTrue(total != null, name + ": no TOTAL line from the original\n" + stdout);
        return "열림 · 부품 " + total;
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        try {
            for (int n; (n = in.read(buf)) > 0;) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            // 프로세스가 끝나면 닫힌다
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
