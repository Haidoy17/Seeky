package android.graphics;
/** Value shim used only for unclipped PDF extraction fixture, never Android rendering. */
public class Path {public enum FillType {WINDING,EVEN_ODD,INVERSE_WINDING,INVERSE_EVEN_ODD}public enum Direction{CW,CCW}public Path(){}public Path(Path p){}public void addRect(float l,float t,float r,float b,Direction d){}public void moveTo(float x,float y){}public void lineTo(float x,float y){}public void close(){}public void setFillType(FillType f){}public void reset(){}}
