/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.prefs;

import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * 메모리 전용 {@link PreferencesFactory}(D-134, 실습실 규칙 N-19). 엔진은 원조 Logisim의 디스크 설정
 * ({@code ~/.java/.userPrefs/com/cburch/logisim}, Windows 레지스트리)을 읽지 않고, 무엇도 남기지 않는다.
 * {@link #install()}은 {@link Preferences} 클래스가 처음 쓰이기 전에 불러야 한다(JDK가 공장을 한 번만 고른다).
 */
public final class MemoryPreferencesFactory implements PreferencesFactory {
    /** JDK가 공장 클래스를 고르는 시스템 속성. */
    public static final String PROPERTY = "java.util.prefs.PreferencesFactory";

    /**
     * 뿌리 노드. 따로 된 클래스에 두어 {@link #install()}이 {@link Preferences} 클래스를 초기화하지 않게 한다(노드를
     * 만들면 Preferences가 초기화되며 그 순간 공장을 고른다).
     */
    private static final class Roots {
        static final MemoryPreferences USER = new MemoryPreferences(null, "");
        static final MemoryPreferences SYSTEM = new MemoryPreferences(null, "");
    }

    /** JDK가 반사로 만든다. */
    public MemoryPreferencesFactory() {
    }

    @Override
    public Preferences userRoot() {
        return Roots.USER;
    }

    @Override
    public Preferences systemRoot() {
        return Roots.SYSTEM;
    }

    /** 시스템 속성을 이 공장으로 맞춘다. {@link Preferences}를 쓰기 전에 부른다. */
    public static void install() {
        System.setProperty(PROPERTY, MemoryPreferencesFactory.class.getName());
    }

    /** 실제로 메모리 전용 공장이 쓰이는가(이미 다른 공장으로 정해졌으면 false). */
    public static boolean isActive() {
        return Preferences.userRoot() == Roots.USER && Preferences.systemRoot() == Roots.SYSTEM;
    }
}
