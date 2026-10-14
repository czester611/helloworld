package pl.helloworld.music

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*

class Track(val name: String, val uri: Uri)

class MainActivity : Activity() {
    private val factors = listOf(1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 2.0f, 3.0f)
    private val tracks = mutableListOf<Track>()
    private var index = -1
    private var player: MediaPlayer? = null
    private var faster = true
    private var factor = 1.0f
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var title: TextView
    private lateinit var speedLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var playBtn: Button
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val factorButtons = mutableMapOf<Float, Button>()
    private lateinit var fasterBtn: Button
    private lateinit var slowerBtn: Button

    private val ticker = object : Runnable {
        override fun run() {
            player?.let { if (it.isPlaying && !seek.isPressed) seek.progress = it.currentPosition }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        buildUi()
        handler.post(ticker)
        getPreferences(MODE_PRIVATE).getString("dir", null)?.let {
            try { loadDir(Uri.parse(it)) } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(24), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#121212"))
        }
        root.addView(btn("📁 Wczytaj katalog") { pickDir() })

        title = TextView(this).apply {
            text = "Nie wybrano utworu"; textSize = 18f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(title)

        seek = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                    if (user) player?.seekTo(p)
                }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        root.addView(seek)

        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun navBtn(t: String, f: () -> Unit) = btn(t, f).also {
            nav.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        navBtn("⏮") { play(index - 1) }
        navBtn("−10s") { player?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) } }
        playBtn = navBtn("▶") { togglePlay() }
        navBtn("+10s") { player?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) } }
        navBtn("⏭") { play(index + 1) }
        root.addView(nav)

        speedLabel = TextView(this).apply {
            textSize = 16f; setTextColor(Color.parseColor("#80CBC4"))
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(speedLabel)

        val mode = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fasterBtn = btn("Przyspiesz") { faster = true; refreshSpeed() }
        slowerBtn = btn("Zwolnij") { faster = false; refreshSpeed() }
        mode.addView(fasterBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        mode.addView(slowerBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        mode.addView(btn("Normalnie") { factor = 1.0f; refreshSpeed() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(mode)

        for (row in factors.chunked(4)) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER }
            for (f in row) {
                val b = btn("×$f") { factor = f; refreshSpeed() }
                factorButtons[f] = b
                r.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(r)
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list = ListView(this).apply {
            this.adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> play(pos) }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshSpeed()
    }

    private fun effectiveSpeed() = if (factor == 1.0f) 1.0f else if (faster) factor else 1f / factor

    private fun refreshSpeed() {
        val sp = effectiveSpeed()
        speedLabel.text = "Prędkość: ×%.2f".format(sp) +
            if (factor == 1.0f) "" else if (faster) "  (przyspieszone ×$factor)" else "  (zwolnione ÷$factor)"
        fasterBtn.alpha = if (faster) 1f else 0.5f
        slowerBtn.alpha = if (faster) 0.5f else 1f
        factorButtons.forEach { (f, b) -> b.alpha = if (f == factor) 1f else 0.6f }
        applySpeed()
    }

    private fun applySpeed() {
        val p = player ?: return
        try {
            val wasPlaying = p.isPlaying
            p.playbackParams = PlaybackParams().setSpeed(effectiveSpeed())
            // Setting params on some devices starts playback; restore the original state.
            if (!wasPlaying && p.isPlaying) p.pause()
        } catch (_: Exception) {}
    }

    private fun pickDir() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 1 || res != RESULT_OK) return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        getPreferences(MODE_PRIVATE).edit().putString("dir", uri.toString()).apply()
        loadDir(uri)
    }

    private fun loadDir(tree: Uri) {
        tracks.clear()
        scan(tree, DocumentsContract.getTreeDocumentId(tree))
        tracks.sortBy { it.name.lowercase() }
        adapter.clear()
        adapter.addAll(tracks.map { it.name })
        Toast.makeText(this, "Znaleziono utworów: ${tracks.size}", Toast.LENGTH_SHORT).show()
    }

    private fun scan(tree: Uri, docId: String) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val name = c.getString(1); val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) scan(tree, id)
                else if (mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in
                    setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus"))
                    tracks.add(Track(name, DocumentsContract.buildDocumentUriUsingTree(tree, id)))
            }
        }
    }

    private fun play(i: Int) {
        if (tracks.isEmpty()) return
        index = (i + tracks.size) % tracks.size
        player?.release()
        val t = tracks[index]
        title.text = t.name
        list.setItemChecked(index, true)
        player = MediaPlayer().apply {
            try {
                setDataSource(this@MainActivity, t.uri)
                setOnPreparedListener { mp ->
                    seek.max = mp.duration
                    mp.start()
                    applySpeed()
                    playBtn.text = "⏸"
                }
                setOnCompletionListener { play(index + 1) }
                setOnErrorListener { _, _, _ -> play(index + 1); true }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePlay() {
        val p = player
        if (p == null) { play(if (index < 0) 0 else index); return }
        if (p.isPlaying) { p.pause(); playBtn.text = "▶" }
        else { p.start(); applySpeed(); playBtn.text = "⏸" }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release()
        super.onDestroy()
    }
}
