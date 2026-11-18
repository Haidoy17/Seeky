const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/reader/speech-highlight.js','utf8');let checks=0;
function fixture(parts){let range=null,scrolls=0;const nodes=parts.map(text=>({nodeValue:text,length:text.length,parentElement:{closest:()=>null}}));
 function element(){return{style:{},children:[],setAttribute(){},appendChild(c){this.children.push(c);},set textContent(t){this.children=[];},get textContent(){return'';}};}
 const body=element(),head=element();const document={body,head,createElement:element,createTreeWalker:()=>{let i=0;return{nextNode:()=>nodes[i++]||null};},createRange:()=>{const r={setStart(node,offset){this.start={node,offset};},setEnd(node,offset){this.end={node,offset};},getClientRects:()=>[{left:4,top:100,width:60,height:18,bottom:118}]};range=r;return r;}};
 const window={getSelection:()=>({isCollapsed:true}),addEventListener(){},scrollBy(){scrolls++;}};vm.runInNewContext(source,{window,document,NodeFilter:{SHOW_TEXT:4},getComputedStyle:()=>({display:'block',visibility:'visible'}),Uint32Array,innerHeight:640});
 return{api:window.seekySpeech,nodes,get range(){return range;},body,head};}
let f=fixture(['Olá ','mundo','!']);f.api.init('Olá mundo!');assert(f.api.show(4,9,true));assert.equal(f.range.start.node,f.nodes[1]);assert.equal(f.range.start.offset,0);assert.equal(f.range.end.node,f.nodes[2]);assert.equal(f.range.end.offset,0);checks++;
f=fixture(['Primeira frase.','Segunda frase.']);f.api.init('Primeira frase.\n\nSegunda frase.');assert(f.api.show(17,24,false));assert.equal(f.range.start.node,f.nodes[1]);assert.equal(f.range.start.offset,0);checks++;
f=fixture(['A\u00a0criação ','é bela.']);f.api.init('A criação\né bela.');assert(f.api.show(2,9,false));assert.equal(f.range.start.offset,2);assert.equal(f.range.end.node,f.nodes[1]);checks++;
f=fixture(['Repetido. Repetido.']);f.api.init('Repetido. Repetido.');assert(f.api.show(10,18,false));assert.equal(f.range.start.offset,10);checks++;
f=fixture(['Texto real.']);f.api.init('Outro conteúdo.');assert.equal(f.api.show(0,5,false),false);checks++;
f=fixture(['Um texto com ','formatação.']);f.api.init('Um texto com formatação.');f.api.show(3,8,true);assert.deepEqual(f.nodes.map(n=>n.nodeValue),['Um texto com ','formatação.']);f.api.clear();assert.equal(f.body.children[0].children.length,0);checks++;
console.log('PASS '+checks+' actual highlight script DOM mapping checks');
