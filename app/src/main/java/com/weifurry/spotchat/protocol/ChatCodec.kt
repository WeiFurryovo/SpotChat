package com.weifurry.spotchat.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ChatCodec {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun encodeToLine(packet: WirePacket): String =
        json.encodeToString(packet) + "\n"

    fun encode(packet: WirePacket): ByteArray =
        encodeToLine(packet).toByteArray(Charsets.UTF_8)

    fun decodeLine(line: String): WirePacket =
        json.decodeFromString(line.trim())

    fun decode(bytes: ByteArray): WirePacket =
        decodeLine(bytes.toString(Charsets.UTF_8))
}
