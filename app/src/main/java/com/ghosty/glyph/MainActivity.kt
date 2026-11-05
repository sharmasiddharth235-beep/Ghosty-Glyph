package com.ghosty.glyph

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var lastShake = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private lateinit var ghostView: GhostView
    private lateinit var moodLabel: TextView
    private lateinit var glyphController: GlyphMatrixController
    private val idleHandler = android.os.Handler()
    private val idleTask = object : Runnable {
        override fun run() {
            if (::ghostView.isInitialized) ghostView.playRandomIdle()
            idleHandler.postDelayed(this, Random.nextLong(6500L, 13000L))
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (scale > 0) level * 100 / scale else 100
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                ghostView.setMood("Excited", "charge")
            } else if (pct in 0..15) {
                ghostView.setMood("Sleepy", "sleep")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.rgb(16, 16, 16)
        window.navigationBarColor = Color.rgb(16, 16, 16)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(16, 16, 16)) }
        ghostView = GhostView(this) { mood -> moodLabel.text = mood; if (::glyphController.isInitialized) glyphController.showMood(mood) }
        root.addView(ghostView, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "G H O S T Y"
            textSize = 13f
            letterSpacing = .22f
            setTextColor(Color.rgb(220, 218, 214))
            gravity = Gravity.CENTER
        }
        root.addView(title, FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP))

        moodLabel = TextView(this).apply {
            text = "Curious"
            textSize = 14f
            setTextColor(Color.rgb(205, 202, 198))
            gravity = Gravity.CENTER
        }
        val moodLp = FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM)
        moodLp.bottomMargin = dp(62)
        root.addView(moodLabel, moodLp)

        val hint = TextView(this).apply {
            text = "SHAKE  ·  TILT  ·  TAP TO PLAY"
            textSize = 10f
            letterSpacing = .12f
            setTextColor(Color.rgb(115, 113, 110))
            gravity = Gravity.CENTER
        }
        val hintLp = FrameLayout.LayoutParams(-1, dp(38), Gravity.BOTTOM)
        hintLp.bottomMargin = dp(18)
        root.addView(hint, hintLp)
        ghostView.setOnClickListener { ghostView.playTapReaction() }
        setContentView(root)
        glyphController = GlyphMatrixController(this)
        glyphController.init()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onResume() {
        super.onResume()
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (::glyphController.isInitialized) glyphController.onForeground()
        idleHandler.postDelayed(idleTask, 5000L)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (::glyphController.isInitialized) glyphController.onBackground()
        idleHandler.removeCallbacks(idleTask)
    }

    override fun onDestroy() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        if (::glyphController.isInitialized) glyphController.release()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()
        val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
        if (delta > 22f && now - lastShake > 1100L) {
            lastShake = now
            ghostView.playShakeReaction()
        } else {
            ghostView.setTilt((-x / 9.8f).coerceIn(-1f, 1f), (y / 9.8f).coerceIn(-1f, 1f))
            if (z < -7.5f && now - lastShake > 1800L) {
                lastShake = now
                ghostView.setMood("Whoa!", "surprise")
            }
        }
        lastX = x; lastY = y; lastZ = z
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class GhostView(context: Context, val moodChanged: (String) -> Unit) : View(context) {
    private val bitmap = makeGhostBitmap()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER }
    private var mood = "Curious"
    private var action = "idle"
    private var tiltX = 0f
    private var tiltY = 0f
    private var animStart = System.currentTimeMillis()
    private var actionUntil = 0L
    private var shakeWobble = 0f
    private var scaleBoost = 1f
    private val random = Random(System.nanoTime())
    private val stars = ArrayList<Particle>()
    private val frame = object : Runnable { override fun run() { invalidate(); postDelayed(this, 16L) } }

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null); post(frame) }

    private fun makeGhostBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(180, 208, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 239, 235); style = Paint.Style.FILL }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(211, 208, 202); style = Paint.Style.STROKE; strokeWidth = 3f }
        val path = Path().apply {
            moveTo(90f, 8f)
            cubicTo(42f, 8f, 15f, 38f, 15f, 83f)
            lineTo(15f, 175f)
            quadTo(15f, 190f, 29f, 190f)
            lineTo(48f, 177f)
            lineTo(65f, 196f)
            lineTo(90f, 180f)
            lineTo(115f, 196f)
            lineTo(132f, 177f)
            lineTo(151f, 190f)
            quadTo(165f, 190f, 165f, 175f)
            lineTo(165f, 83f)
            cubicTo(165f, 38f, 138f, 8f, 90f, 8f)
            close()
        }
        canvas.drawPath(path, body)
        canvas.drawPath(path, outline)
        val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 37, 37) }
        canvas.drawRoundRect(49f, 75f, 67f, 112f, 7f, 7f, eye)
        canvas.drawRoundRect(113f, 75f, 131f, 112f, 7f, 7f, eye)
        canvas.drawCircle(90f, 137f, 8f, eye)
        val pixel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(218, 215, 209) }
        listOf(31f to 137f, 148f to 137f, 40f to 157f, 140f to 157f, 57f to 183f, 123f to 183f).forEach {
            canvas.drawRect(it.first, it.second, it.first + 4f, it.second + 4f, pixel)
        }
        return bitmap
    }

    fun setTilt(x: Float, y: Float) { tiltX = tiltX * .78f + x * .22f; tiltY = tiltY * .78f + y * .22f }
    fun setMood(label: String, type: String) { mood = label; action = type; animStart = System.currentTimeMillis(); actionUntil = animStart + 2200; moodChanged(label); addParticles(8) }
    fun playShakeReaction() { setMood("Dizzy!!", "dizzy"); shakeWobble = 1f; addParticles(18) }
    fun playTapReaction() { val options = listOf("Happy" to "happy", "Boop!" to "boop", "Surprised" to "surprise", "Love you!" to "love"); val p = options.random(); setMood(p.first, p.second) }
    fun playRandomIdle() { val options = listOf("Curious" to "peek", "Happy" to "happy", "Sleepy" to "sleep", "Bouncy" to "bounce", "Daydreaming" to "float", "Playful" to "spin"); val p = options.random(); setMood(p.first, p.second) }
    private data class Particle(var x: Float, var y: Float, var vy: Float, var life: Float, val glyph: String)
    private fun addParticles(count: Int) { repeat(count) { stars.add(Particle(random.nextFloat() * width.coerceAtLeast(1), height * (.34f + random.nextFloat() * .3f), -random.nextFloat() * 1.8f - .4f, 1f, listOf("·", "✦", "♡", "*", "•").random())) } }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val t = (now - animStart) / 1000f
        val active = now < actionUntil
        val idleBob = sin(now / 520.0).toFloat() * 9f
        var bob = idleBob
        var rot = tiltX * 10f + sin(now / 170.0).toFloat() * 1.4f
        var sx = 1f + sin(now / 680.0).toFloat() * .018f
        var sy = 1f - sin(now / 680.0).toFloat() * .018f
        when (if (active) action else "idle") {
            "dizzy" -> { rot += sin(t * 22f) * 17f * (1f - (t / 2.2f).coerceIn(0f, 1f)); bob += sin(t * 15f) * 13f; sx *= 1.05f }
            "surprise" -> { sy *= 1.13f; sx *= .92f; bob -= 15f * (1f - (t / 2.2f).coerceIn(0f, 1f)) }
            "happy", "boop", "charge" -> { bob -= abs(sin(t * 8f)) * 35f; sx *= 1f + sin(t * 8f) * .04f }
            "sleep" -> { rot += sin(t * 2f) * 4f; bob += 15f; sy *= .96f }
            "peek" -> { rot += sin(t * 4f) * 9f; bob -= abs(sin(t * 3f)) * 16f }
            "float" -> { bob += sin(t * 2f) * 22f; rot += sin(t * 1.5f) * 5f }
            "spin" -> { rot += sin(t * 3f) * 28f; bob -= abs(sin(t * 5f)) * 12f }
            "love" -> { bob -= abs(sin(t * 6f)) * 22f }
            "idle" -> { bob += sin(now / 1900.0).toFloat() * 5f }
        }
        val maxW = width * .72f
        val maxH = height * .52f
        val factor = min(maxW / bitmap.width, maxH / bitmap.height) * scaleBoost
        val w = bitmap.width * factor * sx
        val h = bitmap.height * factor * sy
        val cx = width / 2f + tiltX * 15f
        val cy = height * .48f + bob + tiltY * 8f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rot)
        val dst = RectF(-w / 2, -h / 2, w / 2, h / 2)
        canvas.drawBitmap(bitmap, null, dst, paint)
        canvas.restore()
        if (active && action == "love") canvas.drawText("♡", cx + w * .38f, cy - h * .42f - abs(sin(t * 5f)) * 20f, textPaint)
        if (active && action == "sleep") { textPaint.textSize = 27f; textPaint.alpha = 180; canvas.drawText("z  z", cx + w * .38f, cy - h * .42f - t * 8f % 50f, textPaint); textPaint.alpha = 255; textPaint.textSize = 34f }
        val iterator = stars.iterator()
        while (iterator.hasNext()) { val p = iterator.next(); p.y += p.vy; p.life -= .018f; textPaint.alpha = (p.life * 220).toInt().coerceIn(0, 220); textPaint.textSize = 22f; canvas.drawText(p.glyph, p.x, p.y, textPaint); if (p.life <= 0f) iterator.remove() }
        textPaint.alpha = 255; textPaint.textSize = 34f
        if (shakeWobble > 0f) shakeWobble = (shakeWobble - .035f).coerceAtLeast(0f)
    }
}
