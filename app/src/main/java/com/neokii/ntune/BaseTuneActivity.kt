package com.neokii.ntune

import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.viewpager.widget.ViewPager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.neokii.ntune.ui.main.SectionsPagerAdapter
import kotlinx.android.synthetic.main.activity_tune.*
import org.json.JSONObject
import java.util.*

abstract class BaseTuneActivity : BaseActivity(), ViewPager.OnPageChangeListener {

    private var viewPager: ViewPager? = null
    private var sectionsPagerAdapter: SectionsPagerAdapter? = null

    private var tts: TextToSpeech? = null

    abstract fun getTuneKey(): String
    abstract fun getRemoteConfFile(): String
    abstract fun getItemList(json: JSONObject): ArrayList<TuneItemInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts?.language = Locale.getDefault()
            }
        }

        setContentView(R.layout.activity_tune)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        updateOrientaion(resources.configuration.orientation)

        intent?.let {

            try {
                val session = SshSession(it.getStringExtra("host")!!, 8022)
                session.connect(object : SshSession.OnConnectListener {
                    override fun onConnect() {

                        session.exec(
                            "test -f /data/openpilot/selfdrive/ntune.py && echo 1",
                            object : SshSession.OnResponseListener {
                                override fun onResponse(res: String) {

                                    if(res.trim() != "1") {
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            R.string.not_support_ntune,
                                            Snackbar.LENGTH_LONG
                                        )
                                            .show()
                                    }
                                    else {
                                        session.exec(
                                            "cat ${getRemoteConfFile()}",
                                            object : SshSession.OnResponseListener {
                                                override fun onResponse(res: String) {
                                                    start(res)
                                                }

                                                override fun onEnd(e: Exception?) {

                                                    if (e != null) {
                                                        Snackbar.make(
                                                            findViewById(android.R.id.content),
                                                            e.localizedMessage,
                                                            Snackbar.LENGTH_LONG
                                                        )
                                                            .show()
                                                    }
                                                }
                                            })
                                    }
                                }

                                override fun onEnd(e: Exception?) {

                                    if (e != null) {
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            e.localizedMessage,
                                            Snackbar.LENGTH_LONG
                                        )
                                            .show()
                                    }
                                }
                            })

                    }

                    override fun onFail(e: Exception) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            e.localizedMessage,
                            Snackbar.LENGTH_LONG
                        )
                            .show()
                    }

                })
            }

            catch (e: Exception) {
                Toast.makeText(MyApp.getContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
            }

        }

        btnResetAll.text = getString(R.string.tune_reset_all_format, getTuneKey())

        btnResetAll.setOnClickListener {

            AlertDialog.Builder(this@BaseTuneActivity)
                .setMessage(R.string.confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                    android.R.string.ok
                ) { _, _ ->
                    resetAll()
                }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        viewPager?.removeOnPageChangeListener(this)

        tts?.stop()
        tts?.shutdown()
    }

    private fun start(res: String)
    {
        intent?.let {

            try {

                val json = JSONObject(res)
                val list = getItemList(json)

                sectionsPagerAdapter = it.getStringExtra("host")?.let { host ->
                    SectionsPagerAdapter(
                        this, supportFragmentManager, list,
                        host,
                        getRemoteConfFile()
                    )
                }
                val viewPager: ViewPager = findViewById(R.id.view_pager)
                viewPager.adapter = sectionsPagerAdapter
                val tabs: TabLayout = findViewById(R.id.tabs)
                tabs.setupWithViewPager(viewPager)

                viewPager.addOnPageChangeListener(this)
            }
            catch (e: Exception)
            {
                if(res.isEmpty())
                {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.conf_load_failed,
                        Snackbar.LENGTH_LONG
                    )
                        .show()
                }
                else
                {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        e.localizedMessage,
                        Snackbar.LENGTH_LONG
                    )
                        .show()
                }
            }

        }
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {

        sectionsPagerAdapter?.let {

            try {
                val text = it.getPageTitle(position).toString()
                    .split(Regex("(?=[A-Z])"))
                    .joinToString(" ")

                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
            catch (e:Exception){}
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateOrientaion(newConfig.orientation)
    }

    private fun updateOrientaion(orientation: Int)
    {
        if(orientation == ORIENTATION_LANDSCAPE)
            findViewById<View>(R.id.title).visibility = View.GONE
        else
            findViewById<View>(R.id.title).visibility = View.VISIBLE
    }

    private fun resetAll() {
        intent?.getStringExtra("host")?.let { host ->
            try {

                val session = SshSession(host, 8022)
                session.connect(object : SshSession.OnConnectListener {
                    override fun onConnect() {
                        session.exec(
                            "rm ${getRemoteConfFile()} && echo '1' > /data/params/d/SoftRestartTriggered",
                            object : SshSession.OnResponseListener {
                                override fun onResponse(res: String) {
                                    //sectionsPagerAdapter?.notifyDataSetChanged()
                                    finish()
                                }

                                override fun onEnd(e: Exception?) {

                                    if (e != null) {
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            e.localizedMessage,
                                            Snackbar.LENGTH_LONG
                                        )
                                            .show()
                                    }
                                }
                            })
                    }

                    override fun onFail(e: Exception) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            e.localizedMessage,
                            Snackbar.LENGTH_LONG
                        )
                            .show()
                    }

                })
            }

            catch (e: Exception) {
                Toast.makeText(MyApp.getContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
            }

        }
    }
}
