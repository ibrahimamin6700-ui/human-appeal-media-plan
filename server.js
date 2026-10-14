const http=require('http');
const fs=require('fs');
const path=require('path');

const root=__dirname;
const port=process.env.PORT||8080;
const types={
  '.html':'text/html; charset=utf-8',
  '.js':'application/javascript; charset=utf-8',
  '.json':'application/json; charset=utf-8',
  '.webmanifest':'application/manifest+json; charset=utf-8',
  '.png':'image/png',
  '.jpg':'image/jpeg',
  '.jpeg':'image/jpeg',
  '.svg':'image/svg+xml',
  '.txt':'text/plain; charset=utf-8'
};

function safePath(urlPath){
  let pathname=decodeURIComponent((urlPath||'/').split('?')[0]);
  if(pathname==='/'||pathname==='')pathname='/index.html';
  const full=path.normalize(path.join(root,pathname));
  return full.startsWith(root)?full:null;
}

const server=http.createServer((req,res)=>{
  const file=safePath(req.url);
  if(!file){res.writeHead(403);return res.end('Forbidden');}
  fs.stat(file,(err,stat)=>{
    let target=file;
    if(!err&&stat.isDirectory())target=path.join(file,'index.html');
    fs.readFile(target,(readErr,data)=>{
      if(readErr){
        fs.readFile(path.join(root,'index.html'),(fallbackErr,fallback)=>{
          if(fallbackErr){res.writeHead(404);return res.end('Not found');}
          res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-cache'});res.end(fallback);
        });
        return;
      }
      const ext=path.extname(target).toLowerCase();
      const headers={'Content-Type':types[ext]||'application/octet-stream'};
      headers['Cache-Control']=ext==='.html'||ext==='.js'||ext==='.webmanifest'?'no-cache':'public, max-age=86400';
      res.writeHead(200,headers);res.end(data);
    });
  });
});
server.listen(port,()=>console.log(`Human Appeal Media Plan app listening on ${port}`));
