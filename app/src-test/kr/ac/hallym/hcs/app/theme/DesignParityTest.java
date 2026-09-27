/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Z-12b: docs/design-parity.md의 값 표(Hallym MIPS v2.3.0 app.css에서 뽑은 값)와 {@link Tokens}가 같은지 한 줄씩 본다.
 * 판정이 "같음"이면 값이 같아야 하고, "다름:"이면 이유가 있어야 한다.
 */
class DesignParityTest {
    static final Pattern ROW = Pattern.compile("^\\| `([^`]+)` \\| `([^`]+)` \\| ([^|]+) \\| `([A-Z0-9_]+)` \\| (.+) \\|$");

    static File doc() {
        return new File(new File(System.getProperty("hcs.testsDir")).getParentFile(), "docs/design-parity.md");
    }

    @Test
    void tokensMatchTheHallymMipsValues() throws Exception {
        List<String> lines = Files.readAllLines(doc().toPath(), StandardCharsets.UTF_8);
        int rows = 0;
        int same = 0;
        List<String> bad = new ArrayList<>();
        for (String line : lines) {
            Matcher m = ROW.matcher(line.trim());
            if (!m.matches()) {
                continue;
            }
            rows++;
            String name = m.group(1);
            String value = m.group(2);
            String token = m.group(4);
            String verdict = m.group(5).trim();
            Field f;
            try {
                f = Tokens.class.getField(token);
            } catch (NoSuchFieldException e) {
                bad.add(name + ": no Tokens." + token);
                continue;
            }
            Object ours = f.get(null);
            if (verdict.startsWith("다름")) {
                assertTrue(verdict.length() > "다름:".length() + 2, name + ": a different value needs a reason");
                continue;
            }
            if (!verdict.startsWith("같음")) {
                bad.add(name + ": verdict must be 같음 or 다름: " + verdict);
                continue;
            }
            boolean ok;
            if (value.startsWith("#")) {
                ok = ours instanceof Color && (((Color) ours).getRGB() & 0xFFFFFF) == Integer.parseInt(value.substring(1), 16);
            } else if (value.endsWith("px") || value.endsWith("%")) {
                int n = Integer.parseInt(value.replace("px", "").replace("%", ""));
                ok = ours instanceof Integer && (Integer) ours == n;
            } else {
                ok = value.equals(ours);
            }
            if (ok) {
                same++;
            } else {
                bad.add(name + ": Hallym MIPS " + value + ", Tokens." + token + " = " + describe(ours));
            }
        }
        System.out.println("design parity: " + same + " / " + rows + " rows equal");
        if (!bad.isEmpty()) {
            fail(String.join("\n", bad));
        }
        assertTrue(rows >= 90, "the table has its rows (" + rows + ")");
        assertEquals(rows, same + (int) lines.stream().filter(l -> ROW.matcher(l.trim()).matches() && l.contains("| 다름")).count());
    }

    static String describe(Object o) {
        return o instanceof Color ? Tokens.hex((Color) o) : String.valueOf(o);
    }
}
