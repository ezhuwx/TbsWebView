package com.ez.kotlin.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.tencent.smtt.export.external.interfaces.JsResult
import com.tencent.smtt.sdk.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author : ezhuwx
 * Describe :
 * Designed on 2021/11/8
 * E-mail : ezhuwx@163.com
 * Update on 9:35 by ezhuwx
 */
open class QzWebFragment : Fragment() {
    private lateinit var webViewPb: ProgressBar
    private lateinit var webViewLl: ViewGroup
    private lateinit var webView: WebView
    private lateinit var webSettings: WebSettings
    protected var isLoadingProgress = true
    private var isCanZoomControl = true
    private var onWebViewListener: OnWebViewListener? = null
    private var onLoadErrorListener: OnLoadErrorListener? = null
    private var onFileChooseListener: OnFileChooseListener? = null
    private var onLoadListener: OnLoadListener? = null
    private var isFirstLoad = false
    private val isInit = AtomicBoolean(false)
    private var url: String? = null

    companion object {
        const val TAG = "TbsWebView"
        const val URL = "web_view_url"
        const val WEB_VIEW_ID = 0x1014
        const val IS_CAN_ZOOM_CONTROL = "is_can_zoom_control"
        const val IS_LOADING_PROGRESS = "is_loading_progress"

        fun newInstance(
            url: String? = null,
            isLoadingProgress: Boolean,
            isCanZoomControl: Boolean
        ): QzWebFragment {
            val fragment = QzWebFragment()
            val bundle = Bundle()
            bundle.putString(URL, url)
            bundle.putBoolean(IS_CAN_ZOOM_CONTROL, isCanZoomControl)
            bundle.putBoolean(IS_LOADING_PROGRESS, isLoadingProgress)
            fragment.arguments = bundle
            return fragment
        }

        fun newInstance(
            url: String? = null,
            isCanZoomControl: Boolean
        ): QzWebFragment {
            val fragment = QzWebFragment()
            val bundle = Bundle()
            bundle.putString(URL, url)
            bundle.putBoolean(IS_CAN_ZOOM_CONTROL, isCanZoomControl)
            fragment.arguments = bundle
            return fragment
        }

        fun newInstance(url: String? = null): QzWebFragment {
            val fragment = QzWebFragment()
            val bundle = Bundle()
            bundle.putString(URL, url)
            fragment.arguments = bundle
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isFirstLoad = true
        arguments?.run {
            url = getString(URL, null)
            isCanZoomControl = getBoolean(IS_CAN_ZOOM_CONTROL, true)
            isLoadingProgress = getBoolean(IS_LOADING_PROGRESS, true)
        }
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        webViewPb = view.findViewById(R.id.webView_pb)
        webViewLl = view.findViewById(R.id.webView_ll)
        initWebWidget()
        return view;
    }

    override fun onDestroy() {
        with(webView) {
            loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            clearHistory()
            (parent as ViewGroup).removeView(webView)
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }


    /**
     * 接收URL 新建WebView控件
     */
    private fun initWebWidget() {
        with(WebView(requireContext())) {
            webView = this
            view.overScrollMode = View.OVER_SCROLL_NEVER
            id = WEB_VIEW_ID
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            webViewLl.addView(webView)
        }
        initWebView()
        jsListener()
        initWebVideo()
        overrideWebView()
        url?.let {
            webView.loadUrl(it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isInit.compareAndSet(true, false)) {
            onWebViewListener?.onWebView(webView)
        }
    }

    /**
     * webView初始配置
     */
    private fun initWebView() {
        with(webView.settings) {
            this@QzWebFragment.webSettings = this
            //支持缩放，默认为true。是setBuiltInZoomControls的前提。
            setSupportZoom(isCanZoomControl)
            //设置内置的缩放控件。若为false，则该WebView不可缩放
            builtInZoomControls = isCanZoomControl
            //隐藏原生的缩放控件
            displayZoomControls = false
            //将图片调整到适合webView的大小
            useWideViewPort = true
            //设置可以访问文件
            allowFileAccess = true
            // 缩放至屏幕的大小
            loadWithOverviewMode = true
            //支持自动加载图片
            loadsImagesAutomatically = true
            blockNetworkImage = false
            //设置编码格式
            defaultTextEncodingName = "utf-8"
            //启用数据库
            databaseEnabled = false
            setGeolocationEnabled(true)
            setAllowFileAccessFromFileURLs(true)
            setAllowUniversalAccessFromFileURLs(true)
            val dir: String = requireActivity().getDir("database", Context.MODE_PRIVATE).getPath()
            setGeolocationDatabasePath(dir)
            //开启DomStorage缓存
            domStorageEnabled = true
            //支持通过JS打开新窗口
            javaScriptCanOpenWindowsAutomatically = false
            /*缓存关闭*/
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        /*去除滚动条显示*/
        webView.view.isVerticalScrollBarEnabled = false
        /*关闭硬件加速*/
        webView.setLayerType(WebView.LAYER_TYPE_NONE, null)
        /*防止视频播放失败*/
        webView.isDrawingCacheEnabled = true
    }

    /**
     * 内部点击跳转拦截
     */
    private fun overrideWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                Log.i(TAG, url)
                return true
            }
        }
        webView.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack()
                true
            } else {
                false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(webView: WebView, newProgress: Int) {
                webViewPb.progress = newProgress
                onLoadListener?.onLoad(newProgress)
                if (newProgress == 100) {
                    isFirstLoad = false
                    webViewPb.visibility = View.GONE
                    onLoadListener?.onFinished()
                } else {
                    if (isFirstLoad && isLoadingProgress) {
                        webViewPb.visibility = View.GONE
                        onLoadListener?.onStart()
                    }
                }
            }

            override fun onJsAlert(
                webView: WebView,
                url: String,
                message: String,
                jsResult: JsResult
            ): Boolean {
                return super.onJsAlert(webView, url, message, jsResult)
            }

            override fun onJsConfirm(
                webView: WebView,
                s: String,
                s1: String,
                jsResult: JsResult
            ): Boolean {
                jsResult.confirm()
                return super.onJsConfirm(webView, s, s1, jsResult)
            }

            override fun openFileChooser(
                uploadMsg: ValueCallback<Uri>,
                acceptType: String,
                capture: String
            ) {
                onFileChooseListener?.onFileChoose(uploadMsg, acceptType, capture)
            }

            //Android 5.0+
            @SuppressLint("NewApi")
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                onFileChooseListener?.onFileChoose(filePathCallback, fileChooserParams)
                return true
            }
        }
    }

    /**
     * TBS WebView 视频初始化配置
     */
    private fun initWebVideo() {
        val data = Bundle()
        /*android:configChanges="orientation|screenSize|keyboardHidden
         * webView 所在Activity 必须满足此属性 否则standardFullScreen属性为true
         * true表示标准全屏，false表示X5全屏；不设置默认false*/data.putBoolean("standardFullScreen", false)
        //false：关闭小窗；true：开启小窗；不设置默认true standardFullScreen = false 时才可设置
        data.putBoolean("supportLiteWnd", false)
        //1：以页面内开始播放，2：以全屏开始播放；不设置默认：1
        data.putInt("DefaultVideoScreen", 2)
        webView.scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY
        val extension = webView.x5WebViewExtension
        extension?.invokeMiscMethod("setVideoParams", data)
    }


    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun jsListener() {
        webSettings.javaScriptEnabled = true
        webView.addJavascriptInterface(JsCallAndroid(), "app")
    }


    fun setOnLoadErrorListener(onLoadErrorListener: OnLoadErrorListener?) {
        this.onLoadErrorListener = onLoadErrorListener
    }

    fun setOnLoadListener(onLoadListener: OnLoadListener?) {
        this.onLoadListener = onLoadListener
    }

    private class JsCallAndroid {
        /**
         * 定义JS需要调用的方法
         * 被JS调用的方法必须加入@JavascriptInterface注解
         */
        @JavascriptInterface
        fun loadComplete() {
        }
    }


    fun setWebViewListener(onWebViewListener: OnWebViewListener?) {
        this.onWebViewListener = onWebViewListener
    }

    fun setOnFileChooseListener(onFileChooseListener: OnFileChooseListener?) {
        this.onFileChooseListener = onFileChooseListener
    }

    interface OnLoadListener {
        /**
         * 网页加载回调
         *
         * @param progress 进度
         */
        fun onLoad(progress: Int)

        /**
         * 网页加载完成回调
         */
        fun onFinished()

        /**
         * 网页加载开始回调
         */
        fun onStart()
    }


    interface OnWebViewListener {
        /**
         * webView 初始化回调
         *
         * @param webView 对象
         */
        fun onWebView(webView: WebView?)
    }


    interface OnFileChooseListener {
        /**
         * 文件选择
         *
         * @param uploadMsg  uploadMsg
         * @param acceptType acceptType
         * @param capture    capture
         */
        fun onFileChoose(uploadMsg: ValueCallback<Uri>?, acceptType: String?, capture: String?)

        /**
         * 文件选择
         *
         * @param filePathCallback  filePathCallback
         * @param fileChooserParams fileChooserParams
         */
        fun onFileChoose(
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?
        )
    }

    interface OnLoadErrorListener {
        /**
         * 加载失败
         */
        fun onLoadError()
    }
}