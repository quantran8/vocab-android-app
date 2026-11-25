package com.syll.features.lab

import com.syll.domain.practice.LabLibrary
import com.syll.domain.practice.ResultLabFixture
import com.syll.domain.practice.TaskTemplate
import com.syll.features.auth.StubVocabApi
import com.syll.features.practice.PracticeSessionStore
import com.syll.features.practice.StubRecorder
import com.syll.features.practice.StubTranscriber
import com.syll.domain.practice.PracticeTask
import com.syll.domain.practice.TaskBody
import com.syll.domain.practice.TaskOption
import com.syll.domain.practice.TaskPrompt
import com.syll.network.ApiJson
import com.syll.platform.NoOpFeedback
import com.syll.platform.SilentAudioClips
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Port of iOS `LabLibraryTests`, plus the results lab file. */
class LabLibraryTest {
    private fun asset(name: String) = File("src/main/assets/$name").readText()

    @Test
    fun bundledLabCoversEveryBackendTemplate() {
        val library = LabLibrary.decode(ApiJson, asset("practice_lab.json"))
        library.validate()
        assertEquals(TaskTemplate.entries.toSet(), library.templates.map { it.id }.toSet())
    }

    @Test
    fun labTasksUseTheirTemplateAndHaveUniqueIds() {
        val library = LabLibrary.decode(ApiJson, asset("practice_lab.json"))
        val ids = library.templates.flatMap { template -> template.tasks.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        library.templates.forEach { template -> assertTrue(template.tasks.all { it.template == template.id }) }
    }

    @Test
    fun resultsLabDecodes() {
        val fixtures = ResultLabFixture.decode(ApiJson, asset("results_lab.json"))
        assertTrue(fixtures.isNotEmpty())
        assertTrue(fixtures.all { it.result.tiles != null })
    }

    @Test
    fun sandboxPlaysTasksWithoutCommitting() = runBlocking<Unit> {
        val api = StubVocabApi()
        val store = PracticeSessionStore(api, SilentAudioClips, StubRecorder(), NoOpFeedback, this, transcriber = StubTranscriber())
        var ended = false
        store.onSandboxEnd = { ended = true }
        val choice = PracticeTask(
            "t-choice", TaskTemplate.SELECT, "gloss", TaskPrompt.Text("clarify"), "?",
            TaskBody.Choice(listOf(TaskOption(7, "a"), TaskOption(3, "b")), null, 3),
        )
        store.startSandbox(listOf(choice))
        assertTrue(store.isSandbox)
        store.submitOption(3)
        store.advance()
        assertTrue(ended)
        assertTrue(api.commitBodies.isEmpty())
    }
}
