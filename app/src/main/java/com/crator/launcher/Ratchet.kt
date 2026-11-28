package com.crator.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * «Καστάνια» σαν παιχνίδι με καντράν: κρ-κρ-κρ στη δόνηση (και προαιρετικά ο ήχος)
 * όσο γυρνάς τη σελίδα προς το ταμπλό. Οι ήχοι είναι 3 μικρά κλικ κομμένα από το δείγμα.
 */
class Ratchet(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val sounds = listOf(R.raw.tick1, R.raw.tick2, R.raw.tick3).map { pool.load(app, it, 1) }
    private var n = 0
    private var lastAt = 0L

    // μικρό, ξερό «κρ»: primitive αν υπάρχει, αλλιώς σύντομο κλικ
    private val effect: VibrationEffect? = runCatching {
        val v = vib ?: return@runCatching null
        if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f).compose()
        else VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }.getOrNull()

    fun tick(haptic: Boolean, sound: Boolean) {
        val now = System.nanoTime()
        if (now - lastAt < 18_000_000L) return // πάνω από ~55/δευτ. θα γινόταν βουητό
        lastAt = now
        if (haptic) effect?.let { e -> runCatching { vib?.vibrate(e) } }
        if (sound && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val id = sounds[n++ % sounds.size]
            pool.play(id, 0.55f, 0.55f, 1, 0, 0.96f + (n % 3) * 0.03f)
        }
    }

    fun release() = runCatching { pool.release() }
}
