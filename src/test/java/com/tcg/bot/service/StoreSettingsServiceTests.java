package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSettingsServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void keepsCoreAndModulesTutorialStateSeparate() throws Exception {
        StoreSettingsService service = new StoreSettingsService("sheet-id", tempDir.toString());

        service.completeTutorial();

        StoreSettingsService reloaded = new StoreSettingsService("sheet-id", tempDir.toString());
        assertThat(reloaded.isTutorialCompleted()).isTrue();
        assertThat(reloaded.isModulesTutorialCompleted()).isFalse();

        reloaded.completeModulesTutorial();
        StoreSettingsService reloadedWithModules = new StoreSettingsService("sheet-id", tempDir.toString());
        assertThat(reloadedWithModules.isModulesTutorialCompleted()).isTrue();

        reloadedWithModules.resetTutorial();
        StoreSettingsService reset = new StoreSettingsService("sheet-id", tempDir.toString());
        assertThat(reset.isTutorialCompleted()).isFalse();
        assertThat(reset.isModulesTutorialCompleted()).isFalse();
    }
}
