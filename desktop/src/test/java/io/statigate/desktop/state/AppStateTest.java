/*
 * Copyright 2026 The Statigate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.statigate.desktop.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.statigate.core.Document;
import io.statigate.pipeline.PipelineResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AppState} uses only plain JavaFX property/collection classes, none of which need the
 * graphics toolkit initialized, so these run as ordinary JUnit tests with no FX runtime involved.
 */
class AppStateTest {

    @Test
    void startsIdle() {
        AppState state = new AppState();
        assertEquals(AnalysisPhase.IDLE, state.phase());
        assertEquals("", state.fileNameProperty().get());
        assertTrue(state.completedStages().isEmpty());
        assertEquals(0.0, state.stageProgressProperty().get());
    }

    @Test
    void startAnalyzingClearsPreviousResultAndError() {
        AppState state = new AppState();
        state.fail("boom");
        state.startAnalyzing("contract.pdf");

        assertEquals(AnalysisPhase.ANALYZING, state.phase());
        assertEquals("contract.pdf", state.fileNameProperty().get());
        assertEquals("", state.errorMessageProperty().get());
        assertNull(state.result());
        assertTrue(state.completedStages().isEmpty());
    }

    @Test
    void reportStageCompleteAccumulatesInOrderAndIgnoresDuplicates() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");

        state.reportStageComplete("ingestion");
        state.reportStageComplete("ingestion"); // duplicate, must not double-count
        state.reportStageComplete("extraction");

        assertEquals(List.of("ingestion", "extraction"), state.completedStages());
        assertEquals(0.5, state.stageProgressProperty().get(), 1e-9);
    }

    @Test
    void reportStageCompleteIgnoresUnknownStageNames() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        state.reportStageComplete("teleportation");
        assertTrue(state.completedStages().isEmpty());
    }

    @Test
    void reportStageCompleteIgnoresNull() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        state.reportStageComplete(null);
        assertTrue(state.completedStages().isEmpty());
    }

    @Test
    void completeFillsAllStagesEvenIfSomeWereMissedAndSetsResult() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        state.reportStageComplete("ingestion");

        PipelineResult result = samplePipelineResult();
        state.complete(result);

        assertEquals(AnalysisPhase.COMPLETE, state.phase());
        assertEquals(result, state.result());
        assertEquals(4, state.completedStages().size());
        assertEquals(1.0, state.stageProgressProperty().get(), 1e-9);
    }

    @Test
    void failSetsMessageAndClearsResult() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        state.complete(samplePipelineResult());

        state.fail("The file could not be read.");

        assertEquals(AnalysisPhase.FAILED, state.phase());
        assertEquals("The file could not be read.", state.errorMessageProperty().get());
        assertNull(state.result());
    }

    @Test
    void failFallsBackToAGenericMessageWhenGivenNoneOrBlank() {
        AppState state = new AppState();
        state.fail(null);
        assertFalse(state.errorMessageProperty().get().isBlank());
        state.fail("   ");
        assertFalse(state.errorMessageProperty().get().isBlank());
    }

    @Test
    void resetReturnsToIdleFromAnyPhase() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        state.complete(samplePipelineResult());

        state.reset();

        assertEquals(AnalysisPhase.IDLE, state.phase());
        assertEquals("", state.fileNameProperty().get());
        assertNull(state.result());
        assertTrue(state.completedStages().isEmpty());
    }

    // ---- regression coverage for a real, shipped bug: a phase-change listener (this is exactly
    // how DashboardPane decides what to render) must see fully-consistent state, not whichever
    // fields happened to be updated before `phase` was set. ErrorBanner and ReportView each take
    // their data as a one-time constructor argument read at the moment such a listener fires, so
    // if `phase` changes before the data does, they are built from stale data - here, silently
    // from an empty error message, which is exactly what a user saw as a blank error banner.

    @Test
    void phaseListenerSeesTheFailureMessageAlreadySetWhenItFires() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf"); // leaves errorMessage at "" beforehand
        String[] seenInsideListener = { "not set" };
        state.phaseProperty().addListener((obs, oldPhase, newPhase) -> {
            if (newPhase == AnalysisPhase.FAILED) {
                seenInsideListener[0] = state.errorMessageProperty().get();
            }
        });

        state.fail("The document could not be parsed.");

        assertEquals("The document could not be parsed.", seenInsideListener[0]);
    }

    @Test
    void phaseListenerSeesTheResultAlreadySetWhenItFires() {
        AppState state = new AppState();
        state.startAnalyzing("contract.pdf");
        PipelineResult[] seenInsideListener = { null };
        state.phaseProperty().addListener((obs, oldPhase, newPhase) -> {
            if (newPhase == AnalysisPhase.COMPLETE) {
                seenInsideListener[0] = state.result();
            }
        });

        PipelineResult result = samplePipelineResult();
        state.complete(result);

        assertEquals(result, seenInsideListener[0]);
    }

    @Test
    void phaseListenerSeesTheFileNameAlreadySetWhenItFires() {
        AppState state = new AppState();
        String[] seenInsideListener = { "not set" };
        state.phaseProperty().addListener((obs, oldPhase, newPhase) -> {
            if (newPhase == AnalysisPhase.ANALYZING) {
                seenInsideListener[0] = state.fileNameProperty().get();
            }
        });

        state.startAnalyzing("lease-agreement.pdf");

        assertEquals("lease-agreement.pdf", seenInsideListener[0]);
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }

    private static PipelineResult samplePipelineResult() {
        Document document = new Document("/tmp/contract.pdf", "application/pdf", 1, "clean text", List.of());
        return new PipelineResult(document, List.of(), List.of(), List.of(), List.of(),
                Map.of("total", 42L), Map.of("extraction", "keyword-only"));
    }
}
