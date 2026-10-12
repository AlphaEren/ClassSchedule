package com.lorat.classschedule.ui

/**
 * 抓取课表页背后的"原始数据"。
 *
 * 很多教务系统是用 XHR/fetch 取回 JSON（或一段 HTML 表格）再拼出课表的，页面上只剩渲染后的
 * DOM，纯文字识别就只能猜。这里在主页面的所有同源 iframe 里挂钩 XMLHttpRequest / fetch，
 * 把最近若干条响应原文记在 `window.__kbNet`，识别时直接读原始数据。
 *
 * – 覆盖加载后才出现的 iframe（短轮询几分钟）
 * – 只保留最后 10 条、每条最多 800KB，避免吃内存
 */
object NetSniffer {
    val installJs: String = """
(function(){
  if(window.__kbHookStarted){ return 'already'; }
  window.__kbHookStarted=1;
  var MAX=10,CAP=800000;
  function push(w,rec){
    try{
      var a=w.__kbNet; if(!a){ a=[]; w.__kbNet=a; }
      a.push(rec); while(a.length>MAX) a.shift();
    }catch(e){}
  }
  function patch(w){
    if(!w) return false;
    try{ if(w.__kbPatched) return false; w.__kbPatched=1; }catch(e){ return false; }
    try{ if(!w.__kbNet) w.__kbNet=[]; }catch(e){}
    try{
      var X=w.XMLHttpRequest;
      if(X&&X.prototype&&X.prototype.open&&X.prototype.send){
        var open=X.prototype.open,send=X.prototype.send;
        X.prototype.open=function(m,u){
          try{ this.__kbReq={m:(m||'GET')+'',u:(u||'')+''}; }catch(e){}
          return open.apply(this,arguments);
        };
        X.prototype.send=function(b){
          var self=this;
          try{
            if(!this.__kbReq) this.__kbReq={m:'GET',u:''};
            if(typeof b==='string'&&b.length<4000) this.__kbReq.b=b;
            this.addEventListener('load',function(){
              try{
                var t=self.responseText;
                if(t&&t.length){ if(t.length>CAP) t=t.substring(0,CAP); push(w,{u:self.__kbReq.u,m:self.__kbReq.m,b:self.__kbReq.b||'',r:t}); }
              }catch(e){}
            });
          }catch(e){}
          return send.apply(this,arguments);
        };
      }
    }catch(e){}
    try{
      var f=w.fetch;
      if(f){
        w.fetch=function(input,init){
          var u='',m='GET',bd='';
          try{
            u=(typeof input==='string')?input:((input&&input.url)||'');
            if(init){ if(init.method) m=init.method+''; if(typeof init.body==='string'&&init.body.length<4000) bd=init.body; }
          }catch(e){}
          return f.apply(w,arguments).then(function(resp){
            try{
              if(resp&&resp.clone) resp.clone().text().then(function(t){ try{ if(t&&t.length){ if(t.length>CAP) t=t.substring(0,CAP); push(w,{u:u,m:m,b:bd,r:t}); } }catch(e){} });
            }catch(e){}
            return resp;
          });
        };
      }
    }catch(e){}
    return true;
  }
  function walkDeep(w,depth){
    if(depth>3) return;
    patch(w);
    var d=null; try{ d=w.document; }catch(e){ return; }
    if(!d) return;
    var fs=null;
    try{ fs=d.querySelectorAll('iframe,frame'); }catch(e){ return; }
    for(var i=0;i<fs.length;i++){
      var cw=null; try{ cw=fs[i].contentWindow; }catch(e){}
      if(cw) walkDeep(cw,depth+1);
    }
  }
  var tries=0;
  function loop(){
    tries++;
    try{ walkDeep(window,0); }catch(e){}
    if(tries<240) window.setTimeout(loop,350);
  }
  loop();
  return 'hook';
})()
"""
}