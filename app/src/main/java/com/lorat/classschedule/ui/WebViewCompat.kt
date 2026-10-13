package com.lorat.classschedule.ui

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Shared WebView hardening for campus academic systems.
 *
 *  - 保留登录态（Cookie + DOM Storage）
 *  - 校园站点常见证书链不完整 / 自签名，自动放行，避免白屏
 *  - “课表查询”常用 window.open 弹窗打开，这里让弹窗复用同一个 WebView
 */
object WebViewCompat {

    fun configure(
        web: WebView,
        headless: Boolean = false,
        onPageFinished: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
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
                view.evaluateJavascript(NetSniffer.installJs, null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(NetSniffer.installJs, null)
                onPageFinished(url)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.proceed()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onError(error.description?.toString() ?: "页面加载失败")
            }
        }
        // A WebView may not host its own popup window, so window.open() requests are handed to a
        // throwaway WebView whose navigation is immediately moved into the visible one.
        var popupHolder: WebView? = null
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

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
