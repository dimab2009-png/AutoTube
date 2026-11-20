package dev.example.autotube

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock

/**
 * Определяет по GPS, стоит ли автомобиль. Безопасный режим по умолчанию:
 * нет разрешения, нет свежего GPS-сигнала или скорость выше порога — считаем, что движемся.
 */
class DrivingMonitor(context: Context) : LocationListener {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastSpeed = Float.MAX_VALUE
    private var lastFixAt = -FRESH_MS

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
        } catch (_: SecurityException) { /* остаёмся в режиме "движется" */ }
    }

    fun stop() = lm.removeUpdates(this)

    fun isParked(): Boolean =
        SystemClock.elapsedRealtime() - lastFixAt < FRESH_MS && lastSpeed < PARKED_MPS

    override fun onLocationChanged(l: Location) {
        lastSpeed = if (l.hasSpeed()) l.speed else Float.MAX_VALUE
        lastFixAt = SystemClock.elapsedRealtime()
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private companion object {
        const val FRESH_MS = 5000L
        const val PARKED_MPS = 1.0f // ~3.6 км/ч
    }
}
