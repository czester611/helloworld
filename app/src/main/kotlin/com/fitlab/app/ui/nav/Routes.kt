package com.fitlab.app.ui.nav

import com.fitlab.core.templates.TemplateId

/** Trasy nawigacji (tekstowe). */
object Routes {
    const val HOME = "home"
    const val NEW = "new"
    const val PHOTO = "photo"
    const val TEMPLATES = "templates"
    const val TEMPLATE_EDITOR = "template/{templateId}?partId={partId}"
    const val OUTLINE = "outline/{partId}"
    const val PART = "part/{partId}"
    const val TEST = "test/{iterationId}"
    const val PROFILES = "profiles"
    const val COMPENSATION = "compensation"
    const val CALIBRATION = "calibration"
    const val MARKERS = "markers"
    const val DIAGNOSTICS = "diagnostics"

    fun templateEditor(id: TemplateId, partId: Long = -1L) = "template/${id.name}?partId=$partId"
    fun outline(partId: Long) = "outline/$partId"
    fun part(partId: Long) = "part/$partId"
    fun test(iterationId: Long) = "test/$iterationId"
}
