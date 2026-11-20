// Optional browser check of the original-view marking scripts (WebMarks.SELECT and WebMarks.APPLY).
// Usage: java -cp <classes> mz.bam.seeky.android.DumpJs <dir> && node tests/web/web_marks_test.js <dir>
const {chromium}=require('playwright');const fs=require('fs');const dir=process.argv[2];
const select=fs.readFileSync(dir+'/select.js','utf8'),apply=fs.readFileSync(dir+'/apply.js','utf8');
const html=`<html><head><title>T</title><style>body{font:16px serif}</style></head><body><div id="document"><style>.docx p{margin:0}</style><section class="docx"><p>Todo mundo tem <b>medo de alguma</b> coisa. Pode ser de cachorros.</p>
<p>Leia <a class="seeky-bible-reference" href="seekybible://x">Isaías 3.1-7, 12</a> hoje e <i>fique em paz</i>.</p></section></div><div style="height:3000px"></div></body></html>`;
(async()=>{const b=await chromium.launch();const p=await b.newPage();await p.setContent(html);
 const check=(c,m)=>{if(!c){console.log('FAIL',m);process.exitCode=1;}else console.log('ok',m);};
 // Select across the bold run: "medo de alguma coisa. Pode"
 await p.evaluate(()=>{const b=document.querySelector('b');const t2=b.nextSibling;const r=document.createRange();r.setStart(b.firstChild,0);r.setEnd(t2,12);const s=getSelection();s.removeAllRanges();s.addRange(r);});
 const raw=await p.evaluate(select);const sel=JSON.parse(raw);check(sel.t==='medo de alguma coisa. Pode','selected text '+JSON.stringify(sel));
 const style='background-color:#c5ecb0;color:#272237';
 const run=(list)=>p.evaluate(apply.replace('__DATA__',JSON.stringify(list)));
 let n=await run([[7,sel.s,sel.e,style]]);check(n===2,'wrapped across the bold run: '+n);
 let marked=await p.evaluate(()=>[...document.querySelectorAll('span.seeky-mark')].map(s=>s.textContent).join(''));check(marked==='medo de alguma coisa. Pode','marked words '+marked);
 // Element-container boundary (selection that starts at an element boundary) and a second mark inside a link.
 await p.evaluate(()=>{const a=document.querySelector('a');const r=document.createRange();r.setStart(a,0);r.setEnd(a,1);const s=getSelection();s.removeAllRanges();s.addRange(r);});
 const sel2=JSON.parse(await p.evaluate(select));check(sel2.t==='Isaías 3.1-7, 12','link text selection '+JSON.stringify(sel2));
 n=await run([[7,sel.s,sel.e,style],[8,sel2.s,sel2.e,'text-decoration:underline']]);check(n===3,'re-applied both marks: '+n);
 marked=await p.evaluate(()=>[...document.querySelectorAll('span.seeky-mark[data-seeky-id="8"]')].map(s=>s.textContent).join(''));check(marked==='Isaías 3.1-7, 12','underline inside link '+marked);
 // Fresh page (reload): offsets find the same words.
 await p.setContent(html);n=await run([[7,sel.s,sel.e,style],[8,sel2.s,sel2.e,'text-decoration:underline']]);
 marked=await p.evaluate(()=>[...document.querySelectorAll('span.seeky-mark[data-seeky-id="7"]')].map(s=>s.textContent).join(''));check(marked==='medo de alguma coisa. Pode','stable after reload '+marked);
 const scrolled=await p.evaluate(`(function(){var el=document.querySelector('span.seeky-mark[data-seeky-start="${sel.s}"]');if(el){el.scrollIntoView({block:'center'});return 1;}return 0;})()`);check(scrolled===1,'scroll to mark');
 // Deleting all marks unwraps the spans and keeps the text.
 n=await run([]);const left=await p.evaluate(()=>document.querySelectorAll('span.seeky-mark').length);const text=await p.evaluate(()=>document.querySelector('section').textContent.replace(/\s+/g,' ').trim());
 check(left===0&&text.startsWith('Todo mundo tem medo de alguma coisa. Pode ser de cachorros. Leia Isaías 3.1-7, 12 hoje'),'unwrapped: '+text);
 // Click on a mark navigates to seekymark://
 await run([[9,sel.s,sel.e,style]]);let target='';p.on('request',r=>{if(r.url().startsWith('seekymark'))target=r.url();});
 const href=await p.evaluate(()=>{let url='';const sp=document.querySelector('span.seeky-mark');const old=window.location;try{sp.onclick({preventDefault(){},stopPropagation(){}});}catch(e){}return sp.onclick.toString().includes('seekymark://mark?id=')?'ok':'';});check(href==='ok','tap opens the mark');
 await b.close();})();
