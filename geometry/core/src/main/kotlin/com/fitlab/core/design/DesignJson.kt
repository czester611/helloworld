package com.fitlab.core.design

import com.fitlab.core.fit.CompensationTable
import com.fitlab.core.fit.ResolvedFeature
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Serializacja projektów, tabel kompensacji i cech do JSON (pole `type` jako dyskryminator). */
object DesignJson {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(design: PartDesign): String = json.encodeToString(PartDesign.serializer(), design)
    fun decode(text: String): PartDesign = json.decodeFromString(PartDesign.serializer(), text)

    fun encodeCompensation(t: CompensationTable): String = json.encodeToString(CompensationTable.serializer(), t)
    fun decodeCompensation(text: String): CompensationTable = json.decodeFromString(CompensationTable.serializer(), text)

    fun encodeFeatures(f: List<ResolvedFeature>): String =
        json.encodeToString(ListSerializer(ResolvedFeature.serializer()), f)

    fun decodeFeatures(text: String): List<ResolvedFeature> =
        json.decodeFromString(ListSerializer(ResolvedFeature.serializer()), text)
}
