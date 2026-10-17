package com.fitlab.app.ui.nav

import com.fitlab.core.templates.TemplateId

/** Trasy nawigacji (tekstowe). */
object Routes {
    const val HOME = "home"
    const val NEW = "new"
    const val PHOTO = "photo?enclosure={enclosure}"
    const val ENCLOSURE_SOURCE = "enclosure_source"
    const val ENCLOSURE = "enclosure/{partId}"
    const val TEMPLATES = "templates"
    const val TEMPLATE_EDITOR = "template/{templateId}?partId={partId}"
    const val OUTLINE = "outline/{partId}?enclosure={enclosure}"
    const val PART = "part/{partId}"
    const val TEST = "test/{iterationId}"
    const val PROFILES = "profiles"
    const val COMPENSATION = "compensation"
    const val CALIBRATION = "calibration"
    const val MARKERS = "markers"
    const val DIAGNOSTICS = "diagnostics"

    fun templateEditor(id: TemplateId, partId: Long = -1L) = "template/${id.name}?partId=$partId"
    fun photo(enclosure: Boolean = false) = "photo?enclosure=$enclosure"
    fun outline(partId: Long, enclosure: Boolean = false) = "outline/$partId?enclosure=$enclosure"
    fun enclosure(partId: Long = -1L) = "enclosure/$partId"
    fun part(partId: Long) = "part/$partId"
    fun test(iterationId: Long) = "test/$iterationId"
}
