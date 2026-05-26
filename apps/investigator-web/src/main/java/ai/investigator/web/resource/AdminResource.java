package ai.investigator.web.resource;

import ai.investigator.vector.chunker.DocumentChunker;
import ai.investigator.vector.chunker.DocumentChunker.DocumentChunk;
import ai.investigator.vector.repository.VectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demo-only admin endpoints. Seeds Qdrant with a deterministic fixture set
 * aligned to the Ferretti scenario in scripts/seed-data.sh — so the document
 * search agent can return grounded evidence without scraping live sources.
 */
@RestController
@RequestMapping("/api/web/v1/admin")
public class AdminResource {

    private static final Logger log = LoggerFactory.getLogger(AdminResource.class);

    private final DocumentChunker chunker;
    private final VectorRepository vectors;

    public AdminResource(DocumentChunker chunker, VectorRepository vectors) {
        this.chunker = chunker;
        this.vectors = vectors;
    }

    @PostMapping("/seed-documents")
    public ResponseEntity<Map<String, Object>> seedDocuments() {
        List<Fixture> fixtures = List.of(
            new Fixture(
                "brescia-loggia-square-2022.txt",
                "news_article",
                List.of("p-002", "c-001", "pb-001", "k-001"),
                """
                Brescia, 4 May 2022. The City of Brescia awarded Ferretti Construction Ltd
                the tender for the Loggia Square Redevelopment (Phase II) for an amount of
                1,200,000 euros. The resolution was approved in the City Council with the
                favourable vote of Mayor Luigi Conti, in office since 2016. Internal
                administration sources report that the tender procedure recorded only one
                valid bid. Mayor Conti, according to the documentation published on the
                institutional portal, did not declare the existence of family ties with
                shareholders in the control chain of the winning company. The Brescia
                Public Prosecutor's Office has launched preliminary checks on the regularity
                of the procedure. Ferretti Construction Ltd is 100% controlled by the
                Luxembourg holding LuxHold SA.
                """
            ),
            new Fixture(
                "brescia-prosecutor-ferretti-2023.txt",
                "court_record",
                List.of("p-002", "p-003", "c-001", "c-002", "pb-001"),
                """
                Brescia Court, case file no. 4471/2023. The Public Prosecutor has registered
                Mr Luigi Conti, former Mayor of the City of Brescia in the 2016-2024 period,
                in the suspects register on alleged charges of conflict of interest and
                failure to disclose indirect equity stakes. From the records it emerges that
                the suspect's brother, Mario Conti, holds a 15% stake in the LuxHold SA
                holding company based in Luxembourg. LuxHold SA controls 100% of Ferretti
                Construction Ltd, awarded two public contracts by the City of Brescia in
                the 2022-2023 biennium for a total value of 1,650,000 euros. The file also
                includes elements relating to Esposito Offshore Ltd, a Luxembourg company
                no longer active that featured among the shareholders of the controlling
                holding. The defence contests the qualification of the brotherly relationship
                as a relevant scenario for the purposes of anti-corruption legislation.
                """
            ),
            new Fixture(
                "conti-statement-2022.txt",
                "official_filing",
                List.of("p-002"),
                """
                Asset declaration of Mayor Luigi Conti for fiscal year 2022, filed on 30 June
                2023 pursuant to art. 14 of Legislative Decree 33/2013. The declarant reports
                no shareholdings in commercial companies. There are no indications relating to
                stakes held by relatives within the second degree. The section relating to
                conflicts of interest is filled in with the formula "nothing to declare". The
                document is published on the Transparent Administration portal of the City of
                Brescia.
                """
            ),
            new Fixture(
                "luxhold-financial-statements-2022.txt",
                "company_filing",
                List.of("c-002", "c-001", "p-001", "p-003"),
                """
                LuxHold SA — Consolidated financial statements 2022 filed with the Registre
                de Commerce et des Sociétés of Luxembourg. Share capital: 1,250,000 euros.
                Declared shareholders: Marco Ferretti (77%), Mario Conti (15%), Esposito
                Offshore Ltd (8%). The only operating subsidiary is Ferretti Construction Ltd
                based in Italy, construction and public works sector. Consolidated revenues
                2022: 4,300,000 euros, +38% compared to 2021. Growth is predominantly linked
                to public contracts awarded in Lombardy.
                """
            )
        );

        int totalChunks = 0;
        List<DocumentChunk> all = new ArrayList<>();
        for (Fixture f : fixtures) {
            List<DocumentChunk> chunks = chunker.chunk(
                f.text.replaceAll("\\s+", " ").trim(),
                f.source,
                f.sourceType,
                f.entityIds
            );
            totalChunks += chunks.size();
            all.addAll(chunks);
        }

        log.info("Indexing {} chunks from {} fixture documents", totalChunks, fixtures.size());
        vectors.indexChunks(all);

        return ResponseEntity.ok(Map.of(
            "fixtures", fixtures.size(),
            "chunks", totalChunks,
            "status", "indexed"
        ));
    }

    private record Fixture(String source, String sourceType, List<String> entityIds, String text) {}
}
