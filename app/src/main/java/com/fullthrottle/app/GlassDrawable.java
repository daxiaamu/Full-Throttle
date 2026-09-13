package com.fullthrottle.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Cached optical material. The same scene is sampled by the backdrop and lens surfaces. */
final class GlassDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final boolean night, backdrop;
    private final float radius;
    private java.lang.ref.WeakReference<View> explicitOwner;
    void setOwner(View view) { explicitOwner=new java.lang.ref.WeakReference<>(view); }
    private Bitmap capturedScene;
    private int capturedX,capturedY,capturedWidth,capturedHeight;
    void captureBehind(View root) {
        int[] pos=new int[2]; root.getLocationOnScreen(pos);
        capturedX=pos[0]; capturedY=pos[1]; capturedWidth=root.getWidth(); capturedHeight=root.getHeight();
        if(capturedWidth<=0 || capturedHeight<=0) return;
        int w=Math.max(1,capturedWidth/4),h=Math.max(1,capturedHeight/4);
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image); canvas.scale(w/(float)capturedWidth,h/(float)capturedHeight); root.draw(canvas);
        int[] pixels=new int[w*h],temp=new int[w*h]; image.getPixels(pixels,0,w,0,0,w,h);
        // Two separable box passes soften actual text and surfaces below the floating menu.
        for(int pass=0;pass<2;pass++) {
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int red=0,green=0,blue=0;
                for(int k=-4;k<=4;k++) { int c=pixels[y*w+Math.max(0,Math.min(w-1,x+k))]; red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c); }
                temp[y*w+x]=Color.rgb(red/9,green/9,blue/9);
            }
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int red=0,green=0,blue=0;
                for(int k=-4;k<=4;k++) { int c=temp[Math.max(0,Math.min(h-1,y+k))*w+x]; red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c); }
                pixels[y*w+x]=Color.rgb(red/9,green/9,blue/9);
            }
        }
        image.setPixels(pixels,0,w,0,0,w,h); capturedScene=image; cacheKey=""; material=null;
    }
    private Bitmap material;
    private static int[] scenePixels;
    private static int sceneWidth,sceneHeight;
    private static boolean sceneNight;
    private static float sceneViewportW,sceneViewportH;
    private void prepareScene(float w,float h) {
        if(scenePixels!=null && sceneViewportW==w && sceneViewportH==h && sceneNight==night) return;
        sceneViewportW=w; sceneViewportH=h; sceneNight=night;
        sceneWidth=Math.max(1,(int)w/4); sceneHeight=Math.max(1,(int)h/4);
        scenePixels=new int[sceneWidth*sceneHeight];
        for(int y=0;y<sceneHeight;y++) for(int x=0;x<sceneWidth;x++)
            scenePixels[y*sceneWidth+x]=scene(x*w/sceneWidth,y*h/sceneHeight,w,h);
    }
    private int sampleScene(float x,float y) {
        if(capturedScene!=null) {
            int px=Math.max(0,Math.min(capturedScene.getWidth()-1,(int)((x-capturedX)*capturedScene.getWidth()/capturedWidth)));
            int py=Math.max(0,Math.min(capturedScene.getHeight()-1,(int)((y-capturedY)*capturedScene.getHeight()/capturedHeight)));
            return capturedScene.getPixel(px,py);
        }
        int ix=Math.max(0,Math.min(sceneWidth-1,(int)(x*sceneWidth/sceneViewportW)));
        int iy=Math.max(0,Math.min(sceneHeight-1,(int)(y*sceneHeight/sceneViewportH)));
        return scenePixels[iy*sceneWidth+ix];
    }
    private String cacheKey="";
    GlassDrawable(boolean night,boolean backdrop,float radius) {
        this.night=night; this.backdrop=backdrop; this.radius=radius;
    }
    static int highlight(boolean night) { return night?0xAAD0EAFF:0xFFFFFFFF; }
    static int surface(boolean night) { return night?0x303E6380:0x30FFFFFF; }
    private static float clamp(float n) { return Math.max(0,Math.min(1,n)); }
    private static int mix(int a,int b,float t) {
        t=clamp(t);
        return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),
            (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),
            (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }
    // Continuous soft ribbons give the transparent lenses real contours to refract.
    private int scene(float x,float y,float w,float h) {
        float u=x/w,v=y/h;
        float ridge=(float)Math.sin(v*5.5f+u*2.1f);
        float blue=clamp((float)Math.exp(-Math.pow((u-.65f-.28f*ridge)/.28f,2)));
        float aqua=clamp((float)Math.exp(-Math.pow((u-.05f+.28f*ridge)/.32f,2)));
        float ribbon=clamp((float)Math.exp(-Math.pow((u-.54f-.25f*ridge)/.035f,2)))*.55f;
        int base=night?0xFF101D30:0xFFE4ECF6;
        int c=mix(base,night?0xFF225D99:0xFF7BABE4,blue*.85f);
        c=mix(c,night?0xFF17605D:0xFF97D7CC,aqua*.68f);
        return mix(base,c,night?.45f:.32f);
    }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds(); if(b.isEmpty()) return;
        View owner=explicitOwner!=null?explicitOwner.get():getCallback() instanceof View?(View)getCallback():null;
        int[] location=new int[2];
        if(owner!=null) owner.getLocationOnScreen(location);
        float viewportW=owner==null?b.width():owner.getResources().getDisplayMetrics().widthPixels;
        float viewportH=owner==null?b.height():owner.getResources().getDisplayMetrics().heightPixels;
        String key=b.toString()+":"+location[0]+":"+location[1]+":"+viewportW+":"+viewportH;
        if(material==null || !cacheKey.equals(key)) {
            cacheKey=key; prepareScene(viewportW,viewportH);
            // Half resolution is sufficient for the blurred background, sharp edges are vector drawn.
            int w=Math.max(1,b.width()/2),h=Math.max(1,b.height()/2);
            int[] pixels=new int[w*h];
            float r=Math.min(radius,Math.min(b.width(),b.height())/2f);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                float px=(x+.5f)*b.width()/w,py=(y+.5f)*b.height()/h;
                float sx=px,sy=py;
                float edge=0,normalX=0,normalY=0,depth=100;
                if(!backdrop) {
                    float qx=px-Math.max(r,Math.min(b.width()-r,px));
                    float qy=py-Math.max(r,Math.min(b.height()-r,py));
                    float len=(float)Math.sqrt(qx*qx+qy*qy);
                    float distance;
                    if(len>.001f) { distance=r-len; normalX=qx/len; normalY=qy/len; }
                    else {
                        distance=Math.min(Math.min(px,b.width()-px),Math.min(py,b.height()-py));
                        if(distance==px) normalX=-1;
                        else if(distance==b.width()-px) normalX=1;
                        else if(distance==py) normalY=-1; else normalY=1;
                    }
                    depth=distance;
                    float band=Math.max(10,Math.min(r*.42f,28));
                    edge=clamp(1-distance/band);
                    // Magnified interior, nonlinear inward displacement around the curved rim.
                    float displacement=(float)Math.sin(edge*Math.PI*.92f)*band*.65f;
                    sx=b.width()/2f+(px-b.width()/2f)*.94f-normalX*displacement;
                    sy=b.height()/2f+(py-b.height()/2f)*.94f-normalY*displacement;
                }
                int c=sampleScene(location[0]+b.left+sx,location[1]+b.top+sy);
                if(!backdrop) {
                    // Almost clear interior; optical thickness is concentrated at the rim.
                    c=mix(c,night?0xFF16263A:Color.WHITE,capturedScene!=null?(night?.38f:.68f):(night?.12f:.18f));
                    float dispersion=edge*edge*.6f;
                    int redSample=sampleScene(location[0]+b.left+sx+normalX*dispersion,
                        location[1]+b.top+sy+normalY*dispersion);
                    int blueSample=sampleScene(location[0]+b.left+sx-normalX*dispersion,
                        location[1]+b.top+sy-normalY*dispersion);
                    if(capturedScene==null) c=mix(c,Color.rgb(Color.red(redSample),Color.green(c),Color.blue(blueSample)),.25f);
                    float facing=clamp((-normalX-normalY)*.7f);
                    float specular=(float)Math.exp(-Math.pow((depth-2.8f)/2.1f,2));
                    float innerShadow=(float)Math.exp(-Math.pow((depth-8f)/3.2f,2));
                    float caustic=(float)Math.exp(-Math.pow((depth-15f)/4.2f,2));
                    c=mix(c,night?0xFF020C1B:0xFF305577,innerShadow*.045f);
                    c=mix(c,Color.WHITE,specular*facing*.16f);
                    c=mix(c,night?0xFFBAE8FF:Color.WHITE,caustic*(1-facing)*.04f);
                }
                pixels[y*w+x]=c;
            }
            Bitmap next=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
            material=next;
        }
        int saved=canvas.save();
        if(!backdrop) {
            Path clip=new Path(); clip.addRoundRect(new RectF(b),radius,radius,Path.Direction.CW); canvas.clipPath(clip);
        }
        canvas.drawBitmap(material,null,b,paint); canvas.restoreToCount(saved);
        if(!backdrop) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.4f);
            paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,
                new int[]{0xBFFFFFFF,0x08FFFFFF,0x40FFFFFF},null,Shader.TileMode.CLAMP));
            RectF rim=new RectF(b); rim.inset(1,1);
            canvas.drawRoundRect(rim,radius,radius,paint);
            paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        }
    }
    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(),radius); }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}