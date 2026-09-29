/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/**
 * 학생이 보는 예제 회로에 부품끼리 겹친 곳이 없다(D-156). 규칙은 {@link LayoutOverlaps}: 엔진이 보내는 부품 경계가
 * 넓이 있게 겹치면 겹침이고, 맞닿음·포트에서 바깥으로 뻗는 부착·부채꼴 이웃만 정상이다. 규칙 자체는 엔진으로 놓은
 * 작은 회로로 확인한다(원래 demo-datapath의 비교기 위 pc 터널이 겹침으로 잡힌다).
 */
class ExampleLayoutTest {
    static final Path REPO = new File(System.getProperty("hcs.circDir")).toPath().getParent().getParent();

    /**
     * 검사하는 회로: 학생에게 가는 예제(v1 Help › Examples의 셋), 참조 CPU(화면 스크린샷·진짜 엔진 e2e가 연다),
     * demo-datapath에서 만든 고장 회로(Messages 스크린샷). tests/mips/ref-mips-v1-stack.circ는 옛 파일 회귀용으로
     * 그대로 둔 파일이라, tests/circ의 작은 회귀 회로·고장·흐름 회로는 학생에게 가지 않는 시험 입력이라 뺀다.
     */
    static final List<String> CHECKED = List.of("tests/circ/demo-datapath.circ", "tests/circ/console-demo.circ",
            "tests/circ/stack-demo.circ", "tests/mips/ref-mips.circ", "electron/tests/fixtures/broken-datapath.circ");

    @TempDir
    Path tmp;

    InProcess e;
    String fileId;
    String main;

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    @TestFactory
    Stream<DynamicTest> examplesHaveNoOverlappingParts() {
        return CHECKED.stream().map(name -> DynamicTest.dynamicTest(name, () -> {
            List<LayoutOverlaps.Overlap> found = LayoutOverlaps.scan(e, REPO.resolve(name).toFile(), tmp);
            assertEquals(List.of(), found.stream().map(Object::toString).toList(),
                    name + ": parts overlap (move them apart, D-156)");
        }));
    }

    // ---- 규칙 ----

    void fresh() {
        JsonObject r = e.client.callObject("file.new", params());
        fileId = r.get("fileId").getAsString();
        main = r.get("main").getAsString();
    }

    void add(String lib, String name, int x, int y, Object... attrs) {
        e.client.callObject("edit.addComponent", params("fileId", fileId, "circuitId", main, "lib", lib, "name", name,
                "loc", xy(x, y), "attrs", params(attrs)));
    }

    List<String> overlaps() {
        JsonObject snap = e.client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
        return LayoutOverlaps.find(snap).stream().map(Object::toString).toList();
    }

    /** 원래 demo-datapath: 비교기 왼쪽 입력에 붙은 pc 터널이 오른쪽(몸체 쪽)으로 뻗었다. */
    @Test
    void aTunnelLyingOnThePartItIsAttachedToIsAnOverlap() {
        fresh();
        add("Arithmetic", "Comparator", 360, 460, "width", "32");
        add("Wiring", "Tunnel", 320, 450, "width", "32", "label", "pc"); // 기본 facing west: 몸체가 동쪽
        assertEquals(List.of("main: Tunnel \"pc\" at (320,450) west × Comparator at (360,460) (20×18)"), overlaps());
    }

    @Test
    void aTunnelReachingOutwardFromItsPortIsNot() {
        fresh();
        add("Arithmetic", "Comparator", 360, 460, "width", "32");
        add("Wiring", "Tunnel", 320, 450, "width", "32", "label", "pc", "facing", "east");
        add("Wiring", "Tunnel", 360, 450, "label", "gt", "facing", "west"); // 출력 쪽: 몸체가 동쪽으로
        assertEquals(List.of(), overlaps());
    }

    /** 멀티플렉서의 선택 포트는 경계 안쪽(비스듬한 몸체 가장자리)에 있다: 아래로 뻗는 터널은 정상. */
    @Test
    void aTunnelOnASelectPortInsideTheBoundsReachingOutIsNot() {
        fresh();
        add("Plexers", "Multiplexer", 650, 450, "select", "2", "width", "32", "enable", "false");
        add("Wiring", "Tunnel", 630, 470, "width", "2", "label", "op", "facing", "north");
        assertEquals(List.of(), overlaps());
        add("Wiring", "Tunnel", 630, 470, "width", "2", "label", "op2", "facing", "west"); // 몸체 쪽(동쪽)
        assertEquals(1, overlaps().stream().filter(s -> s.contains("\"op2\"") && s.contains("Multiplexer")).count(),
                overlaps().toString());
    }

    /** 스플리터 팔(10 간격)의 터널: 모두 팔 쪽(동쪽)으로 뻗으면 서로 조금 겹쳐도 정상, 팔을 따라 뻗으면 겹침. */
    @Test
    void splitterFanOutTunnels() {
        fresh();
        add("Wiring", "Splitter", 200, 200, "fanout", "3", "incoming", "3");
        // 팔 0..2: (220,170) (220,180) (220,190)
        add("Wiring", "Tunnel", 220, 170, "label", "a");
        add("Wiring", "Tunnel", 220, 180, "label", "b");
        add("Wiring", "Tunnel", 220, 190, "label", "c");
        assertEquals(List.of(), overlaps());

        fresh();
        add("Wiring", "Splitter", 200, 200, "fanout", "3", "incoming", "3");
        add("Wiring", "Tunnel", 220, 170, "label", "a", "facing", "north"); // 몸체가 아래 팔 쪽으로
        add("Wiring", "Tunnel", 220, 180, "label", "b", "facing", "north");
        add("Wiring", "Tunnel", 220, 190, "label", "c", "facing", "north");
        List<String> found = overlaps();
        assertEquals(5, found.size(), found.toString());
        assertTrue(found.contains("main: Tunnel \"a\" at (220,170) north × Tunnel \"b\" at (220,180) north (16×10)"),
                found.toString());
        assertTrue(found.contains("main: Tunnel \"a\" at (220,170) north × Splitter at (200,200) east (9×20)"),
                found.toString());
    }

    /** 부채꼴 이웃이라도 같은 방향이 아니거나 한 칸보다 멀면 예외가 아니다. */
    @Test
    void neighboursMustShareFacingAndPitch() {
        fresh();
        add("Plexers", "Multiplexer", 850, 500, "select", "2", "width", "32", "enable", "false");
        add("Wiring", "Tunnel", 810, 480, "width", "32", "label", "r0", "facing", "east");
        add("Wiring", "Tunnel", 810, 490, "width", "32", "label", "r1", "facing", "east");
        assertEquals(List.of(), overlaps());
        add("Wiring", "Tunnel", 810, 500, "width", "32", "label", "r2"); // 몸체 동쪽: 멀티플렉서 위
        List<String> found = overlaps();
        assertEquals(List.of("main: Tunnel \"r2\" at (810,500) west × Multiplexer at (850,500) east (20×18)"), found);
        add("Wiring", "Tunnel", 810, 510, "width", "32", "label", "r3", "facing", "north"); // 같은 부모, 다른 방향
        found = overlaps(); // 아래 몸체가 부모 쪽: 이웃 예외도 바깥 예외도 아니다
        assertEquals(2, found.size(), found.toString());
        assertEquals(1, found.stream().filter(x -> x.contains("\"r3\" at (810,510) north") && x.contains("Multiplexer"))
                .count(), found.toString());
    }

    /** 같은 자리의 두 터널은 겹침(부채꼴 이웃은 서로 다른 자리). */
    @Test
    void twoTunnelsOnOnePointOverlap() {
        fresh();
        add("Wiring", "Pin", 100, 100, "label", "x");
        add("Wiring", "Tunnel", 100, 100, "label", "x");
        add("Wiring", "Tunnel", 100, 100, "label", "y");
        assertEquals(List.of("main: Tunnel \"x\" at (100,100) west × Tunnel \"y\" at (100,100) west (18×18)"),
                overlaps().stream().filter(s -> s.contains("Tunnel \"x\" at (100,100) west × Tunnel")).toList());
    }

    /** 맞닿음(겹친 폭 2 이하)과 글(Base Text)은 겹침이 아니다. 경계가 넓게 겹친 두 부품은 겹침. */
    @Test
    void touchingTextAndPlainOverlap() {
        fresh();
        add("Gates", "AND Gate", 200, 200);
        add("Gates", "AND Gate", 250, 200); // 경계 [150,175,50,50]과 [200,175,50,50]: 맞닿음
        add("Base", "Text", 180, 200, "text", "note over the gate");
        assertEquals(List.of(), overlaps());
        add("Gates", "OR Gate", 230, 210);
        List<String> found = overlaps();
        assertEquals(2, found.size(), found.toString());
        assertTrue(found.contains("main: AND Gate at (200,200) east × OR Gate at (230,210) east (20×40)"),
                found.toString());
        assertTrue(found.contains("main: AND Gate at (250,200) east × OR Gate at (230,210) east (30×40)"),
                found.toString());
    }
}
