package com.bblvn.composebaseproject.data.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row/section of the catalog.
 *
 * Array order is meaningful and MUST be preserved — category ids are not sorted ascending and
 * "Trending 🔥" is already first (Risks #10).
 */
@Serializable
data class TemplateCategory(
    @SerialName("category_id") val categoryId: Int = 0,
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("components") val templates: List<Template> = emptyList(),
)

/** Root object of the catalog JSON. */
@Serializable
data class Catalog(
    @SerialName("categories") val categories: List<TemplateCategory> = emptyList(),
)
