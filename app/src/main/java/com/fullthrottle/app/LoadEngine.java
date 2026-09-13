package com.fullthrottle.app;

import android.opengl.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class LoadEngine {
    private volatile boolean running;
    private final List<Thread> workers = new ArrayList<>();
    private volatile double sink;
    final int cores = Runtime.getRuntime().availableProcessors();
    void start(Consumer<String> gpuStatus) {
        running = true;
        for (int i = 0; i < cores; i++) {
            final int seed = i;
            launch("load-cpu-" + i, () -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                double value = seed + 1.01;
                while (running && !Thread.currentThread().isInterrupted()) {
                    for (int n = 0; n < 4096; n++) value = Math.sin(value) * Math.cos(value + 0.1) + Math.sqrt(value * value + 1.1);
                    sink = value;
                }
            });
        }
        launch("load-gpu", () -> render(gpuStatus));
    }
    private void launch(String name, Runnable task) { Thread t = new Thread(task, name); workers.add(t); t.start(); }
    void stop() { running = false; for (Thread t : workers) t.interrupt(); workers.clear(); }
    private int shader(int kind, String source) {
        int shader = GLES20.glCreateShader(kind);
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) { GLES20.glDeleteShader(shader); throw new IllegalStateException("shader"); }
        return shader;
    }
    private void render(Consumer<String> status) {
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        int program = 0, vertex = 0, fragment = 0;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (!EGL14.eglInitialize(display, new int[2], 0, new int[2], 0)) throw new IllegalStateException("EGL init");
            EGLConfig[] configs = new EGLConfig[1]; int[] count = new int[1];
            int[] attrs = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE};
            if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0) throw new IllegalStateException("EGL config");
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{EGL14.EGL_WIDTH,1024,EGL14.EGL_HEIGHT,1024,EGL14.EGL_NONE},0);
            if (!EGL14.eglMakeCurrent(display,surface,surface,context)) throw new IllegalStateException("EGL context");
            vertex = shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; varying vec2 uv; void main(){ uv=p; gl_Position=vec4(p,0.,1.); }");
            fragment = shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec2 uv; uniform float phase; void main(){ vec3 v=vec3(uv,phase); for(int i=0;i<96;i++){ v=sin(v.yzx*1.73+v.zxy*0.63+float(i)*0.013); } gl_FragColor=vec4(v*0.5+0.5,1.); }");
            program = GLES20.glCreateProgram(); GLES20.glAttachShader(program,vertex); GLES20.glAttachShader(program,fragment); GLES20.glLinkProgram(program);
            int[] linked = new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
            if (linked[0] == 0) throw new IllegalStateException("link");
            GLES20.glUseProgram(program);
            FloatBuffer points = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
            points.put(new float[]{-1,-1,3,-1,-1,3}).position(0);
            int loc=GLES20.glGetAttribLocation(program,"p"), phase=GLES20.glGetUniformLocation(program,"phase");
            GLES20.glEnableVertexAttribArray(loc); GLES20.glVertexAttribPointer(loc,2,GLES20.GL_FLOAT,false,0,points);
            GLES20.glViewport(0,0,1024,1024);
            status.accept("GPU 持续运算");
            int frame=0;
            while (running && !Thread.currentThread().isInterrupted()) {
                GLES20.glUniform1f(phase,(frame++ % 1000)/1000f);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,3);
                GLES20.glFinish();
                if (GLES20.glGetError()!=GLES20.GL_NO_ERROR) throw new IllegalStateException("GPU draw");
            }
        } catch (RuntimeException e) { if (running) status.accept("GPU 不可用 · CPU 继续运行"); }
        finally {
            if (program!=0) GLES20.glDeleteProgram(program);
            if (vertex!=0) GLES20.glDeleteShader(vertex);
            if (fragment!=0) GLES20.glDeleteShader(fragment);
            if (display!=EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(surface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,surface);
                if(context!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context);
                EGL14.eglTerminate(display); EGL14.eglReleaseThread();
            }
        }
    }
}