package com.custom.zalosub

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_RESULT_CODE = 1001
    private val CHANNEL_ID = "zalo_sub_notify_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        requestNotificationPermission()

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
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Giả lập Chrome trên Windows để chạy Zalo Web mượt nhất
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Cầu nối nhận thông báo từ Zalo Web đẩy sang Android
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                injectMobileResponsiveScript()
            }

            // Mở các đường link bên ngoài (YouTube, web...) bằng trình duyệt mặc định
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.contains("zalo.me") || url.contains("zadn.vn") || url.contains("zdn.vn")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

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

        // Hỗ trợ tải file và hình ảnh về thư mục Downloads của điện thoại
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                if (url.startsWith("blob:")) {
                    Toast.makeText(this, "Nhấn giữ vào ảnh để lưu hoặc mở xem toàn màn hình", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Đang tải file xuống máy...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Không thể tải file này", Toast.LENGTH_SHORT).show()
            }
        }

        webView.loadUrl("https://chat.zalo.me")
    }

    // Xử lý đẩy thông báo ra thanh trạng thái của Vivo X300
    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun showNotification(title: String, body: String) {
            val intent = Intent(mContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(mContext, CHANNEL_ID)
            } else {
                Notification.Builder(mContext)
            }

            builder.setSmallIcon(android.R.drawable.sym_action_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val manager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tin nhắn Zalo 2",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo tin nhắn mới từ Zalo Web"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun injectMobileResponsiveScript() {
        val jsCode = """
            (function() {
                if (window.zaloMobileInjected) return;
                window.zaloMobileInjected = true;

                // 1. Chuyển hướng toàn bộ thông báo của Zalo Web sang thông báo hệ thống Android
                window.Notification = function(title, options) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.showNotification(title || "Zalo 2", (options && options.body) ? options.body : "Bạn có tin nhắn mới");
                    }
                };
                window.Notification.permission = "granted";
                window.Notification.requestPermission = function(cb) {
                    if (cb) cb("granted");
                    return Promise.resolve("granted");
                };

                // 2. Chuẩn hóa khung nhìn Mobile
                let meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = "viewport";
                    document.head.appendChild(meta);
                }
                meta.content = "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no";

                // 3. CSS tối ưu giao diện và cảm ứng
                const style = document.createElement('style');
                style.innerHTML = `
                    * { -webkit-tap-highlight-color: transparent !important; }
                    ::-webkit-scrollbar { width: 0px !important; background: transparent !important; }

                    #conversationListId { width: calc(100vw - 64px) !important; max-width: none !important; }

                    /* Ẩn hoàn toàn cột màu xanh bên trái khi vào chat */
                    body.show-chat nav,
                    body.show-chat #sidebarNav,
                    body.show-chat #main-tab,
                    body.show-chat .nav__tabs,
                    body.show-chat [class*="sidebar"],
                    body.show-chat [class*="left-nav"],
                    body.show-chat .zalo-hidden-sidebar,
                    body.show-chat #conversationListId {
                        display: none !important;
                        width: 0 !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        z-index: -1 !important;
                    }

                    /* Đẩy khung chat tràn màn hình 100% và co giãn theo bàn phím */
                    #chatViewContainer, main, #chatView {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        right: 0 !important;
                        width: 100vw !important;
                        height: 100dvh !important;
                        max-width: 100vw !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        z-index: 2147483640 !important;
                        background: #fff !important;
                        transform: translateX(100%);
                        transition: transform 0.15s ease-out;
                        box-sizing: border-box !important;
                    }

                    body.show-chat #chatViewContainer,
                    body.show-chat main,
                    body.show-chat #chatView {
                        transform: translateX(0) !important;
                    }

                    /* Ô soạn thảo văn bản rộng rãi, dễ nhìn */
                    #chatInputContainer, .chat-input-container, #input_line_wrapper, .rich-input {
                        width: 100% !important;
                        max-width: 100vw !important;
                        padding-left: 14px !important;
                        padding-right: 10px !important;
                        font-size: 15px !important;
                        box-sizing: border-box !important;
                    }

                    /* Nút Quay lại */
                    #zalo-mobile-back {
                        position: fixed;
                        top: 12px;
                        left: 10px;
                        z-index: 2147483647 !important;
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
                    body.show-chat header { padding-left: 85px !important; }
                `;
                document.head.appendChild(style);

                const backBtn = document.createElement('button');
                backBtn.id = 'zalo-mobile-back';
                backBtn.innerText = '← Quay lại';
                backBtn.onclick = () => document.body.classList.remove('show-chat');
                document.body.appendChild(backBtn);

                function detectAndTagSidebar() {
                    document.querySelectorAll('div, nav, aside').forEach(el => {
                        const rect = el.getBoundingClientRect();
                        if (rect.left === 0 && rect.width > 35 && rect.width <= 85 && rect.height > window.innerHeight * 0.5) {
                            if (!el.id.includes('chat') && !el.contains(document.querySelector('#chatViewContainer'))) {
                                el.classList.add('zalo-hidden-sidebar');
                            }
                        }
                    });
                }

                document.addEventListener('click', function(e) {
                    if (e.target.closest('.msg-item') || e.target.closest('[id^="friend-item"]') || e.target.closest('.conv-item')) {
                        detectAndTagSidebar();
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
