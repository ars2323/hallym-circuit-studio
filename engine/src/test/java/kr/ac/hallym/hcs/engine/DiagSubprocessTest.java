/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 실제 엔진 프로세스를 한국어 기본 로캘(한국어 Windows 실습실 PC)로 띄워도 Messages의 이름은 영어다(D-143: 엔진의
 * 원조 언어를 영어로 고정한다). 포트 이름이 "출력"이 되지 않고, 한국어 문장은 엔진이 따로 쓴다.
 */
class DiagSubprocessTest {
    @TempDir
    Path tmp;

    @Test
    void namesStayEnglishOnAKoreanPc() throws Exception {
        try (SubprocessTest.Proc p = new SubprocessTest.Proc(tmp.toFile(),
                List.of("-Duser.language=ko", "-Duser.country=KR"))) {
            p.client.callObject("engine.hello", params("client", "test", "version", "0"));
            JsonObject opened = p.client.callObject("file.open", params("path",
                    new File(Fixtures.CIRC_DIR, "faults/static-short.circ").getPath()));
            JsonArray l = p.client.callObject("diag.list", params("fileId", opened.get("fileId").getAsString()))
                    .getAsJsonArray("messages");
            assertEquals(1, l.size(), l.toString());
            JsonObject text = l.get(0).getAsJsonObject().getAsJsonObject("text");
            assertEquals("main 회로의 한 선을 main › NOT #1 output 포트와 main › Buf #1 output 포트가 함께 구동합니다.",
                    text.get("ko").getAsString());
            assertEquals("In main, one wire is driven by both main › NOT #1 output and main › Buf #1 output.",
                    text.get("en").getAsString());
            assertFalse(text.toString().contains("출력"), "port names are English: " + text);
            p.client.call("engine.shutdown", params());
            assertEquals(0, p.awaitExit());
        }
    }
}
