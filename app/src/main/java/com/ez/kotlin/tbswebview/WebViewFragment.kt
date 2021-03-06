package com.ez.kotlin.tbswebview

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

/**
 * @author : ezhuwx
 * Describe :
 * Designed on 2021/11/8
 * E-mail : ezhuwx@163.com
 * Update on 9:35 by ezhuwx
 */
open class WebViewFragment : Fragment() {
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
    private val isLoadError = false
    private var url = ""

    companion object {
        const val TAG = "TbsWebView"
        const val URL = "web_view_url"
        const val WEB_VIEW_ID = 0x1014
        const val IS_CAN_ZOOM_CONTROL = "is_can_zoom_control"
        const val IS_LOADING_PROGRESS = "is_loading_progress"

        fun newInstance(
            url: String,
            isLoadingProgress: Boolean,
            isCanZoomControl: Boolean
        ): WebViewFragment {
            val fragment = WebViewFragment()
            val bundle = Bundle()
            bundle.putString(URL, url)
            bundle.putBoolean(IS_CAN_ZOOM_CONTROL, isCanZoomControl)
            bundle.putBoolean(IS_LOADING_PROGRESS, isLoadingProgress)
            fragment.arguments = bundle
            return fragment
        }

        fun newInstance(
            url: String,
            isCanZoomControl: Boolean
        ): WebViewFragment {
            val fragment = WebViewFragment()
            val bundle = Bundle()
            bundle.putString(URL, url)
            bundle.putBoolean(IS_CAN_ZOOM_CONTROL, isCanZoomControl)
            fragment.arguments = bundle
            return fragment
        }

        fun newInstance(url: String): WebViewFragment {
            val fragment = WebViewFragment()
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
            url = getString(URL, "")
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
     * ??????URL ??????WebView??????
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
        onWebViewListener?.onWebView(webView)
        webView.loadUrl(url)
    }

    /**
     * webView????????????
     */
    private fun initWebView() {
        with(webView.settings) {
            this@WebViewFragment.webSettings = this
            //????????????????????????true??????setBuiltInZoomControls????????????
            setSupportZoom(isCanZoomControl)
            //????????????????????????????????????false?????????WebView????????????
            builtInZoomControls = isCanZoomControl
            //???????????????????????????
            displayZoomControls = false
            //????????????????????????webView?????????
            useWideViewPort = true
            //????????????????????????
            allowFileAccess = true
            // ????????????????????????
            loadWithOverviewMode = true
            //????????????????????????
            loadsImagesAutomatically = true
            blockNetworkImage = false
            //??????????????????
            defaultTextEncodingName = "utf-8"
            //???????????????
            databaseEnabled = false
            setGeolocationEnabled(true)
            setAllowFileAccessFromFileURLs(true)
            setAllowUniversalAccessFromFileURLs(true)
            val dir: String = requireActivity().getDir("database", Context.MODE_PRIVATE).getPath()
            setGeolocationDatabasePath(dir)
            //??????DomStorage??????
            domStorageEnabled = true
            //????????????JS???????????????
            javaScriptCanOpenWindowsAutomatically = false
            /*????????????*/
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        /*?????????????????????*/
        webView.view.isVerticalScrollBarEnabled = false
        /*??????????????????*/
        webView.setLayerType(WebView.LAYER_TYPE_NONE, null)
        /*????????????????????????*/
        webView.isDrawingCacheEnabled = true
    }

    /**
     * ????????????????????????
     */
    private fun overrideWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!isLoadError && !url.lowercase(Locale.getDefault()).startsWith("http")) {
                    return true
                }
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
     * TBS WebView ?????????????????????
     */
    private fun initWebVideo() {
        val data = Bundle()
        /*android:configChanges="orientation|screenSize|keyboardHidden
         * webView ??????Activity ????????????????????? ??????standardFullScreen?????????true
         * true?????????????????????false??????X5????????????????????????false*/data.putBoolean("standardFullScreen", false)
        //false??????????????????true?????????????????????????????????true standardFullScreen = false ???????????????
        data.putBoolean("supportLiteWnd", false)
        //1??????????????????????????????2?????????????????????????????????????????????1
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
         * ??????JS?????????????????????
         * ???JS???????????????????????????@JavascriptInterface??????
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
         * ??????????????????
         *
         * @param progress ??????
         */
        fun onLoad(progress: Int)

        /**
         * ????????????????????????
         */
        fun onFinished()

        /**
         * ????????????????????????
         */
        fun onStart()
    }


    interface OnWebViewListener {
        /**
         * webView ???????????????
         *
         * @param webView ??????
         */
        fun onWebView(webView: WebView?)
    }


    interface OnFileChooseListener {
        /**
         * ????????????
         *
         * @param uploadMsg  uploadMsg
         * @param acceptType acceptType
         * @param capture    capture
         */
        fun onFileChoose(uploadMsg: ValueCallback<Uri>?, acceptType: String?, capture: String?)

        /**
         * ????????????
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
         * ????????????
         */
        fun onLoadError()
    }
}