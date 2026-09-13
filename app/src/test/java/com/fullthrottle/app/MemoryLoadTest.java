package com.fullthrottle.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class MemoryLoadTest {
    private static final long M=1024L*1024;
    @Test public void preservesHeapAndSystemHeadroom() {
        assertEquals(320*M,MemoryLoad.budget(500*M,80*M,4096*M));
        assertEquals(44*M,MemoryLoad.budget(500*M,80*M,300*M));
        assertEquals(0,MemoryLoad.budget(128*M,80*M,4096*M));
        assertEquals(0,MemoryLoad.budget(500*M,80*M,200*M));
    }
}