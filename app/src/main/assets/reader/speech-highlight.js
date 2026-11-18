(function(){
 'use strict';
 if(window.seekySpeech)return;
 var state=null,overlay=null;
 function compact(s){return s.replace(/[\s\u00a0]+/g,'');}
 function clear(){if(overlay)overlay.textContent='';if(state)state.range=null;}
 function selected(){var s=window.getSelection();return !!(s&&!s.isCollapsed);}
 function init(source){
  clear();var records=[],parts=[],total=0,walk=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT),node;
  while((node=walk.nextNode())){
   var parent=node.parentElement;if(!parent||parent.closest('script,style,noscript,template,[data-seeky-speech-overlay]'))continue;
   if(getComputedStyle(parent).display==='none'||getComputedStyle(parent).visibility==='hidden')continue;
   var t=compact(node.nodeValue||'');if(!t)continue;records.push({node:node,start:total,end:total+t.length});parts.push(t);total+=t.length;
  }
  var prefix=new Uint32Array(source.length+1),n=0;
  for(var i=0;i<source.length;i++){if(!/[\s\u00a0]/.test(source[i]))n++;prefix[i+1]=n;}
  state={source:compact(source),prefix:prefix,dom:parts.join(''),records:records,range:null};
  if(!overlay){overlay=document.createElement('div');overlay.setAttribute('data-seeky-speech-overlay','1');overlay.style.cssText='position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;margin:0!important;padding:0!important;border:0!important;pointer-events:none!important;z-index:2147483646!important;';(document.documentElement||document.body).appendChild(overlay);}
 }
 function point(offset,end){var list=state.records,lo=0,hi=list.length;while(lo<hi){var mid=(lo+hi)>>1;if(list[mid].end<=offset)lo=mid+1;else hi=mid;}if(lo===list.length){var last=list[list.length-1];return last?{node:last.node,offset:last.node.length}:null;}
  var r=list[lo],need=offset-r.start,raw=r.node.nodeValue||'',count=0;
  for(var i=0;i<raw.length;i++){if(/[\s\u00a0]/.test(raw[i]))continue;if(count===need)return{node:r.node,offset:i};count++;}
  return{node:r.node,offset:raw.length};
 }
 function paint(follow){
  if(!state||!overlay||!state.range)return;overlay.textContent='';var rects=state.range.getClientRects();
  for(var i=0;i<rects.length&&i<160;i++){var r=rects[i];if(r.width<1||r.height<1)continue;var mark=document.createElement('div');mark.style.cssText='position:absolute!important;pointer-events:none!important;max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;border:0!important;border-radius:3px!important;background:rgba(245,188,49,.38)!important;';mark.style.left=r.left+'px';mark.style.top=r.top+'px';mark.style.width=r.width+'px';mark.style.height=r.height+'px';overlay.appendChild(mark);}
  if(follow&&!selected()&&rects.length){var first=rects[0];if(first.top<48||first.bottom>innerHeight-70)window.scrollBy(0,first.top-70);}
 }
 function show(from,to,follow){
  if(!state||!state.records.length)return false;from=Math.max(0,Math.min(from,state.prefix.length-1));to=Math.max(from+1,Math.min(to,state.prefix.length-1));var begin=state.prefix[from],end=state.prefix[Math.min(to,state.prefix.length-1)];if(end<=begin)return false;
  var before=Math.max(0,begin-32),after=Math.min(state.source.length,end+32),context=state.source.slice(before,after),at=state.dom.slice(before,after)===context?before:state.dom.indexOf(context,Math.max(0,begin-1200));if(at<0)at=state.dom.indexOf(context);var actual;
  if(at>=0){actual=at+begin-before;}else{var phrase=state.source.slice(begin,end);if(!phrase)return false;var expected=Math.floor(begin*state.dom.length/Math.max(1,state.source.length));actual=state.dom.indexOf(phrase,Math.max(0,expected-1200));if(actual<0)actual=state.dom.indexOf(phrase);if(actual<0){clear();return false;}}
  var a=point(actual,false),b=point(actual+end-begin,true);if(!a||!b)return false;try{var range=document.createRange();range.setStart(a.node,a.offset);range.setEnd(b.node,b.offset);state.range=range;paint(!!follow);return true;}catch(e){clear();return false;}
 }
 window.seekySpeech={init:init,show:show,clear:clear};
 window.addEventListener('scroll',function(){paint(false);},{passive:true});window.addEventListener('resize',function(){paint(false);});
 var css=document.createElement('style');css.textContent='html body,html body *{-webkit-user-select:text!important;user-select:text!important;}[data-seeky-speech-overlay],[data-seeky-speech-overlay] *{-webkit-user-select:none!important;user-select:none!important;}';document.head.appendChild(css);
})()
