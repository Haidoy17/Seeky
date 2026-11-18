package mz.bam.seeky.android;

/** Horizontal turns lock before a long press; vertical scrolling and multi-touch stay independent. */
final class PageSwipe {
    final float lock,turn;final long hold;float x,y;long started;boolean active,blocked,captured;
    PageSwipe(float lock,float turn,long hold){this.lock=lock;this.turn=turn;this.hold=hold;}
    void begin(float x,float y,long time){this.x=x;this.y=y;started=time;active=true;blocked=captured=false;}
    boolean move(float x,float y,long time,int pointers,boolean selecting){
        if(!active||blocked)return false;
        if(pointers>1||selecting){blocked=true;return false;}
        float dx=Math.abs(x-this.x),dy=Math.abs(y-this.y);
        if(!captured){if(time-started>=hold||dy>lock&&dy>dx){blocked=true;return false;}if(dx>lock&&dx>dy*1.5f)captured=true;}
        return captured;
    }
    int end(float x,float y,long time,int pointers,boolean selecting){boolean locked=move(x,y,time,pointers,selecting);int direction=locked&&Math.abs(x-this.x)>=turn&&Math.abs(x-this.x)>Math.abs(y-this.y)*1.5f?(x<this.x?1:-1):0;reset();return direction;}
    void reset(){active=captured=blocked=false;}
}
