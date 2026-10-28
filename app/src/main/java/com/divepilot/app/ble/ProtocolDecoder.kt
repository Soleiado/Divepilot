package com.divepilot.app.ble

import androidx.annotation.StringRes
import com.divepilot.app.R
import kotlin.math.abs
import kotlin.math.round

/** Commande envoyée par les boutons du boîtier (trame de 2 octets). */
enum class BleCommand(val code: String, @StringRes val labelRes: Int) {
    FOCUS("f101", R.string.cmd_focus),
    COMPASS("f102", R.string.cmd_compass),
    PHOTO_VIDEO_TOGGLE("f103", R.string.cmd_photo_video),
    SHUTTER("f10b", R.string.cmd_shutter),
    ZOOM_IN_A("f105", R.string.cmd_zoom_in),
    ZOOM_IN_B("f106", R.string.cmd_zoom_in),
    MENU_A("f107", R.string.cmd_menu),
    MENU_B("f108", R.string.cmd_menu),
    ZOOM_OUT_A("f109", R.string.cmd_zoom_out),
    ZOOM_OUT_B("f10a", R.string.cmd_zoom_out),
    FLASH("f10d", R.string.cmd_flash);

    companion object {
        fun fromCode(code: String): BleCommand? = entries.firstOrNull { it.code == code }
    }
}

/** Résultat de décodage d'une trame BLE. */
sealed interface DecodedFrame {
    val rawHex: String

    data class Command(val command: BleCommand, override val rawHex: String) : DecodedFrame

    data class Telemetry(
        val batteryPercent: Int,
        val temperatureCelsius: Double,
        val depthMeters: Double,
        override val rawHex: String,
    ) : DecodedFrame

    data class Unknown(val note: String, override val rawHex: String) : DecodedFrame
}

object ProtocolDecoder {

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun decode(bytes: ByteArray): DecodedFrame {
        val hex = toHex(bytes)
        return when (bytes.size) {
            2 -> decodeCommand(hex)
            13 -> decodeTelemetry(bytes, hex)
            else -> DecodedFrame.Unknown("${bytes.size} octets (trame non reconnue)", hex)
        }
    }

    private fun decodeCommand(hex: String): DecodedFrame {
        val command = BleCommand.fromCode(hex)
        return if (command != null) DecodedFrame.Command(command, hex)
        else DecodedFrame.Unknown("commande inconnue", hex)
    }

    private fun decodeTelemetry(bytes: ByteArray, hex: String): DecodedFrame {
        // Batterie : octet d'index 2 ; au-delà de 98 on considère « plein » (100 %).
        val batteryRaw = bytes[2].toInt() and 0xFF
        val battery = if (batteryRaw <= 98) batteryRaw else 100

        // Température : octets 8..9 (2 octets, big-endian) ; valeur = raw / 100 (°C).
        val tempRaw = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        val temperature = tempRaw / 100.0

        // Profondeur : octets 10..12 (3 octets, big-endian) ; mètres = round(raw/100 - 100) / 10.
        val depthRaw = ((bytes[10].toInt() and 0xFF) shl 16) or
            ((bytes[11].toInt() and 0xFF) shl 8) or
            (bytes[12].toInt() and 0xFF)
        var depth = round(depthRaw / 100.0 - 100.0) / 10.0
        // Sentinelles : hors d'eau / valeur aberrante -> 0.
        if (depth > 200.0 || depth < 0.0 || abs(depth - 383.2) < 0.01) {
            depth = 0.0
        }

        return DecodedFrame.Telemetry(battery, temperature, depth, hex)
    }
}
