package com.neokii.ntune

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.neokii.ntune.databinding.CmdButtonBinding
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : BaseActivity(), SshShell.OnSshListener
{
    var session: SshSession? = null
    var shell: SshShell? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true)
            //actionBar.setDisplayHomeAsUpEnabled(true)
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                actionBar.title = "${getString(R.string.app_name)} ${packageInfo.versionName}"
            } catch (e: java.lang.Exception) {
            }
        }

        setContentView(R.layout.activity_main)

        SettingUtil.getString(applicationContext, "last_host", "").also {
            if(it.isNotEmpty())
                editHost.setText(it)
        }

        btnConnectIndi.setOnClickListener {
            handleConnect(IndiTuneActivity::class.java)
        }

        btnConnectTorque.setOnClickListener {
            handleConnect(TorqueTuneActivity::class.java)
        }

        btnConnectScc.setOnClickListener {
            handleConnect(SccTuneActivity::class.java)
        }

        if(Feature.FEATURE_UNIVERSAL)
            btnConnectScc.visibility = View.GONE

        btnSshKey.setOnClickListener {
            val intent = Intent(this, SshKeySettingActivity::class.java)
            startActivity(intent)
        }

        btnSyncTime.setOnClickListener {
            syncTime()
        }

        btnGeneral.setOnClickListener {
            handleConnect(GeneralTuneActivity::class.java)
        }

        btnExceptionCapture.setOnClickListener {

            val host = editHost.text.toString();
            if(host.isNotEmpty())
            {
                val intent = Intent(this, ExceptionCaptureActivity::class.java)
                intent.putExtra("host", host)
                startActivity(intent)
            }
        }

        btnScan.setOnClickListener {
            handleScan()
        }

        btnGitAccount.setOnClickListener {
            handleGitAccount()
        }

        editHost.setOnKeyListener { v, keyCode, _ -> Boolean
            if(keyCode == KeyEvent.KEYCODE_ENTER)
            {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }

        checkComma3?.isChecked = SettingUtil.getBoolean(applicationContext, "is_tici", false)

        checkComma3?.setOnCheckedChangeListener { _, isChecked ->
            SettingUtil.setBoolean(applicationContext, "is_tici", isChecked)

            SshShell.refesh()
            listView.adapter?.notifyDataSetChanged()
        }

        btnGitAccount.visibility = View.GONE

        buildButtons()
        updateScan(false)

        if(!SettingUtil.getBoolean(this, "launched", false)) {
            handleScan()
        }

        SettingUtil.setBoolean(this, "launched", true)

        checkPortrait?.let { checkBox ->
            checkBox.isChecked = SettingUtil.getBoolean(applicationContext, "lock_portrait", false)
            checkBox.setOnClickListener {
                SettingUtil.setBoolean(applicationContext, "lock_portrait", checkBox.isChecked)
                updateOrientation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        session?.close()
        shell?.close()

        SettingUtil.setBoolean(this, "launched", false)
    }

    private fun handleConnect(cls: Class<out BaseTuneActivity>)
    {
        try {
            val host = editHost.text.toString();
            if(host.isNotEmpty())
            {
                updateControls(true)

                val imm: InputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editHost.windowToken, 0)

                session = SshSession(host, 8022)
                session?.connect(object : SshSession.OnConnectListener {
                    override fun onConnect() {

                        updateControls(false)

                        SettingUtil.setString(applicationContext, "last_host", host)

                        val intent = Intent(this@MainActivity, cls)

                        intent.putExtra("host", host)
                        startActivity(intent)
                    }

                    override fun onFail(e: Exception) {

                        updateControls(false)
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            e.localizedMessage,
                            Snackbar.LENGTH_LONG
                        )
                            .show()
                    }
                })
            }
        }

        catch (e: Exception) {
            Toast.makeText(MyApp.getContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateControls(pending: Boolean)
    {
        btnGeneral.isEnabled = !pending
        btnConnectIndi.isEnabled = !pending
        btnConnectTorque.isEnabled = !pending
        btnConnectScc.isEnabled = !pending

        btnExceptionCapture.isEnabled = !pending
    }

    private fun updateScan(pending: Boolean)
    {
        if(pending)
        {
            progBar.visibility = View.VISIBLE
            btnScan.isEnabled = false
            editHost.isEnabled = false
        }
        else
        {
            progBar.visibility = View.INVISIBLE
            btnScan.isEnabled = true
            editHost.isEnabled = true
        }
    }

    var pendingScan: Boolean = false

    private fun handleScan()
    {
        if(pendingScan)
            return

        pendingScan = true
        updateScan(pendingScan)
        EonScanner().startScan(applicationContext, 8022, object : EonScanner.OnResultListener {
            override fun onResult(ip: String?) {

                pendingScan = false
                updateScan(pendingScan)

                if (!TextUtils.isEmpty(ip)) {
                    editHost.setText(ip)
                    SettingUtil.setString(applicationContext, "last_host", ip)

                    Snackbar.make(
                        findViewById(android.R.id.content), getString(
                            R.string.format_scan_success,
                            ip
                        ), Snackbar.LENGTH_LONG
                    )
                        .show()
                } else {
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.scan_fail),
                        Snackbar.LENGTH_LONG
                    )
                        .show()
                }
            }
        })
    }

    private fun handleGitAccount()
    {
        val f = GitAccountDialog()
        f.show(supportFragmentManager, null)
    }

    fun DP2PX(context: Context, dp: Int): Int {
        val resources: Resources = context.resources
        val metrics: DisplayMetrics = resources.getDisplayMetrics()
        return (dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    private fun addLog(text: String)
    {
        if(logView.text.length > 1024*1024)
            logView.text = ""

        logView.append(text + "\n")
        logScrollView.post {
            logScrollView.smoothScrollTo(0, logView.bottom)
        }
    }

    inner class ListAdapter: RecyclerView.Adapter<ListAdapter.ViewHolder>()
    {
        inner class ViewHolder(val binding: CmdButtonBinding) : RecyclerView.ViewHolder(binding.root)
        {
            init {
                binding.btnCmd.setOnClickListener {
                    val cmd = SshShell.cmdList[adapterPosition]

                    val host = editHost.text.toString()
                    if(host.isNotEmpty())
                    {
                        if(cmd.cmds.size == 1 && cmd.cmds[0].equals("git_change_branch")) {
                            changeBranch()
                        }
                        else {
                            if(cmd.confirm)
                            {
                                AlertDialog.Builder(this@MainActivity)
                                    .setMessage(R.string.confirm)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton(
                                        android.R.string.ok
                                    ) { _, _ ->
                                        shell(host, cmd.cmds)
                                    }
                                    .show()
                            }
                            else
                            {
                                shell(host, cmd.cmds)
                            }
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return SshShell.cmdList.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(CmdButtonBinding.inflate(layoutInflater))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            val cmd = SshShell.cmdList[position]
            holder.binding.btnCmd.text = getString(cmd.resId)
        }
    }

    private fun buildButtons()
    {
        listView.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        listView.adapter = ListAdapter()
    }

    fun shell(host: String, cmds: ArrayList<String>)
    {
        try {
            if(shell == null || shell?.host != host || !shell?.isConnected()!!) {

                if(shell != null)
                    shell?.close()

                shell = SshShell(host, 8022, this)
                shell?.start()
            }

            if(shell?.isConnected() == true)
                addLog("\n")

            for (cmd in cmds)
                shell?.send(cmd)
        }
        catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(MyApp.getContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onConnect() {
        TODO("Not yet implemented")
    }

    override fun onError(e: java.lang.Exception) {
        TODO("Not yet implemented")
    }

    override fun onRead(res: String) {
        addLog(res)
    }

    private fun syncTime()
    {
        val host = editHost.text.toString()

        val pattern = "yyyy-MM-dd HH:mm:ss"
        val simpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date: String = simpleDateFormat.format(Date())

        if(host.isNotEmpty())
        {
            val cmd = "date -s \"$date\""
            shell(host, arrayListOf(cmd))
        }
    }

    private fun changeBranch() {

        val runnable = {
            session?.exec(
                "cd /data/openpilot && git branch",
                object : SshSession.OnResponseListener {
                    override fun onResponse(res: String) {
                        selectBranch(res)
                    }

                    override fun onEnd(e: Exception?) {
                        if (e != null) {
                            session = null
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


        if(session != null) {
            runnable()
        }
        else {
            val host = editHost.text.toString();
            if(host.isNotEmpty()) {
                val imm: InputMethodManager =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editHost.windowToken, 0)

                session = SshSession(host, 8022).also { s ->

                    s.connect(object : SshSession.OnConnectListener {
                        override fun onConnect() {
                            runnable()
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
            }
        }

    }

    private fun selectBranch(res: String) {

        val items = mutableListOf<String>()

        res.split("\n").also {
            for(tok in it) {
                tok.trim().also { branch ->

                    if(branch.isNotEmpty())
                        items.add(branch)
                }
            }
        }

        if(items.size > 0) {

            AlertDialog.Builder(this).also { dialog ->
                dialog.setTitle(R.string.git_change_branch_desc)
                dialog.setItems(items.toTypedArray(),
                    DialogInterface.OnClickListener { dialog, which ->

                        session?.let {
                            val branch = items[which].replace("*", "").trim()
                            shell(it.host, arrayListOf("git checkout $branch"))
                        }
                    })

                    dialog.create().show()
            }
        }
    }

}
