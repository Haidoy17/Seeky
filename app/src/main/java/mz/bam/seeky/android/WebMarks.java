package mz.bam.seeky.android;

import java.util.List;
import java.util.Locale;
import org.json.*;

/**
 * Marks in the original EPUB and Word views. Offsets count the characters of the page's text nodes
 * (scripts and styles excluded), so the same words are found again whenever the page is reloaded,
 * re-flowed or linked to Bible passages.
 */
final class WebMarks {
    private static final String WALKER="function seekyWalker(root){return document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentNode;return p&&p.closest&&p.closest('script,style,head,title,noscript')?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_ACCEPT;}},false);}"
        +"function seekyRoot(){return document.getElementById('document')||document.body;}";
    /** Returns {"s":start,"e":end,"t":text} for the current selection, or "" when nothing is selected. */
    static final String SELECT="(function(){"+WALKER
        +"var sel=window.getSelection();if(!sel||sel.rangeCount==0||sel.isCollapsed)return '';var r=sel.getRangeAt(0),root=seekyRoot();if(!root)return '';"
        +"function at(c,o){var b=document.createRange();b.setStart(c,o);b.collapse(true);var w=seekyWalker(root),n,pos=0;while(n=w.nextNode()){if(n===c)return pos+o;if(b.comparePoint(n,0)>=0)return pos;pos+=n.nodeValue.length;}return pos;}"
        +"var s=at(r.startContainer,r.startOffset),e=at(r.endContainer,r.endOffset);if(e<=s)return '';"
        +"return JSON.stringify({s:s,e:e,t:sel.toString()});})()";
    static final String APPLY="(function(list){"+WALKER
        +"var root=seekyRoot();if(!root)return 0;var old=root.querySelectorAll('span.seeky-mark');"
        +"for(var i=0;i<old.length;i++){var sp=old[i],p=sp.parentNode;if(!p)continue;while(sp.firstChild)p.insertBefore(sp.firstChild,sp);p.removeChild(sp);}"
        +"var count=0;for(var k=0;k<list.length;k++){var m=list[k],w=seekyWalker(root),n,pos=0,targets=[];"
        +"while(n=w.nextNode()){var len=n.nodeValue.length,a=pos,b=pos+len;if(b>m[1]&&a<m[2])targets.push([n,Math.max(m[1],a)-a,Math.min(m[2],b)-a]);pos=b;if(pos>=m[2])break;}"
        +"for(var j=0;j<targets.length;j++){var t=targets[j];if(!/\\S/.test(t[0].nodeValue.substring(t[1],t[2])))continue;"
        +"try{var range=document.createRange();range.setStart(t[0],t[1]);range.setEnd(t[0],t[2]);var span=document.createElement('span');span.className='seeky-mark';"
        +"span.setAttribute('data-seeky-id',m[0]);span.setAttribute('data-seeky-start',m[1]);span.setAttribute('style',m[3]);"
        +"(function(id){span.onclick=function(ev){ev.preventDefault();ev.stopPropagation();location.href='seekymark://mark?id='+id;};})(m[0]);"
        +"range.surroundContents(span);count++;}catch(x){}}}return count;})(__DATA__)";

    /** CSS of one mark: highlighter colours keep dark letters in light and dark themes. */
    static String style(String kind){
        String c=String.format(Locale.ROOT,"#%06x",Highlights.color(kind)&0xffffff);
        String st=Highlights.style(kind);
        if(st.equals("underline"))return "text-decoration:underline;text-decoration-color:"+c+";text-decoration-thickness:2px;text-underline-offset:3px;cursor:pointer";
        if(st.equals("squiggle"))return "text-decoration:underline wavy;text-decoration-color:"+c+";text-decoration-thickness:1.5px;text-underline-offset:3px;cursor:pointer";
        if(st.equals("strike"))return "text-decoration:line-through;text-decoration-color:"+c+";text-decoration-thickness:2px;cursor:pointer";
        return "background-color:"+c+";color:#272237;border-radius:3px;box-decoration-break:clone;-webkit-box-decoration-break:clone;cursor:pointer"+(kind.equals("note")?";border-bottom:2px dotted #2f80ed":"");
    }
    static String apply(List<Highlights.Entry> marks){
        JSONArray list=new JSONArray();
        for(Highlights.Entry e:marks)list.put(new JSONArray().put(e.id).put(e.start).put(e.end).put(style(e.kind)));
        return APPLY.replace("__DATA__",list.toString());
    }
    static String scroll(int start){return "(function(){var el=document.querySelector('span.seeky-mark[data-seeky-start=\""+start+"\"]');if(el){el.scrollIntoView({block:'center'});return 1;}return 0;})()";}
    /** evaluateJavascript hands back a JSON value; a returned string arrives quoted. */
    static String unquote(String value){
        if(value==null||value.equals("null"))return "";
        try{Object v=new JSONTokener(value).nextValue();return v instanceof String?(String)v:"";}catch(JSONException e){return "";}
    }
    private WebMarks(){}
}
