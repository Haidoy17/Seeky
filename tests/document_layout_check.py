"""Exercise the bundled DOCX renderer in Chromium with local-only intercepted resources."""
from pathlib import Path
import argparse,zipfile,io,base64,json,os,time
p=argparse.ArgumentParser();p.add_argument('--browsers',type=Path,required=True);p.add_argument('--evidence',type=Path,required=True);a=p.parse_args();os.environ['PLAYWRIGHT_BROWSERS_PATH']=str(a.browsers)
from playwright.sync_api import sync_playwright
r=Path(__file__).parents[1];assets=r/'app/src/main/assets/document';a.evidence.mkdir(parents=True,exist_ok=True)
word='http://schemas.openxmlformats.org/wordprocessingml/2006/main';rel='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
parts={
'[Content_Types].xml':'''<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/></Types>''',
'_rels/.rels':f'''<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rDoc" Type="{rel}/officeDocument" Target="word/document.xml"/></Relationships>''',
'word/_rels/document.xml.rels':f'''<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="styles" Type="{rel}/styles" Target="styles.xml"/><Relationship Id="head" Type="{rel}/header" Target="header1.xml"/><Relationship Id="foot" Type="{rel}/footer" Target="footer1.xml"/><Relationship Id="image" Type="{rel}/image" Target="media/image.png"/><Relationship Id="alt" Type="{rel}/aFChunk" Target="unsafe.html"/></Relationships>''',
'word/styles.xml':f'''<w:styles xmlns:w="{word}"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Georgia" w:hAnsi="Georgia"/><w:sz w:val="24"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:b/><w:color w:val="005599"/><w:sz w:val="36"/></w:rPr></w:style></w:styles>''',
'word/header1.xml':f'''<w:hdr xmlns:w="{word}"><w:p><w:r><w:t>Cabeçalho de teste</w:t></w:r></w:p></w:hdr>''',
'word/footer1.xml':f'''<w:ftr xmlns:w="{word}"><w:p><w:r><w:t>Rodapé de teste</w:t></w:r></w:p></w:ftr>''',
'word/unsafe.html':'<script>window.attacked=true</script>',
'word/document.xml':f'''<w:document xmlns:w="{word}" xmlns:r="{rel}" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body><w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Física e Natureza</w:t></w:r></w:p><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Negrito original</w:t></w:r><w:r><w:t xml:space="preserve"> e </w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t>Itálico original</w:t></w:r></w:p><w:p><w:r><w:rPr><w:u w:val="single"/><w:color w:val="cc4400"/></w:rPr><w:t>Sublinhado e cor</w:t></w:r></w:p><w:tbl><w:tblPr><w:tblW w:w="9000" w:type="dxa"/><w:tblBorders><w:top w:val="single" w:sz="8" w:color="005599"/><w:bottom w:val="single" w:sz="8" w:color="005599"/><w:left w:val="single" w:sz="8" w:color="005599"/><w:right w:val="single" w:sz="8" w:color="005599"/><w:insideH w:val="single" w:sz="8" w:color="005599"/><w:insideV w:val="single" w:sz="8" w:color="005599"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w="6000"/><w:gridCol w:w="3000"/></w:tblGrid><w:tr><w:tc><w:tcPr><w:shd w:fill="e1edf5"/></w:tcPr><w:p><w:r><w:t>Módulo 1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>12 horas</w:t></w:r></w:p></w:tc></w:tr></w:tbl><w:p><w:r><w:drawing><wp:inline><wp:extent cx="457200" cy="457200"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="Imagem"/></pic:nvPicPr><pic:blipFill><a:blip r:embed="image"/></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="457200" cy="457200"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p><w:altChunk r:id="alt"/><w:sectPr><w:headerReference w:type="default" r:id="head"/><w:footerReference w:type="default" r:id="foot"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:left="1440" w:right="1440" w:bottom="1440" w:header="720" w:footer="720"/></w:sectPr></w:body></w:document>'''
}
b=io.BytesIO()
with zipfile.ZipFile(b,'w',zipfile.ZIP_DEFLATED) as z:
 for n,v in parts.items():z.writestr(n,v)
 z.writestr('word/media/image.png',base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXa8AAAAASUVORK5CYII='))
data=b.getvalue();(a.evidence/'layout-fixture.docx').write_bytes(data)
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,args=['--no-sandbox']);page=browser.new_page(viewport={'width':900,'height':1100});blocked=[]
 def route(request):
  from urllib.parse import urlsplit
  u=urlsplit(request.request.url)
  if u.hostname!='seeky-document.invalid':blocked.append(request.request.url);return request.abort()
  if u.path=='/input.docx':return request.fulfill(status=200,body=data,content_type='application/vnd.openxmlformats-officedocument.wordprocessingml.document')
  file=assets/Path(u.path).name
  if file.is_file():return request.fulfill(path=str(file),content_type='text/html' if file.suffix=='.html' else 'application/javascript')
  return request.fulfill(status=404,body='')
 page.route('**/*',route);page.goto('https://seeky-document.invalid/document/docx.html');
 for attempt in range(300):
  state=page.evaluate("window.seekyState")
  if state in ["ready","error"]:break
  time.sleep(.1)
 assert state=="ready",state
 result=page.evaluate('''() => {const find=t=>[...document.querySelectorAll('span')].find(e=>e.textContent===t),style=t=>getComputedStyle(find(t));return {font:style('Negrito original').fontFamily,bold:style('Negrito original').fontWeight,italic:style('Itálico original').fontStyle,underline:style('Sublinhado e cor').textDecorationLine,color:style('Sublinhado e cor').color,tables:document.querySelectorAll('table').length,cells:[...document.querySelectorAll('td')].map(e=>e.textContent),headers:document.body.textContent.includes('Cabeçalho de teste'),footers:document.body.textContent.includes('Rodapé de teste'),images:document.querySelectorAll('img').length,iframes:document.querySelectorAll('iframe').length,attacked:window.attacked===true,pages:document.querySelectorAll('section.docx').length};}''')
 assert int(result['bold'])>=700 and result['italic']=='italic' and 'underline' in result['underline'] and 'Georgia' in result['font'] and result['color']=='rgb(204, 68, 0)',result
 assert result['tables']==1 and result['cells']==['Módulo 1','12 horas'],result
 assert result['headers'] and result['footers'] and result['images']==1 and result['iframes']==0 and not result['attacked'],result
 page.screenshot(path=str(a.evidence/'docx-format-layout.png'),full_page=True);page.set_viewport_size({'width':360,'height':720});page.reload();
 for attempt in range(300):
  if page.evaluate("window.seekyState")=="ready":break
  time.sleep(.1)
 mobile=page.evaluate("({width:window.innerWidth,scroll:document.documentElement.scrollWidth,zoom:parseFloat(document.getElementById('document').style.zoom)})");assert mobile['scroll']<=mobile['width']+2,mobile;result['mobile']=mobile;page.screenshot(path=str(a.evidence/'docx-mobile-layout.png'),full_page=True);(a.evidence/'docx-layout-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print('PASS DOCX original bold, italic, underline, colour, table, image, header, footer, page and blocked altChunk script');browser.close()
