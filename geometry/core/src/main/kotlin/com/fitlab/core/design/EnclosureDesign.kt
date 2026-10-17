package com.fitlab.core.design

import com.fitlab.core.enclosure.EnclosureSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Obudowa (dno + pokrywa) dopasowana do obrysu obiektu. */
@Serializable
@SerialName("enclosure")
data class EnclosureDesign(val spec: EnclosureSpec) : PartDesign
