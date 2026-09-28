/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * 부모가 끝나면 엔진도 끝난다(N-04, D-142): stdin이 열려 있어도. 테스트가 엔진의 stdin을 쥔 채, 엔진을 띄운 중간
 * 프로세스(sh)만 끝낸다. 엔진은 {@code Main.watchParent}로 부모의 끝을 보고 스스로 끝나야 한다(떠도는 java 없음).
 */
class ParentWatchTest {
    @TempDir
    Path tmp;

    @Test
    @EnabledOnOs(OS.LINUX)
    void theEngineEndsWhenItsParentEndsEvenWithItsStdinOpen() throws Exception {
        String jar = new File(SubprocessTest.STAGE, "hcs-engine.jar").getAbsolutePath();
        // sh starts the engine on sh's own stdin (fd 3: an explicit redirection, not /dev/null), prints its pid, and ends in 2 s.
        String script = "exec 3<&0; '" + SubprocessTest.JAVA + "' -Djava.awt.headless=true -Djava.util.prefs.userRoot='"
                + tmp.resolve("prefs") + "' -jar '" + jar + "' 0<&3 & echo $!; sleep 2";
        ProcessBuilder pb = new ProcessBuilder(List.of("sh", "-c", script));
        pb.directory(tmp.toFile());
        Process sh = pb.start();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> {
            try (InputStream in = sh.getErrorStream()) {
                in.transferTo(err);
            } catch (Exception e) {
                // done
            }
        });
        drain.setDaemon(true);
        drain.start();
        BufferedReader out = new BufferedReader(new InputStreamReader(sh.getInputStream(), StandardCharsets.UTF_8));
        long pid = Long.parseLong(out.readLine().trim());
        Optional<ProcessHandle> engine = ProcessHandle.of(pid);
        assertTrue(engine.isPresent() && engine.get().isAlive(), "the engine started");
        // It answers on the stdin this test holds.
        OutputStream in = sh.getOutputStream();
        in.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"engine.hello\",\"params\":{\"client\":\"t\",\"version\":\"0\"}}\n"
                .getBytes(StandardCharsets.UTF_8));
        in.flush();
        assertTrue(out.readLine().contains("\"logisim\":\"2.7.1\""));
        // sh ends; its stdin (the engine's) stays open here.
        assertTrue(sh.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
        long end = System.currentTimeMillis() + 15_000;
        while (engine.get().isAlive() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        boolean alive = engine.get().isAlive();
        if (alive) {
            engine.get().destroyForcibly();
        }
        drain.join(5_000);
        assertFalse(alive, "the engine outlived its parent; stderr: " + err.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("ended"), err.toString(StandardCharsets.UTF_8));
        in.close();
    }

    /**
     * 화면(Electron main)이 죽으면(N-19, D-152): 앱이 맡긴 엔진은 끝나기 전에 저장하지 않은 파일마다 학생 파일 옆에
     * 복구 파일을 쓴다. 테스트가 stdin을 쥔 채 부모(sh)만 죽인다(Windows에서 main을 죽여도 stdin이 닫히지 않던 경우).
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    void whenItsParentIsKilledTheEngineWritesTheRecoveryFilesFirst() throws Exception {
        String jar = new File(SubprocessTest.STAGE, "hcs-engine.jar").getAbsolutePath();
        java.nio.file.Path work = java.nio.file.Files.createDirectory(tmp.resolve("work"));
        File circ = Fixtures.copyWithSiblings(new File(Fixtures.CIRC_DIR, "gates.circ"), work);
        String script = "exec 3<&0; '" + SubprocessTest.JAVA + "' -Djava.awt.headless=true -Djava.util.prefs.userRoot='"
                + tmp.resolve("prefs") + "' -jar '" + jar + "' 0<&3 & echo $!; sleep 120";
        ProcessBuilder pb = new ProcessBuilder(List.of("sh", "-c", script));
        pb.directory(tmp.toFile());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process sh = pb.start();
        BufferedReader out = new BufferedReader(new InputStreamReader(sh.getInputStream(), StandardCharsets.UTF_8));
        ProcessHandle engine = ProcessHandle.of(Long.parseLong(out.readLine().trim())).orElseThrow();
        OutputStream in = sh.getOutputStream();
        String[] calls = {
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"engine.hello\",\"params\":{\"recoveryFiles\":true}}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"file.open\",\"params\":{\"path\":\""
                + circ.getPath().replace("\\", "\\\\") + "\"}}",
        };
        for (String call : calls) {
            in.write((call + "\n").getBytes(StandardCharsets.UTF_8));
            in.flush();
        }
        assertTrue(out.readLine().contains("\"logisim\""));
        String opened = out.readLine();
        String main = opened.replaceAll(".*\"main\":\"(c\\d+)\".*", "$1");
        String fileId = opened.replaceAll(".*\"fileId\":\"(f\\d+)\".*", "$1");
        in.write(("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"edit.addComponent\",\"params\":{\"fileId\":\"" + fileId
                + "\",\"circuitId\":\"" + main + "\",\"lib\":\"Gates\",\"name\":\"NOT Gate\",\"loc\":[900,900]}}\n")
                .getBytes(StandardCharsets.UTF_8));
        in.flush();
        String answer;
        do {
            answer = out.readLine();
        } while (answer != null && !answer.contains("\"id\":3"));
        assertTrue(answer != null && answer.contains("\"changed\":true"), String.valueOf(answer));
        File recovery = new File(circ.getPath() + ".hcs-recover");
        assertFalse(recovery.exists(), "none yet: the main process asks for it after an idle moment");
        sh.destroyForcibly(); // the main process killed; the engine's stdin stays open here
        long end = System.currentTimeMillis() + 15_000;
        while (engine.isAlive() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        boolean alive = engine.isAlive();
        if (alive) {
            engine.destroyForcibly();
        }
        assertFalse(alive, "the engine ends with its parent");
        assertTrue(recovery.isFile(), "the unsaved edit, beside the student's file");
        assertTrue(new String(java.nio.file.Files.readAllBytes(recovery.toPath()), StandardCharsets.UTF_8)
                .contains("NOT Gate"));
        in.close();
    }
}
