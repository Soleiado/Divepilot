package com.divepilot.app.ble

import java.util.UUID

/**
 * Constantes du protocole BLE du boîtier étanche.
 *
 * Ces valeurs (UUID de service/caractéristiques et nom d'appareil) sont des faits
 * d'interopérabilité nécessaires pour dialoguer avec le même boîtier ; elles sont
 * reprises comme telles, sans copier le code de l'application d'origine.
 */
object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("e6400001-b5a3-f393-e0a9-e50e24dcca9e")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("e6400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("e6400003-b5a3-f393-e0a9-e50e24dcca9e")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Filtre de scan : nom d'appareil contenant « dive ». */
    const val SCAN_NAME_FILTER = "dive"

    /** Filtre de reconnexion : nom d'appareil contenant « hotdive ». */
    const val RECONNECT_NAME_FILTER = "hotdive"
}
