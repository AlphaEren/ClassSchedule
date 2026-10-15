package com.lorat.classschedule.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lorat.classschedule.data.AppLog
import com.lorat.classschedule.data.redactUrl

/**
 * Shared WebView hardening for campus academic systems.
 *
 *  - 保留登录态（Cookie + DOM Storage）
 *  - 校园站点常见证书链不完整 / 自签名，自动放行，避免白屏
 *  - “课表查询”常用 window.open 弹窗打开，这里让弹窗复用同一个 WebView
 *  - 出问题时把 JS console / HTTP 错误 / 页面 DOM 概况回报给界面，便于自查（否则只能看到白屏）
 */
object WebViewCompat {

    private const val Tag = "WebViewCompat"

    /**
     * 页面「加载完成」不代表前端框架起来了（Angular/Vue 站点几秒后才渲染）。
     * 这里回报标题、body 长度、输入框位置、加载遮罩状态等，白屏时一眼能看出卡在哪。
     */
    private const val ProbeJs = """
(function(){
  try{
    var b=document.body, txt=b?(b.innerText||''):'';
    var vp=document.querySelector('meta[name=viewport]');
    var pw=document.querySelector('input[type=password]')||document.querySelector('input');
    var r=null; try{ r=pw?pw.getBoundingClientRect():null; }catch(e){}
    return JSON.stringify({
      title:document.title,
      bodyLen:b?b.innerHTML.length:0,
      textLen:txt.replace(/\s+/g,'').length,
      scripts:document.scripts?document.scripts.length:0,
      ready:document.readyState,
      vw:window.innerWidth, vh:window.innerHeight,
      sw:document.documentElement?document.documentElement.scrollWidth:0,
      sh:document.documentElement?document.documentElement.scrollHeight:0,
      dpr:window.devicePixelRatio,
      metaVp:vp?vp.getAttribute('content'):'',
      input:r?(Math.round(r.left)+','+Math.round(r.top)+' '+Math.round(r.width)+'x'+Math.round(r.height)):'none',
      scrollY:Math.round(window.scrollY||0),
      overlay:(function(){
        try{
          var e=document.querySelector('.preloader,[class*=preloader]');
          if(!e) return 'none';
          var cs=getComputedStyle(e), rr=e.getBoundingClientRect();
          return cs.position+','+cs.display+',op='+cs.opacity+',z='+cs.zIndex+',size='+Math.round(rr.width)+'x'+Math.round(rr.height);
        }catch(err){ return 'err'; }
      })(),
      hidden:(function(){
        try{
          var e=document.querySelector('input[type=password]')||document.querySelector('input');
          var out=[]; var i=0;
          while(e && i<30){
            var cs=getComputedStyle(e);
            if(cs.opacity!=='1' || cs.visibility!=='visible' || cs.display==='none' || (cs.transform&&cs.transform!=='none')){
              out.push(e.tagName+'#'+((e.className||'')+'').slice(0,22)+' op='+cs.opacity+' vis='+cs.visibility+' disp='+cs.display+' tf='+((cs.transform||'none')+'').slice(0,26));
            }
            e=e.parentElement; i++;
          }
          return out.length? out.join(' || ') : 'clean';
        }catch(err){ return 'err'; }
      })(),
      imgs:(function(){ var a=document.images||[],ok=0; for(var i=0;i<a.length;i++){ if(a[i].complete&&a[i].naturalWidth>0) ok++; } return ok+'/'+a.length; })(),
      fonts:document.fonts?document.fonts.status:'n/a'
    });
  }catch(e){ return 'probe-error: '+e; }
})()
"""

    /** 把系统 WebView 的 UA 里 `; wv`、`Version/4.0` 去掉，得到同版本 Chrome 的 UA。 */
    private fun chromeLikeUa(context: Context): String =
        runCatching {
            WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace(Regex("\\s*Version/\\d+(\\.\\d+)*"), "")
        }.getOrNull().orEmpty()

    fun configure(
        web: WebView,
        headless: Boolean = false,
        onPageFinished: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
        onError: (String) -> Unit = {},
        /** JS console 输出（登录页白屏时靠它定位）。 */
        onConsole: (String) -> Unit = {},
        /** 子资源/主文档的 HTTP 错误。 */
        onHttpError: (String, Int) -> Unit = { _, _ -> },
        /** 页面加载完成后探测到的 DOM 概况，用于判断「白屏」。 */
        onPageInfo: (String) -> Unit = {},
    ) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            // 有些校园系统按 UA 下发不同皮肤，WebView 默认 UA 带 wv 与 Version/4.0 时
            // 可能拿到渲染不全的页面；对齐成同版本 Chrome 的 UA。
            userAgentString = chromeLikeUa(web.context)
            loadWithOverviewMode = true
            useWideViewPort = true
            // 桌面版页面允许捏合缩放（不显示那两颗 ± 按钮）。
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // API 30+ 默认禁止 file://，而「从 HTML 文件导入」与本地调试页都需要它。
            // 只开放文件读取本身，跨文件 XHR（allowFileAccessFromFileURLs）仍然关闭。
            allowFileAccess = true
            allowContentAccess = true
            if (headless) {
                // Background refresh only reads text + layout, so skip images/cached chrome for speed.
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // Install as early as we can so the timetable's own XHR/fetch calls are captured.
                AppLog.i("WebView", "开始加载 " + redactUrl(url))
                view.evaluateJavascript(NetSniffer.installJs, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                AppLog.i("WebView", "加载完成 " + redactUrl(url))
                view.evaluateJavascript(NetSniffer.installJs, null)
                fun probe(tag: String) {
                    view.evaluateJavascript(ProbeJs) { raw ->
                        // evaluateJavascript 回传的是「转义后的 JSON 字符串」，先还原成可读 JSON
                        val text = raw.orEmpty()
                            .trim()
                            .removeSurrounding("\"")
                            .replace("\\\"", "\"")
                            .replace("\\/", "/")
                        val line = "$tag $text"
                        Log.i(Tag, line)
                        AppLog.i("WebView", line)
                        onPageInfo(line)
                    }
                }
                probe("PAGE")
                view.postDelayed({ probe("PAGE+6s") }, 6000)
                onPageFinished(url)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                Log.w(Tag, "ERR ${error.errorCode} ${error.description} ${request.url}")
                if (request.isForMainFrame) {
                    AppLog.e(
                        "WebView",
                        "页面加载失败 code=${error.errorCode} ${error.description} ${redactUrl(request.url.toString())}",
                    )
                    onError(error.description?.toString() ?: "页面加载失败")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                val code = errorResponse.statusCode
                Log.w(Tag, "HTTP $code ${request.url}")
                AppLog.w("WebView", "HTTP $code ${redactUrl(request.url.toString())}")
                if (request.isForMainFrame) onHttpError(request.url.toString(), code)
            }
        }
        // A WebView may not host its own popup window, so window.open() requests are handed to a
        // throwaway WebView whose navigation is immediately moved into the visible one.
        var popupHolder: WebView? = null
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val line = "[${msg.messageLevel()}] ${msg.message()}  (${msg.sourceId()}:${msg.lineNumber()})"
                Log.w(Tag, line)
                AppLog.w("WebView", line)
                onConsole(line)
                return true
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setSupportMultipleWindows(false)
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                var moved = false
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        if (!moved) {
                            moved = true
                            view.loadUrl(request.url.toString())
                        }
                        return true
                    }

                    override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                        if (!moved && url.isNotBlank() && url != "about:blank") {
                            moved = true
                            view.loadUrl(url)
                        }
                    }
                }
                popupHolder = popup
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
    }
}
