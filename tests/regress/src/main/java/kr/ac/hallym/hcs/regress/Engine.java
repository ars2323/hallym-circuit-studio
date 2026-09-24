/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Logisim jar 하나를 {@code -tty table}로 돌려 결과를 문자열로 받는다. */
final class Engine {
    static final long TIMEOUT_SECONDS = 60;

    private final File java;
    private final File jar;

    Engine(File java, File jar) {
        this.java = java;
        this.jar = jar;
    }

    /** 지금 JVM으로 jar를 돌린다. */
    static Engine current(File jar) {
        return new Engine(new File(new File(System.getProperty("java.home"), "bin"), "java"), jar);
    }

    /** tests/circ/<name>.circ의 회로 목록(이름 순). */
    static List<String> circuits(File dir) {
        List<String> names = new ArrayList<String>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".circ")) {
                    names.add(f.getName().substring(0, f.getName().length() - 5));
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /** "exit=<code>" 한 줄과 표준 출력. <name>.args가 있으면 그 인자를 덧붙인다(예: -load). */
    String run(File dir, String name) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<String>(Arrays.asList(
                java.getPath(), "-Djava.awt.headless=true", "-jar", jar.getPath(),
                name + ".circ", "-tty", "table"));
        File args = new File(dir, name + ".args");
        if (args.exists()) {
            String text = new String(Files.readAllBytes(args.toPath()), StandardCharsets.UTF_8).trim();
            cmd.addAll(Arrays.asList(text.split("\\s+")));
        }
        Process p = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> copy(p.getInputStream(), out));
        reader.start();
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException(name + ": no halt within " + TIMEOUT_SECONDS + "s");
        }
        reader.join();
        return "exit=" + p.exitValue() + "\n" + new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        try {
            for (int n; (n = in.read(buf)) > 0;) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            // 프로세스가 끝나면 스트림이 닫힌다.
        }
    }
}
