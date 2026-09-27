package fr.taverne.mercenaires;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

final class MapView extends View {
    interface Listener { void selected(Marker marker); }
    static final class Marker {
        final long id; final boolean interest; final float x,y;
        Marker(long id,boolean interest,float x,float y){this.id=id;this.interest=interest;this.x=x;this.y=y;}
    }
    private final Bitmap image;
    private final Paint paint=new Paint(3);
    private final List<Marker> markers=new ArrayList<>();
    private final Listener listener;
    private final boolean placement;
    private float zoom=1f,offsetX=0,offsetY=0,downX,downY,lastX,lastY,initialDistance;
    private boolean dragged=false;
    private float chosenX=-1,chosenY=-1;
    private boolean focusPending=false;
    private float focusX,focusY;
    MapView(Context context,boolean placement,Listener listener){
        super(context);this.placement=placement;this.listener=listener;
        image=BitmapFactory.decodeResource(getResources(),R.drawable.map_country);
        setBackgroundColor(Color.rgb(20,35,31));
    }
    void setMarkers(List<Marker> items){markers.clear();markers.addAll(items);invalidate();}
    void setChosen(float x,float y){chosenX=x;chosenY=y;invalidate();}
    void focusOnTavern(){focusX=.465f;focusY=.36f;zoom=2.5f;focusPending=true;if(getWidth()>0&&getHeight()>0)applyFocus();}
    private void applyFocus(){offsetX=(.5f-focusX)*image.getWidth()*scale();offsetY=(.5f-focusY)*image.getHeight()*scale();clamp();focusPending=false;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(focusPending&&w>0&&h>0)applyFocus();}
    float chosenX(){return chosenX;} float chosenY(){return chosenY;}
    private float base(){return Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());}
    private float scale(){return base()*zoom;}
    private float left(){return getWidth()/2f+offsetX-image.getWidth()*scale()/2f;}
    private float top(){return getHeight()/2f+offsetY-image.getHeight()*scale()/2f;}
    private float screenX(float x){return left()+x*image.getWidth()*scale();}
    private float screenY(float y){return top()+y*image.getHeight()*scale();}
    private void clamp(){
        float w=image.getWidth()*scale(),h=image.getHeight()*scale();
        float limitX=Math.max(0,(w-getWidth())/2f),limitY=Math.max(0,(h-getHeight())/2f);
        offsetX=Math.max(-limitX,Math.min(limitX,offsetX));offsetY=Math.max(-limitY,Math.min(limitY,offsetY));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);clamp();
        c.save();c.translate(left(),top());c.scale(scale(),scale());paint.setColor(Color.WHITE);c.drawBitmap(image,0,0,paint);c.restore();
        for(Marker m:markers)drawMarker(c,m.x,m.y,m.interest?"?":"✦",m.interest?0xff315b58:0xffbd823d);
        if(placement&&chosenX>=0)drawMarker(c,chosenX,chosenY,"✓",0xffd39d45);
    }
    private void drawMarker(Canvas c,float x,float y,String label,int color){
        float sx=screenX(x),sy=screenY(y),radius=getResources().getDisplayMetrics().density*17;
        paint.setColor(0xfff7e9c4);c.drawCircle(sx,sy,radius+3,paint);
        paint.setColor(color);c.drawCircle(sx,sy,radius,paint);
        paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextSize(radius*1.25f);
        c.drawText(label,sx,sy+radius*.42f,paint);
    }
    private float distance(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.hypot(dx,dy);}
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=lastX=e.getX();downY=lastY=e.getY();dragged=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(e.getPointerCount()>=2){initialDistance=distance(e);dragged=true;}return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()>=2){float d=distance(e);if(initialDistance>0){zoom=Math.max(1f,Math.min(4f,zoom*d/initialDistance));initialDistance=d;invalidate();}return true;}
                if(Math.abs(e.getX()-downX)>12||Math.abs(e.getY()-downY)>12)dragged=true;
                if(dragged){offsetX+=e.getX()-lastX;offsetY+=e.getY()-lastY;clamp();invalidate();}
                lastX=e.getX();lastY=e.getY();return true;
            case MotionEvent.ACTION_UP:
                if(!dragged){
                    float x=e.getX(),y=e.getY();
                    if(placement){chosenX=Math.max(0,Math.min(1,(x-left())/(image.getWidth()*scale())));chosenY=Math.max(0,Math.min(1,(y-top())/(image.getHeight()*scale())));invalidate();}
                    else {Marker hit=null;float best=36*getResources().getDisplayMetrics().density;for(Marker m:markers){float d=(float)Math.hypot(x-screenX(m.x),y-screenY(m.y));if(d<best){hit=m;best=d;}}if(hit!=null&&listener!=null)listener.selected(hit);}
                }return true;
            case MotionEvent.ACTION_POINTER_UP: initialDistance=0;return true;
            default:return true;
        }
    }
}
