/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.diag;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.diag.Diagnostic;

/**
 * v2 Messages 문구(D-143): 한 진단을 영어·한국어 두 벌로 쓴다. 인자는 v1 진단의 인자 그대로이고, 문구 틀은 엔진의
 * 묶음(kr/ac/hallym/hcs/engine/diag/messages*.properties)에 있다. 한국어 틀은 이름 바로 뒤에 조사를 붙이지 않는다(v2
 * 지시 7절). 안쪽 문장(원인, E 표기)은 {@link Diagnostic.Text}라 같은 언어로 다시 쓴다.
 */
public final class DiagText {
    static final String BUNDLE = "kr.ac.hallym.hcs.engine.diag.messages";
    public static final Locale KO = Locale.KOREAN;
    public static final Locale EN = Locale.ENGLISH;
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private DiagText() {
    }

    /** 한 언어의 틀. 없으면 null. */
    static String pattern(Locale locale, String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale.getLanguage().equals("ko") ? KO : Locale.ROOT, NO_FALLBACK)
                    .getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /** 틀 key에 args를 넣은 한 언어의 글(안쪽 {@link Diagnostic.Text}와 {@link Both}도 그 언어로). */
    public static String render(Locale locale, String key, Object... args) {
        String p = pattern(locale, key);
        if (p == null) {
            return kr.ac.hallym.hcs.app.Messages.get(locale, key, resolve(locale, args));
        }
        return args.length == 0 ? p : new MessageFormat(p, locale).format(resolve(locale, args));
    }

    private static Object[] resolve(Locale locale, Object[] args) {
        Object[] out = args.clone();
        for (int i = 0; i < out.length; i++) {
            if (out[i] instanceof Diagnostic.Text) {
                Diagnostic.Text t = (Diagnostic.Text) out[i];
                out[i] = render(locale, t.key, t.args().toArray());
            } else if (out[i] instanceof Both) {
                out[i] = ((Both) out[i]).get(locale);
            }
        }
        return out;
    }

    /** 이미 두 벌로 있는 글(MIPS 부품 몸체의 글자). */
    public static final class Both {
        final String en;
        final String ko;

        Both(String en, String ko) {
            this.en = en;
            this.ko = ko;
        }

        String get(Locale locale) {
            return locale.getLanguage().equals("ko") ? ko : en;
        }
    }

    /** 진단 한 줄(near: 짝 없는 터널의 가까운 이름, 없으면 null). 앞뒤 공백은 뺀다. */
    public static String message(Locale locale, Diagnostic d, String near) {
        List<Object> raw = d.rawArgs();
        Object[] args = raw.toArray();
        if (d.kind == Diagnostic.Kind.MIPS_STATUS && args.length > 2 && args[2] instanceof String) {
            args[2] = MipsText.both((String) args[2]);
        }
        String s = render(locale, d.key(), args).trim();
        if (d.kind == Diagnostic.Kind.TUNNEL_UNPAIRED && near != null) {
            s = s + " " + render(locale, "diag.near", near);
        }
        return s;
    }

    /** {ko, en}. */
    public static JsonObject both(Diagnostic d, String near) {
        JsonObject o = new JsonObject();
        o.addProperty("ko", message(KO, d, near));
        o.addProperty("en", message(EN, d, near));
        return o;
    }

    /** {ko, en} of one key. */
    public static JsonObject both(String key, Object... args) {
        JsonObject o = new JsonObject();
        o.addProperty("ko", render(KO, key, args).trim());
        o.addProperty("en", render(EN, key, args).trim());
        return o;
    }

    /**
     * lib-mips 부품 몸체의 글자(부품 몸체와 같은 사실, D-04)를 v2 문구 두 벌로. lib-mips는 원조 언어 설정에 따라 영어나
     * 한국어 한 벌만 만들므로, 알려진 글꼴은 틀로 다시 쓰고 모르는 글은 두 언어 모두 그대로 둔다.
     */
    static final class MipsText {
        private static final Object[][] FORMS = {
            {"mips.unaligned", Pattern.compile("Addr not word-aligned|Addr가 워드 정렬 안 됨")},
            {"mips.noRegion", Pattern.compile("(\\S+) is in no memory region|(\\S+)는 어느 메모리 영역에도 없음")},
            {"mips.stackLimit", Pattern.compile("Stack use exceeds its limit \\((\\S+)\\)|Stack 사용량이 한계\\((\\S+)\\)를 넘었습니다")},
            {"mips.v0Undefined", Pattern.compile("V0 undefined|V0가 정의되지 않음")},
            {"mips.a0Undefined", Pattern.compile("A0 undefined|A0가 정의되지 않음")},
            {"mips.syscall", Pattern.compile("syscall (-?\\d+) not supported|syscall (-?\\d+) 지원 안 함")},
            {"mips.stringNotInMemory", Pattern.compile("string address (\\S+) not in memory|문자열 주소 (\\S+)가 메모리에 없음")},
            {"mips.undefinedByte", Pattern.compile("undefined byte at (\\S+)|(\\S+)의 바이트가 정의되지 않음")},
            {"mips.stringTooLong", Pattern.compile("string longer than (\\d+)|문자열이 (\\d+)바이트보다 김")},
            {"mips.syscallFloating", Pattern.compile("Syscall floating|Syscall 떠 있음")},
        };

        private MipsText() {
        }

        static Both both(String text) {
            for (Object[] f : FORMS) {
                Matcher m = ((Pattern) f[1]).matcher(text);
                if (m.matches()) {
                    String arg = null;
                    for (int g = 1; g <= m.groupCount(); g++) {
                        if (m.group(g) != null) {
                            arg = m.group(g);
                        }
                    }
                    Object[] args = arg == null ? new Object[0] : new Object[] {arg};
                    String key = (String) f[0];
                    return new Both(render(EN, key, args), render(KO, key, args));
                }
            }
            return new Both(text, text);
        }
    }
}
