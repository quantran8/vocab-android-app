package com.syll.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** iOS `CEFRLevel`. The wire value is the code ("B1"). */
@Serializable
enum class CefrLevel(val code: String) {
    @SerialName("A1") A1("A1"),
    @SerialName("A2") A2("A2"),
    @SerialName("B1") B1("B1"),
    @SerialName("B2") B2("B2"),
    @SerialName("C1") C1("C1"),
    @SerialName("C2") C2("C2"),
}
