"""Render the production Java EPUB adapter output in an isolated local Chromium fixture."""
from pathlib import Path
import argparse,json,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--browsers',type=Path,required=True);p.add_argument('--evidence',type=Path,required=True);a=p.parse_args();a.evidence.mkdir(parents=True,exist_ok=True);os.environ['PLAYWRIGHT_BROWSERS_PATH']=str(a.browsers)
root=Path(__file__).parents[1];subprocess.run(['java','-Dseeky.epub.fixture='+str(a.evidence),'-cp',str(root/'build/test-classes'),'mz.bam.seeky.android.EpubLayoutTest'],check=True)
from playwright.sync_api import sync_playwright
from urllib.parse import urlsplit
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,args=['--no-sandbox','--no-zygote','--single-process']);page=browser.new_page(viewport={'width':360,'height':800},java_script_enabled=False);mode={'css':'fit.css'}
 def route(request):
  u=urlsplit(request.request.url)
  if u.hostname!='seeky-document.invalid':return request.abort()
  name={'/book/source.html':'source.html','/book/fitted.html':'fitted.html','/book/book.css':'book.css','/book/image.svg':'image.svg','/reader/epub.css':mode['css']}.get(u.path)
  if not name:return request.fulfill(status=404,body='')
  content_type='text/css' if name.endswith('.css') else 'image/svg+xml' if name.endswith('.svg') else 'text/html; charset=UTF-8'
  return request.fulfill(path=str(a.evidence/name),content_type=content_type)
 page.route('**/*',route);page.goto('https://seeky-document.invalid/book/source.html');before=page.evaluate('({viewport:innerWidth,scroll:document.documentElement.scrollWidth})');assert before['scroll']>before['viewport']+100,before;page.screenshot(path=str(a.evidence/'epub-before.png'),full_page=True)
 expected=page.locator('body').inner_text();assert 'Capítulo 1' in expected and 'formatação' in expected,expected;results={'before':before,'fitted':[]}
 def measure():
  return page.evaluate('''() => {let bad=[];const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let n;while(n=walker.nextNode()){if(!n.textContent.trim()||['SCRIPT','STYLE'].includes(n.parentElement.tagName))continue;let r=document.createRange();r.selectNodeContents(n);for(const b of r.getClientRects())if(b.width>0&&(b.left<-.5||b.right>innerWidth+.5))bad.push({text:n.textContent.slice(0,60),left:b.left,right:b.right});}const h=getComputedStyle(document.querySelector('h1')),b=getComputedStyle(document.body);return {viewport:innerWidth,scroll:document.documentElement.scrollWidth,bad,font:b.fontFamily,headingColor:h.color,headingWeight:h.fontWeight,underline:getComputedStyle(document.querySelector('u')).textDecorationLine,table:document.querySelectorAll('table').length,images:document.querySelectorAll('img').length,padding:b.paddingLeft,align:getComputedStyle(document.querySelector('p.first')).textAlign,spacing:getComputedStyle(document.querySelector('p.first')).lineHeight,scriptRan:window.bookScriptRan===true};}''')
 for width in [320,360,640]:
  page.set_viewport_size({'width':width,'height':800});page.goto('https://seeky-document.invalid/book/fitted.html');result=measure();assert result['scroll']<=width+1 and not result['bad'],result;assert 'Georgia' in result['font'] and result['headingColor']=='rgb(23, 74, 164)' and int(result['headingWeight'])>=700 and 'underline' in result['underline'],result;assert result['images']==1 and result['table']==1 and not result['scriptRan'],result;assert page.locator('body').inner_text()==expected;results['fitted'].append(result);page.screenshot(path=str(a.evidence/f'epub-fit-{width}.png'),full_page=True)
 mode['css']='custom.css';page.set_viewport_size({'width':360,'height':800});page.reload();custom=measure();assert custom['padding']=='28px' and custom['align']=='justify' and custom['spacing']=='33.6px',custom;assert custom['scroll']<=361 and not custom['bad'],custom;assert page.locator('body').inner_text()==expected;results['custom']=custom;page.screenshot(path=str(a.evidence/'epub-custom-360.png'),full_page=True)
 (a.evidence/'epub-layout-result.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n');browser.close();print('PASS EPUB overflow reproduced, all text fits at 320/360/640 px, rich formatting retained, custom margins/spacing/alignment applied')
