package com.syll.domain.challenge

import java.security.MessageDigest

/**
 * iOS `ChallengeBoardOrdering`: the deterministic column order shared with the backend's replay. Each column is
 * sorted on its own, ascending by lowercase hex `SHA256(UTF8(boardSeed + "|" + correctCount + "|" + side + "|" + tileId))`,
 * ties broken by `tileId`. Computed for the first board (`correctCount` 0) and again after every correct match.
 * Must stay byte-for-byte equal to the server rule: check it against the shared test vectors.
 */
object ChallengeBoardOrdering {
    enum class Side(val raw: String) { WORD("word"), MEANING("meaning") }

    fun order(tileIds: List<String>, seed: String, correctCount: Int, side: Side): List<String> =
        tileIds
            .map { it to sortKey(seed, correctCount, side, it) }
            .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
            .map { it.first }

    fun sortKey(seed: String, correctCount: Int, side: Side, tileId: String): String {
        val input = "$seed|$correctCount|${side.raw}|$tileId"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * iOS `ChallengeBoardSlots`: where each tile sits on screen. The engine's column order is the replay rule; the screen
 * keeps tiles in place instead.
 */
object ChallengeBoardSlots {
    /**
     * A tile still on the board keeps its slot; a new tile takes a freed slot, top first. A slot with nothing left
     * to fill stays empty (null) so the tiles below don't move. [holding] tiles (just matched, still showing) keep
     * their slot too; new tiles that find no free slot wait until they are released.
     */
    fun reconcile(slots: List<String?>, order: List<String>, holding: Set<String> = emptySet()): List<String?> {
        val onBoard = order.toSet()
        val stays = onBoard + holding
        if (slots.none { it != null && it in onBoard }) return order

        val kept = slots.filterNotNull().toSet()
        val incoming = ArrayDeque(order.filter { it !in kept })
        val next: MutableList<String?> = slots.map { slot ->
            if (slot != null && slot in stays) slot else incoming.removeFirstOrNull()
        }.toMutableList()
        if (next.none { it != null && it in holding }) next.addAll(incoming)
        return next
    }
}
