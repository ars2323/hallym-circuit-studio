/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * .circ 저장 결과를 비교하기 위한 정규화(docs/DECISIONS.md D-006).
 *
 * <p>원조 2.7.1도 같은 파일을 늘 같은 바이트로 저장하지는 않는다. 다음 두 가지만 지우고 나머지는
 * 바이트 그대로 비교한다.
 * <ol>
 *   <li>줄 앞 공백과 빈 줄: JRE 버전마다 XML 직렬화기의 들여쓰기가 다르다(JDK 8과 9+).</li>
 *   <li>한 {@code <circuit>} 안의 {@code <wire>}·{@code <comp>} 순서: 원조가 HashSet 순서로 쓴다.
 *       부품은 identity hash 순서라 세션마다 달라질 수 있다.</li>
 * </ol>
 */
final class CircNormalizer {
    private CircNormalizer() {
    }

    static String normalize(String xml) {
        List<String> out = new ArrayList<>();
        List<String> block = null; // 현재 모으는 wire/comp 묶음
        List<String> items = null; // 한 circuit 안의 wire/comp 묶음들
        for (String raw : xml.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (items == null) {
                out.add(line);
                if (line.startsWith("<circuit ")) {
                    items = new ArrayList<>();
                }
                continue;
            }
            if (block != null) {
                block.add(line);
                if (line.equals("</comp>")) {
                    items.add(String.join("\n", block));
                    block = null;
                }
            } else if (line.startsWith("<wire ")) {
                items.add(line);
            } else if (line.startsWith("<comp ")) {
                if (line.endsWith("/>")) {
                    items.add(line);
                } else {
                    block = new ArrayList<>(List.of(line));
                }
            } else if (line.equals("</circuit>")) {
                Collections.sort(items);
                out.addAll(items);
                out.add(line);
                items = null;
            } else {
                out.add(line); // circuit 속성(<a …/>)은 원래 순서대로 둔다
            }
        }
        return String.join("\n", out) + "\n";
    }
}
