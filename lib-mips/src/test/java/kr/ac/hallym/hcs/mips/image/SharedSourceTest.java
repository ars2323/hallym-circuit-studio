/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

/** D-125: 공용 소스(src/shared/java)가 트랙 A jar에 Java 8 바이트코드로 들어간다(원조 2.7.1이 어떤 JRE에서 돌지 모른다). */
class SharedSourceTest {
    @Test
    void sharedClassesAreInTheTrackAJarAsJava8() throws Exception {
        assertEquals("HALLYM-EXEC 1", HmxFormat.header());
        File jar = new File(System.getProperty("hcs.mipsJar", "build/libs/hcs-mips.jar"));
        try (ZipFile z = new ZipFile(jar)) {
            ZipEntry e = z.getEntry("kr/ac/hallym/hcs/mips/image/HmxFormat.class");
            assertEquals(true, e != null, "HmxFormat is in " + jar);
            try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                assertEquals(0xCAFEBABE, d.readInt());
                d.readUnsignedShort();
                assertEquals(52, d.readUnsignedShort(), "class file major version 52 = Java 8");
            }
        }
    }
}
