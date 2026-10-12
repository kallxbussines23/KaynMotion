package com.kayn.motion

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class MainActivity : Activity() {
    private val bg = Color.rgb(14, 12, 16)
    private val panel = Color.rgb(29, 23, 31)
    private val accent = Color.rgb(143, 25, 58)
    private val muted = Color.rgb(181, 167, 180)
    private lateinit var player: VideoView
    private lateinit var tracking: TrackingView
    private lateinit var info: TextView
    private lateinit var timeline: SeekBar
    private var source: Uri? = null
    private var durationMs = 0
    private val frames = mutableListOf<JSONObject>()
    private val pickVideo = 44

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float, color: Int = Color.WHITE) =
        TextView(this).apply { text = s; textSize = size; setTextColor(color) }
    private fun action(s: String, primary: Boolean = false, run: () -> Unit) =
        TextView(this).apply {
            text = s; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(if (primary) accent else panel)
            setOnClickListener { run() }
        }
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titles.addView(text("KAYN MOTION", 12f, Color.rgb(235, 85, 120)))
        titles.addView(text("Tracking Studio", 24f))
        header.addView(titles)
        header.addView(action("SAVE", true) { save(); toast("Project disimpan offline") })
        root.addView(header)
        root.addView(gap(12))

        val preview = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(5, 5, 7))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        player = VideoView(this)
        preview.addView(player, FrameLayout.LayoutParams(-1, -1))
        tracking = TrackingView()
        preview.addView(tracking, FrameLayout.LayoutParams(-1, -1))
        root.addView(preview)
        info = text("Import video, lalu geser titik tracking ke objek.", 12f, muted)
        info.setPadding(0, dp(10), 0, dp(6))
        root.addView(info)

        val importRow = LinearLayout(this)
        importRow.addView(action("+ Import video", true) { pick() }, LinearLayout.LayoutParams(0, -2, 1f))
        importRow.addView(action("Add keyframe") { addFrame() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(importRow)
        timeline = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(235, 62, 105))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && durationMs > 0) {
                        val t = durationMs * progress / 1000
                        player.seekTo(t); tracking.moveToTime(t)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(timeline)
        val row = LinearLayout(this)
        row.addView(action("▶ Play / Pause", true) {
            if (source == null) pick() else if (player.isPlaying) player.pause() else player.start()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(action("Reset point") { tracking.resetPoint(); info.text = "Titik di-reset ke tengah." },
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        root.addView(gap(8))
        val foot = text("LOCAL PROJECT  •  MANUAL MOTION TRACKING", 10f, muted)
        foot.gravity = Gravity.CENTER
        root.addView(foot)
        setContentView(root)
        restore()
    }

    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
        }
        startActivityForResult(intent, pickVideo)
    }

    @Deprecated("Legacy result callback for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickVideo || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        source = uri
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        player.setVideoURI(uri)
        player.setOnPreparedListener { mp ->
            durationMs = mp.duration
            info.text = "Video siap • ${durationMs / 1000}s • geser titik lalu tambah keyframe"
            player.seekTo(1)
            tracking.moveToTime(0)
        }
        player.setOnCompletionListener { tracking.moveToTime(durationMs) }
        player.setOnErrorListener { _, _, _ -> toast("Video tidak bisa diputar"); true }
    }

    private fun addFrame() {
        if (source == null) { toast("Import video dulu"); return }
        val pos = player.currentPosition
        val pt = tracking.point()
        val frame = JSONObject().put("timeMs", pos).put("x", pt.first).put("y", pt.second)
        val index = frames.indexOfFirst { it.optInt("timeMs") == pos }
        if (index >= 0) frames[index] = frame else frames.add(frame)
        frames.sortBy { it.optInt("timeMs") }
        tracking.setFrames(frames)
        save()
        info.text = "Keyframe ${frames.size} • ${"%.2f".format(pos / 1000f)}s"
    }

    private fun save() {
        val data = JSONObject()
            .put("videoUri", source?.toString() ?: "")
            .put("keyframes", JSONArray(frames))
        openFileOutput("project.json", MODE_PRIVATE).bufferedWriter().use { it.write(data.toString(2)) }
    }

    private fun restore() {
        try {
            val data = JSONObject(openFileInput("project.json").bufferedReader().use { it.readText() })
            val arr = data.optJSONArray("keyframes") ?: JSONArray()
            for (i in 0 until arr.length()) frames.add(arr.getJSONObject(i))
            tracking.setFrames(frames)
            val saved = data.optString("videoUri", "")
            if (saved.isNotBlank()) {
                source = Uri.parse(saved)
                player.setVideoURI(source)
                player.setOnPreparedListener { mp ->
                    durationMs = mp.duration
                    info.text = "Project dipulihkan • ${frames.size} keyframe"
                    tracking.setFrames(frames)
                }
            }
        } catch (_: Exception) {}
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    inner class TrackingView : View(this) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var x = .5f
        private var y = .5f
        private var time = 0
        private var keys: List<JSONObject> = emptyList()
        fun point() = Pair(x, y)
        fun resetPoint() { x = .5f; y = .5f; invalidate() }
        fun setFrames(list: List<JSONObject>) { keys = list.toList(); moveToTime(time) }
        fun moveToTime(ms: Int) {
            time = ms
            if (keys.isNotEmpty()) {
                val sorted = keys.sortedBy { it.optInt("timeMs") }
                val a = sorted.lastOrNull { it.optInt("timeMs") <= ms } ?: sorted.first()
                val b = sorted.firstOrNull { it.optInt("timeMs") >= ms } ?: sorted.last()
                val ta = a.optInt("timeMs"); val tb = b.optInt("timeMs")
                val f = if (ta == tb) 0f else ((ms - ta).toFloat() / (tb - ta)).coerceIn(0f, 1f)
                x = (a.optDouble("x", .5) * (1 - f) + b.optDouble("x", .5) * f).toFloat()
                y = (a.optDouble("y", .5) * (1 - f) + b.optDouble("y", .5) * f).toFloat()
            }
            invalidate()
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val cx = x * width; val cy = y * height
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2).toFloat()
            paint.color = Color.WHITE
            c.drawLine(cx - dp(25), cy, cx + dp(25), cy, paint)
            c.drawLine(cx, cy - dp(25), cx, cy + dp(25), paint)
            paint.color = Color.rgb(255, 54, 92)
            c.drawCircle(cx, cy, dp(13).toFloat(), paint)
            paint.style = Paint.Style.FILL
            c.drawCircle(cx, cy, dp(3).toFloat(), paint)
            paint.color = Color.WHITE; paint.textSize = dp(10).toFloat()
            c.drawText("TRACK", cx + dp(15), cy - dp(14), paint)
            paint.color = Color.rgb(255, 190, 60)
            keys.forEach { k ->
                c.drawCircle((k.optDouble("x", .5) * width).toFloat(),
                    (k.optDouble("y", .5) * height).toFloat(), dp(4).toFloat(), paint)
            }
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent.requestDisallowInterceptTouchEvent(true)
                    x = (e.x / max(1, width)).coerceIn(0f, 1f)
                    y = (e.y / max(1, height)).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    info.text = "Posisi titik ${(x * 100).toInt()}%, ${(y * 100).toInt()}% • tekan Add keyframe"
                    return true
                }
            }
            return true
        }
    }
}
