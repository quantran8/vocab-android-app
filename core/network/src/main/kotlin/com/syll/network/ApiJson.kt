package com.syll.network

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for the backend (iOS `APIClient.decoder`).
 *
 * Unknown keys are ignored and missing optional fields decode to their defaults, so an older or newer backend
 * still works. Where the contract wants a hard failure (an unknown task template or variant fails the whole plan)
 * the model's own serializer throws.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = false
    encodeDefaults = true
}

