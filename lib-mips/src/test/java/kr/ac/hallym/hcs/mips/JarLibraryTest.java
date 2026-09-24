/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

/**
 * 원조 Logisim 2.7.1이 JAR 라이브러리를 어떻게 불러오고 .circ에 어떻게 저장하는지 고정한다
 * (docs/jar-library.md, 이슈 #4).
 */
class JarLibraryTest {
    static final Path LOGISIM_JAR = Path.of(System.getProperty("hcs.logisimJar"));
    static final Path SMOKE_JAR = Path.of(System.getProperty("hcs.smokeJar"));
    static final Path MIPS_JAR = Path.of(System.getProperty("hcs.mipsJar"));
    static final Path FIXTURES = Path.of(System.getProperty("hcs.testsDir"), "jarlib");

    static final String SMOKE_CLASS = "kr.ac.hallym.hcs.smoke.SmokeLibrary";
    /** 0x29 + 1 = 0x2A를 2.7.1의 -tty table 형식으로 쓴 값. */
    static final String SMOKE_EXPECTED = "0000 0000 0000 0000 0000 0000 0010 1010";

    @TempDir
    Path tmp;

    record Run(int exit, String stdout, String stderr) {}

    /** 원조 2.7.1을 헤드리스 -tty 모드로 실행한다. */
    static Run runOriginal(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-jar", LOGISIM_JAR.toString()));
        cmd.addAll(Arrays.asList(args));
        Path out = dir.resolve("stdout.txt");
        Path err = dir.resolve("stderr.txt");
        Process p = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "Logisim이 60초 안에 끝나지 않음");
        return new Run(p.exitValue(), Files.readString(out).strip(), Files.readString(err));
    }

    /** smoke.circ를 dir에 두고 jar 경로 부분만 바꾼다. */
    static Path writeSmokeCircuit(Path dir, String jarPath) throws IOException {
        String xml = Files.readString(FIXTURES.resolve("smoke.circ"), StandardCharsets.UTF_8)
                .replace("jar#hcs-smoke.jar#", "jar#" + jarPath + "#");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve("smoke.circ"), xml, StandardCharsets.UTF_8);
    }

    static void copyJar(Path jar, Path dest) throws IOException {
        Files.createDirectories(dest.getParent());
        Files.copy(jar, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 원조 2.7.1의 Loader로 열었다가 다른 이름으로 저장하고, 저장된 jar 설명자를 돌려준다. */
    static String savedJarDescriptor(Path circ) throws Exception {
        Loader loader = new Loader(null);
        LogisimFile file = loader.openLogisimFile(circ.toFile());
        File dest = circ.resolveSibling("saved.circ").toFile();
        assertTrue(loader.save(file, dest), "저장 실패");
        String saved = Files.readString(dest.toPath(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<lib desc=\"(jar#[^\"]*)\"").matcher(saved);
        assertTrue(m.find(), "저장된 파일에 jar 라이브러리가 없음");
        return m.group(1);
    }

    @Test
    void originalLoadsJarNextToCircuit() throws Exception {
        Path circ = writeSmokeCircuit(tmp, "hcs-smoke.jar");
        copyJar(SMOKE_JAR, tmp.resolve("hcs-smoke.jar"));
        Run r = runOriginal(tmp, circ.toString(), "-tty", "table");
        assertEquals(0, r.exit(), r.stderr());
        assertEquals(SMOKE_EXPECTED, r.stdout());
    }

    @Test
    void originalLoadsJarInSubdirectory() throws Exception {
        Path circ = writeSmokeCircuit(tmp, "lib/hcs-smoke.jar");
        copyJar(SMOKE_JAR, tmp.resolve("lib/hcs-smoke.jar"));
        Run r = runOriginal(tmp, circ.toString(), "-tty", "table");
        assertEquals(0, r.exit(), r.stderr());
        assertEquals(SMOKE_EXPECTED, r.stdout());
    }

    @Test
    void originalLoadsEmptyMipsLibrary() throws Exception {
        Files.copy(FIXTURES.resolve("mips-empty.circ"), tmp.resolve("mips-empty.circ"));
        copyJar(MIPS_JAR, tmp.resolve("hcs-mips.jar"));
        Run r = runOriginal(tmp, "mips-empty.circ", "-tty", "table");
        assertEquals(0, r.exit(), r.stderr());
        assertEquals("1", r.stdout());
    }

    /**
     * jar가 없으면 2.7.1은 파일 선택 대화상자를 띄운다. 헤드리스에서는 그 자리에서 실패한다.
     * -sub 치환도 파일을 먼저 찾은 뒤에 적용되므로 없는 파일은 구하지 못한다.
     */
    @Test
    void missingJarNeedsDialogEvenWithSubstitution() throws Exception {
        Path circ = writeSmokeCircuit(tmp, "hcs-smoke.jar");
        copyJar(SMOKE_JAR, tmp.resolve("elsewhere/hcs-smoke.jar"));

        Run plain = runOriginal(tmp, circ.toString(), "-tty", "table");
        assertNotEquals(0, plain.exit());
        assertTrue(plain.stderr().contains("HeadlessException"), plain.stderr());

        Run sub = runOriginal(tmp, circ.toString(), "-tty", "table",
                "-sub", "hcs-smoke.jar", "elsewhere/hcs-smoke.jar");
        assertNotEquals(0, sub.exit());
        assertTrue(sub.stderr().contains("HeadlessException"), sub.stderr());
    }

    /** 같은 폴더, 한 단계 아래, 한 단계 위만 상대 경로로 저장하고 나머지는 절대 경로가 된다. */
    @Test
    void savedDescriptorIsRelativeOnlyForNearbyJar() throws Exception {
        Path same = tmp.resolve("same");
        copyJar(SMOKE_JAR, same.resolve("hcs-smoke.jar"));
        assertEquals("jar#hcs-smoke.jar#" + SMOKE_CLASS,
                savedJarDescriptor(writeSmokeCircuit(same, "hcs-smoke.jar")));

        Path sub = tmp.resolve("sub");
        copyJar(SMOKE_JAR, sub.resolve("lib/hcs-smoke.jar"));
        assertEquals("jar#lib/hcs-smoke.jar#" + SMOKE_CLASS,
                savedJarDescriptor(writeSmokeCircuit(sub, "lib/hcs-smoke.jar")));

        Path up = tmp.resolve("up");
        copyJar(SMOKE_JAR, up.resolve("hcs-smoke.jar"));
        assertEquals("jar#../hcs-smoke.jar#" + SMOKE_CLASS,
                savedJarDescriptor(writeSmokeCircuit(up.resolve("proj"), "../hcs-smoke.jar")));

        Path deep = tmp.resolve("deep");
        copyJar(SMOKE_JAR, deep.resolve("a/b/hcs-smoke.jar"));
        String absolute = deep.resolve("a/b/hcs-smoke.jar").toFile().getCanonicalPath();
        assertEquals("jar#" + absolute + "#" + SMOKE_CLASS,
                savedJarDescriptor(writeSmokeCircuit(deep, "a/b/hcs-smoke.jar")));
    }

    /**
     * fixture는 원조 2.7.1을 JDK 8에서 돌려 저장한 결과다. 테스트 JRE에서 다시 저장하면 바이트는
     * 달라도(D-006) 정규화하면 같다.
     */
    @Test
    void resaveMatchesFixtureAfterNormalization() throws Exception {
        Path circ = writeSmokeCircuit(tmp, "hcs-smoke.jar");
        copyJar(SMOKE_JAR, tmp.resolve("hcs-smoke.jar"));
        savedJarDescriptor(circ);
        String fixture = Files.readString(circ);
        String resaved = Files.readString(tmp.resolve("saved.circ"));
        assertEquals(CircNormalizer.normalize(fixture), CircNormalizer.normalize(resaved));
    }

    @Test
    void normalizerKeepsEverythingButWhitespaceAndItemOrder() {
        String a = "<project>\n<circuit name=\"m\">\n  <a name=\"circuit\" val=\"m\"/>\n"
                + "  <wire from=\"(1,1)\" to=\"(2,1)\"/>\n  <comp lib=\"0\" loc=\"(5,5)\" name=\"Pin\">\n"
                + "    <a name=\"x\" val=\"1\"/>\n  </comp>\n</circuit>\n</project>\n";
        String b = "<project>\n\n  <circuit name=\"m\">\n<a name=\"circuit\" val=\"m\"/>\n"
                + "<comp lib=\"0\" loc=\"(5,5)\" name=\"Pin\">\n<a name=\"x\" val=\"1\"/>\n</comp>\n"
                + "<wire from=\"(1,1)\" to=\"(2,1)\"/>\n</circuit>\n</project>\n";
        assertEquals(CircNormalizer.normalize(a), CircNormalizer.normalize(b));
        assertNotEquals(CircNormalizer.normalize(a), CircNormalizer.normalize(a.replace("val=\"1\"", "val=\"2\"")));
        assertNotEquals(CircNormalizer.normalize(a), CircNormalizer.normalize(a.replace("(5,5)", "(5,6)")));
    }
}
