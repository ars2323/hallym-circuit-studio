/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

/** 학생 PC의 2.7.1이 Java 8에서 돌 수도 있으므로 모든 클래스가 Java 8(52) 바이트코드여야 한다. */
class BytecodeVersionTest {
    static final int JAVA_8 = 52;

    @Test
    void allClassesTargetJava8() throws Exception {
        for (String prop : new String[] {"hcs.mipsJar", "hcs.smokeJar"}) {
            int classes = 0;
            try (JarFile jar = new JarFile(System.getProperty(prop))) {
                assertTrue(jar.getManifest().getMainAttributes().getValue("Library-Class") != null,
                        prop + ": Library-Class 없음");
                for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
                    JarEntry entry = e.nextElement();
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    try (InputStream in = jar.getInputStream(entry);
                            DataInputStream data = new DataInputStream(in)) {
                        assertEquals(0xCAFEBABE, data.readInt());
                        data.readUnsignedShort(); // minor
                        assertEquals(JAVA_8, data.readUnsignedShort(), entry.getName());
                    }
                    classes++;
                }
            }
            assertTrue(classes > 0, prop + ": 클래스 없음");
        }
    }
}
