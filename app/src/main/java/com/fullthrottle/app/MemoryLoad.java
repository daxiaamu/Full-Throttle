package com.fullthrottle.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/** Occupies the app's available heap and repeatedly touches physical pages. */
final class MemoryLoad implements Runnable {
    private static final int CHUNK=2*1024*1024;
    private final ActivityManager manager;
    private volatile boolean running=true,trim;
    volatile long allocatedBytes;
    MemoryLoad(Context context) { manager=context.getApplicationContext().getSystemService(ActivityManager.class); }
    void stop() { running=false; }
    void trim() { trim=true; }
    static long budget(long maxHeap,long usedHeap,long availableRam) {
        long reserve=Math.max(64L*1024*1024,maxHeap/5);
        long systemReserve=256L*1024*1024;
        return Math.max(0,Math.min(maxHeap-usedHeap-reserve,availableRam-systemReserve));
    }
    @Override public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        List<byte[]> blocks=new ArrayList<>();
        Runtime runtime=Runtime.getRuntime();
        boolean allocationFinished=false;
        try {
            while(running && !Thread.currentThread().isInterrupted()) {
                if(trim) {
                    trim=false; allocationFinished=true;
                    while(blocks.size()>1) blocks.remove(blocks.size()-1);
                    allocatedBytes=(long)blocks.size()*CHUNK;
                }
                if(!allocationFinished) {
                    ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
                    manager.getMemoryInfo(info);
                    long used=runtime.totalMemory()-runtime.freeMemory();
                    if(!info.lowMemory && budget(runtime.maxMemory(),used,info.availMem)>=CHUNK) {
                        try {
                            byte[] block=new byte[CHUNK];
                            for(int i=0;i<block.length;i+=64) block[i]=(byte)i;
                            blocks.add(block); allocatedBytes=(long)blocks.size()*CHUNK;
                        } catch(OutOfMemoryError exhausted) {
                            allocationFinished=true;
                            // Free a few chunks immediately to leave room for the stop controls.
                            for(int i=0;i<4 && !blocks.isEmpty();i++) blocks.remove(blocks.size()-1);
                            allocatedBytes=(long)blocks.size()*CHUNK;
                        }
                        Thread.sleep(8);
                        continue;
                    }
                    allocationFinished=true;
                }
                if(blocks.isEmpty()) { Thread.sleep(100); continue; }
                for(byte[] block:blocks) {
                    if(!running || Thread.currentThread().isInterrupted()) break;
                    for(int i=0;i<block.length;i+=64) block[i]++;
                }
            }
        } catch(InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } finally {
            blocks.clear(); allocatedBytes=0;
            // Make the large temporary working set reclaimable promptly after stopping.
            System.gc();
        }
    }
}