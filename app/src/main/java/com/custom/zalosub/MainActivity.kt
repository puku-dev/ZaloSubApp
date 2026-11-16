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

        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

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
                    /* Màn hình danh sách bên ngoài */
                    #sidebarNav, nav { width: 52px !important; min-width: 52px !important; }
                    #conversationListId { width: calc(100vw - 52px) !important; max-width: none !important; }

                    /* Ẩn hoàn toàn cột màu xanh bên trái khi đã vào khung chat */
                    body.show-chat #sidebarNav,
                    body.show-chat nav,
                    body.show-chat #main-tab,
                    body.show-chat #conversationListId {
                        display: none !important;
                        width: 0 !important;
                    }

                    /* Khung chat mở rộng 100% màn hình, tự động co giãn theo bàn phím (100dvh) */
                    #chatViewContainer, main {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100dvh !important;
                        max-width: 100vw !important;
                        margin-left: 0 !important;
                        padding-left: 0 !important;
                        z-index: 99999 !important;
                        background: #fff !important;
                        transform: translateX(100%);
                        transition: transform 0.15s ease-out;
                        box-sizing: border-box !important;
                    }

                    body.show-chat #chatViewContainer,
                    body.show-chat main {
                        transform: translateX(0) !important;
                    }

                    /* Đảm bảo ô nhập chữ hiển thị trọn vẹn, không bị tràn lề trái */
                    #chatInputContainer, .chat-input-container, #input_line_wrapper, .rich-input {
                        width: 100% !important;
                        max-width: 100vw !important;
                        padding-left: 10px !important;
                        padding-right: 8px !important;
                        box-sizing: border-box !important;
                    }

                    /* Thu gọn thanh icon công cụ phía trên ô nhập chữ cho vừa màn hình dọc */
                    .chat-box-bar, #chat-box-bar-id {
                        display: flex !important;
                        overflow-x: auto !important;
                        padding-left: 6px !important;
                    }

                    /* Tinh chỉnh nút Quay lại nằm gọn góc trên bên trái */
                    #zalo-mobile-back {
                        position: fixed;
                        top: 12px;
                        left: 8px;
                        z-index: 100000;
                        background: #0068ff;
                        color: white;
                        border: none;
                        padding: 6px 10px;
                        border-radius: 6px;
                        font-weight: bold;
                        font-size: 13px;
                        display: none;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.2);
                    }
                    body.show-chat #zalo-mobile-back { display: block !important; }
                    
                    /* Đẩy tên nhóm/người chat sang phải một chút để nhường chỗ cho nút Quay lại */
                    body.show-chat header { padding-left: 82px !important; }
                `;
                document.head.appendChild(style);

                const backBtn = document.createElement('button');
                backBtn.id = 'zalo-mobile-back';
                backBtn.innerText = '← Quay lại';
                backBtn.onclick = () => document.body.classList.remove('show-chat');
                document.body.appendChild(backBtn);

                document.addEventListener('click', function(e) {
                    if (e.target.closest('.msg-item') || e.target.closest('[id^="friend-item"]') || e.target.closest('.conv-item')) {
                        document.body.classList.add('show-chat');
                    }
                }, true);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override fun onBackPressed() {
        webView.evaluateJavascript("document.body.classList.contains('show-chat')") { result ->
            if (result == "true") {
                webView.evaluateJavascript("document.body.classList.remove('show-chat')", null)
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                moveTaskToBack(true)
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
