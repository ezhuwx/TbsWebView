package com.ez.kotlin.webview

import android.app.Application
import android.content.Context
import android.util.Log
import com.tencent.smtt.export.external.TbsCoreSettings
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.QbSdk.PreInitCallback
import com.tencent.smtt.sdk.TbsDownloader
import com.tencent.smtt.sdk.TbsListener
import com.tencent.smtt.sdk.TbsListener.ErrorCode
import java.util.HashMap

/**
 * @author : ezhuwx
 * Describe :Tbs初始化工具
 * Designed on 2021/11/8
 * E-mail : ezhuwx@163.com
 * Update on 9:30 by ezhuwx
 */
open class TbsUtils {
    lateinit var mContext: Context
    val TAG = "TbsLoadUtil"
    var mInit = false

    fun init(mContext: Application) {
        this.mContext = mContext.applicationContext
        initSdk()
        QbSdk.setTbsListener(object : TbsListener {
            override fun onDownloadFinish(i: Int) {
                Log.d(TAG, "load：$i")
                if (!mInit && i != ErrorCode.DOWNLOAD_SUCCESS && !TbsDownloader.isDownloading()) {
                    reDownload()
                }
            }

            override fun onInstallFinish(i: Int) {
                if (i == ErrorCode.DOWNLOAD_INSTALL_SUCCESS) {
                    initSdk()
                }
                Log.d(TAG, "finish：$i")
            }

            override fun onDownloadProgress(i: Int) {
                //下载进度监听
                Log.d(TAG, "progress：$i")
            }
        })
    }

    private fun initSdk() {
        val map = HashMap<String, Any>()
        map[TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER] = true
        map[TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE] = true
        QbSdk.initTbsSettings(map)
        QbSdk.setDownloadWithoutWifi(true)
        QbSdk.disableAutoCreateX5Webview()
        QbSdk.initX5Environment(mContext, object : PreInitCallback {
            override fun onCoreInitFinished() {}
            override fun onViewInitFinished(init: Boolean) {
                mInit = init
                Log.e(TAG, "Tbs Load：$init")
                if (!mInit && TbsDownloader.needDownload(mContext, false)
                    && !TbsDownloader.isDownloading()
                ) {
                    reDownload()
                }
            }
        })
    }

    private fun reDownload() {
        QbSdk.reset(mContext)
        TbsDownloader.startDownload(mContext)
    }
}