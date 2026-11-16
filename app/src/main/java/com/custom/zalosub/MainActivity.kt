package com.custom.zalosub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_RESULT_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Ép User-Agent thành Chrome trên Windows 10 để Zalo cho phép mở giao diện Web
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

        // Cho phép lưu Cookie vĩnh viễn để không bị văng tài khoản khi đóng app
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                injectMobileResponsiveScript()
            }
        }

        // Hỗ trợ chọn ảnh/file từ điện thoại để gửi qua Zalo
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(Intent.createChooser(intent, "Chọn ảnh hoặc file"), FILE_CHOOSER_RESULT_CODE)
                return true
            }
        }

        webView.loadUrl("https://chat.zalo.me")
    }

    private fun injectMobileResponsiveScript() {
        val jsCode = """
            (function() {
                if (window.zaloMobileInjected) return;
                window.zaloMobileInjected = true;

                let meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = "viewport";
                    document.head.appendChild(meta);
                }
                meta.content = "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no";

                const style = document.createElement('style');
                style.innerHTML = `
                    #sidebarNav { width: 48px !important; }
                    #conversationListId { width: calc(100vw - 48px) !important; max-width: none !important; }
                    #chatViewContainer {
                        position: fixed !important;
                        top: 0; left: 0; width: 100vw !important; height: 100vh !important;
                        z-index: 9999; background: #fff;
                        transform: translateX(100%);
                        transition: transform 0.2s ease-in-out;
                    }
                    body.show-chat #chatViewContainer { transform: translateX(0) !important; }
                    #zalo-mobile-back {
                        position: fixed; top: 12px; left: 10px; z-index: 10000;
                        background: #0068ff; color: white; border: none;
                        padding: 6px 12px; border-radius: 6px; font-weight: bold;
                        display: none; box-shadow: 0 2px 5px rgba(0,0,0,0.2);
                    }
                    body.show-chat #zalo-mobile-back { display: block !important; }
                    body.show-chat header { padding-left: 80px !important; }
                `;
                document.head.appendChild(style);

                const backBtn = document.createElement('button');
                backBtn.id = 'zalo-mobile-back';
                backBtn.innerText = '← Quay lại';
                backBtn.onclick = () => document.body.classList.remove('show-chat');
                document.body.appendChild(backBtn);

                document.addEventListener('click', function(e) {
                    if (e.target.closest('.msg-item') || e.target.closest('[id^="friend-item"]')) {
                        document.body.classList.add('show-chat');
                    }
                }, true);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    // Bắt cử chỉ vuốt cạnh trái/phải của Vivo X300 để quay lại danh sách chat thay vì thoát app
    override fun onBackPressed() {
        webView.evaluateJavascript("document.body.classList.contains('show-chat')") { result ->
            if (result == "true") {
                webView.evaluateJavascript("document.body.classList.remove('show-chat')", null)
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                moveTaskToBack(true) // Thu nhỏ app xuống nền thay vì đóng hẳn
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) return
            val results: Array<Uri>? = if (resultCode == RESULT_OK && data != null) {
                val clipData = data.clipData
                if (clipData != null) {
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else {
                    data.data?.let { arrayOf(it) }
                }
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
