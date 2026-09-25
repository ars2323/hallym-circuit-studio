/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.util.StringGetter;

/** {@link WordImage}를 .circ의 {@code <a name="contents">…</a>}로 저장하는 속성. 속성 창에서는 요약만 보인다. */
final class WordImageAttribute extends Attribute<WordImage> {
    WordImageAttribute(String name, StringGetter display) {
        super(name, display);
    }

    @Override
    public String toDisplayString(WordImage value) {
        if (value == null || value.isEmpty()) {
            return Text.name("(empty)").get();
        }
        return Text.count(value.size(), "word") + " from 0x" + WordImage.hex(value.firstAddress());
    }

    @Override
    public String toStandardString(WordImage value) {
        return value == null ? "" : value.format();
    }

    @Override
    public WordImage parse(String value) {
        return WordImage.parse(value);
    }
}
