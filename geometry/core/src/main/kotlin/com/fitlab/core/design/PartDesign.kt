package com.fitlab.core.design

import com.fitlab.core.fit.FitClass
import com.fitlab.core.fit.FitReference
import com.fitlab.core.geom.Ring
import com.fitlab.core.geom.Vec2
import com.fitlab.core.templates.TemplateId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Projekt części: obrys ze zdjęcia albo szablon parametryczny. */
@Serializable
sealed interface PartDesign

/** Część z obrysu (mm): obrys CCW wycentrowany, wycięcia CW, otwory okrągłe, grubość. */
@Serializable
@SerialName("outline")
data class OutlineDesign(
    val outer: Ring,
    val innerContours: List<Ring> = emptyList(),
    val holes: List<HoleFeature> = emptyList(),
    val thicknessMm: Double,
    val topChamferMm: Double = 0.0,
) : PartDesign

/** Okrągły otwór przelotowy. */
@Serializable
data class HoleFeature(
    val id: String,
    val center: Vec2,
    val nominalDiameterMm: Double,
    val reference: FitReference = FitReference.ORIGINAL,
    val fit: FitClass = FitClass.SLIDE,
    val chamferMm: Double = 0.0,
    val needsCaliperCheck: Boolean = false,
)

/** Część z szablonu: wartości parametrów i klasy pasowań (klucze z ParamSpec/FitSlotSpec). */
@Serializable
@SerialName("template")
data class TemplateDesign(
    val templateId: TemplateId,
    val values: Map<String, Double>,
    val fits: Map<String, FitClass>,
) : PartDesign
