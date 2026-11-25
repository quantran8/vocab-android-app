package com.syll.features.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize

/**
 * Where each tile's place is, relative to the task's content (iOS anchor preferences / `reportFrame`), as snapshot
 * state so the tiles drawn above follow layout changes. Tiles are drawn in one overlay and only fly between these
 * frames (iOS `matchedGeometryEffect` / `.position`), so no copy of a tile is left behind mid-move.
 */
class TileFrames<K : Any> {
    private val coordinates = mutableMapOf<K, LayoutCoordinates>()
    private val frames = mutableStateMapOf<K, Rect>()

    /** Children may be placed before the container reports in, so every frame is measured again from here. */
    var container: LayoutCoordinates? = null
        set(value) {
            field = value
            coordinates.keys.toList().forEach(::update)
        }

    operator fun get(key: K): Rect? = frames[key]

    fun place(key: K, layout: LayoutCoordinates) {
        coordinates[key] = layout
        update(key)
    }

    fun forget(key: K) {
        coordinates.remove(key)
        frames.remove(key)
    }

    private fun update(key: K) {
        val container = container?.takeIf { it.isAttached } ?: return
        val layout = coordinates[key]?.takeIf { it.isAttached } ?: return
        val frame = Rect(container.localPositionOf(layout, Offset.Zero), layout.size.toSize())
        if (frames[key] != frame) frames[key] = frame
    }
}

/**
 * A tile's top-left, springing to each new [target] with [spec]. The first known place is taken at once, so the
 * step's own entrance moves the tiles with it and they never fly in. While [held] is set (a drag), the tile follows
 * it without animation and springs from there once released.
 */
@Composable
fun rememberFlightPosition(target: Offset?, spec: AnimationSpec<Offset>, held: Offset? = null): Offset? {
    val animatable = remember { mutableStateOf<Animatable<Offset, AnimationVector2D>?>(null) }
    LaunchedEffect(target, held) {
        val goal = held ?: target ?: return@LaunchedEffect
        val current = animatable.value
        when {
            current == null -> animatable.value = Animatable(goal, Offset.VectorConverter)
            held != null -> current.snapTo(held)
            else -> current.animateTo(goal, spec)
        }
    }
    return animatable.value?.value
}
