package com.tcg.bot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportListParserTest {

    @Test
    void readsQuantitySetNumberAndFoil() {
        var line = ImportListParser.parseImportLine("4 Sol Ring (CMM) 0410");

        assertThat(line.quantity()).isEqualTo(4);
        assertThat(line.name()).isEqualTo("Sol Ring");
        assertThat(line.setCode()).isEqualTo("CMM");
        assertThat(line.collectorNumber()).isEqualTo("410");
        assertThat(line.foil()).isNull();

        var foil = ImportListParser.parseImportLine("Sol Ring foil");
        assertThat(foil.name()).isEqualTo("Sol Ring");
        assertThat(foil.foil()).isTrue();
    }

    @Test
    void foilAtTheEndKeepsTheCollectorNumber() {
        for (String text : new String[]{
                "1 Sol Ring (CMM) 410 foil",
                "1 Sol Ring (CMM) 410 FOIL",
                "1 Sol Ring (CMM) 410 (foil)",
                "1 Sol Ring [CMM] #410 [Foil]",
                "1 Sol Ring (CMM) 410 *F*",
                "1 Sol Ring foil (CMM) 410"}) {
            var line = ImportListParser.parseImportLine(text);
            assertThat(line.name()).as(text).isEqualTo("Sol Ring");
            assertThat(line.setCode()).as(text).isEqualToIgnoringCase("CMM");
            assertThat(line.collectorNumber()).as(text).isEqualTo("410");
            assertThat(line.foil()).as(text).isTrue();
        }

        var nonFoil = ImportListParser.parseImportLine("2 Counterspell (CMM) 81 non-foil");
        assertThat(nonFoil.name()).isEqualTo("Counterspell");
        assertThat(nonFoil.collectorNumber()).isEqualTo("81");
        assertThat(nonFoil.foil()).isFalse();
    }

    @Test
    void trailingQuantityAndCommentsAndBlankLines() {
        assertThat(ImportListParser.parseImportLine("Counterspell x3").quantity()).isEqualTo(3);
        assertThat(ImportListParser.parseImportLine("// sideboard")).isNull();
        assertThat(ImportListParser.parseImportLine("   ")).isNull();
    }

    @Test
    void mergesRepeatedLines() {
        var lines = ImportListParser.parseAndMergeImportLines("1 Sol Ring (CMM) 410\n2 sol ring (cmm) 410\n1 Counterspell");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).quantity()).isEqualTo(3);
        assertThat(lines.get(0).duplicateCount()).isEqualTo(1);
    }

    @Test
    void splitFacesAreSearchedSeparately() {
        assertThat(ImportListParser.importNameCandidates("Fire // Ice"))
                .containsExactly("fire / ice", "fire", "ice");
    }
}
