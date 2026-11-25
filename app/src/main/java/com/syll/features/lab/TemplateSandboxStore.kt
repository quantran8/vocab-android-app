package com.syll.features.lab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.syll.domain.practice.LabTemplate
import com.syll.domain.practice.TaskTemplate
import com.syll.features.practice.PracticeSessionStore

/**
 * iOS `TemplateSandboxStore`: owns Practice Lab navigation. Each template plays through its own
 * [PracticeSessionStore] in sandbox mode, so the Lab shows exactly what a live session shows but never plans, commits
 * or touches learner progress.
 */
class TemplateSandboxStore(
    /** Loaded on first open: the bundled file is only read when the Lab is used. */
    private val loadTemplates: () -> List<LabTemplate>,
    val session: PracticeSessionStore,
) {
    val templates: List<LabTemplate> by lazy(loadTemplates)

    var isOpen by mutableStateOf(false)
        private set
    var activeTemplate by mutableStateOf<TaskTemplate?>(null)
        private set

    init {
        session.onSandboxEnd = { closeTemplate() }
    }

    fun openLab() {
        isOpen = true
    }

    fun closeLab() {
        closeTemplate()
        isOpen = false
    }

    /** Every entry starts a fresh run. */
    fun open(kind: TaskTemplate) {
        val template = templates.firstOrNull { it.id == kind } ?: return
        session.close()
        activeTemplate = kind
        session.startSandbox(template.tasks)
    }

    fun closeTemplate() {
        session.close()
        activeTemplate = null
    }
}
