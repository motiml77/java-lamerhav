const { chromium } = require('playwright');
const http=require('http'),fs=require('fs'),path=require('path');
const ROOT='C:/Users/MOTILE~1/AppData/Local/Temp/claude/C--Users-Moti-Levi-Desktop-AI-shiurei-kodesh/3acfcd05-a44d-4b41-92f5-94febe6f29b6/scratchpad/sdk-test';
http.createServer((req,res)=>{
  const f=path.join(ROOT,'index.html');
  res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});fs.createReadStream(f).pipe(res);
}).listen(4399, async ()=>{
  let b; try{b=await chromium.launch({channel:'chrome'});}catch(e){b=await chromium.launch({channel:'msedge'});}
  const pg=await b.newPage();
  pg.on('pageerror',e=>console.log('PAGEERR',String(e).slice(0,150)));
  await pg.goto('http://localhost:4399/',{waitUntil:'domcontentloaded'});
  await pg.waitForFunction('window.__done === true',{timeout:420000}).catch(()=>console.log('(timeout)'));
  console.log(await pg.textContent('#out'));
  await b.close(); process.exit(0);
});
