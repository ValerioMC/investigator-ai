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
                "brescia-piazza-loggia-2022.txt",
                "news_article",
                List.of("p-002", "c-001", "pb-001", "k-001"),
                """
                Brescia, 4 maggio 2022. Il Comune di Brescia ha aggiudicato a Costruzioni Ferretti Srl
                il bando per la riqualificazione di Piazza Loggia (fase II) per un importo di
                1.200.000 euro. La delibera è stata approvata in Consiglio comunale con il voto
                favorevole del sindaco Luigi Conti, in carica dal 2016. Fonti interne all'amministrazione
                segnalano che la procedura di gara ha registrato una sola offerta valida. Il sindaco
                Conti, secondo la documentazione pubblicata sul portale istituzionale, non ha
                segnalato l'esistenza di legami familiari con soci della catena di controllo della
                società aggiudicataria. La Procura della Repubblica di Brescia ha avviato accertamenti
                preliminari sulla regolarità della procedura. Costruzioni Ferretti Srl risulta
                controllata al 100% dalla holding lussemburghese LuxHold SA.
                """
            ),
            new Fixture(
                "procura-brescia-ferretti-2023.txt",
                "court_record",
                List.of("p-002", "p-003", "c-001", "c-002", "pb-001"),
                """
                Tribunale di Brescia, fascicolo n. 4471/2023. La Procura ha iscritto nel registro
                degli indagati il dott. Luigi Conti, già sindaco del Comune di Brescia nel periodo
                2016-2024, per ipotesi di conflitto di interessi e omessa dichiarazione di
                partecipazioni indirette. Dagli atti emerge che il fratello dell'indagato,
                Mario Conti, detiene una quota del 15% della holding LuxHold SA con sede in
                Lussemburgo. LuxHold SA controlla il 100% di Costruzioni Ferretti Srl,
                aggiudicataria di due contratti pubblici del Comune di Brescia nel biennio
                2022-2023 per un valore complessivo di 1.650.000 euro. Il fascicolo include
                anche elementi relativi a Esposito Offshore Ltd, società lussemburghese non
                più attiva che figurava tra i soci della holding di controllo. La difesa
                contesta la qualificazione del rapporto fraterno come fattispecie rilevante
                ai fini della normativa anticorruzione.
                """
            ),
            new Fixture(
                "conti-dichiarazione-2022.txt",
                "official_filing",
                List.of("p-002"),
                """
                Dichiarazione patrimoniale del sindaco Luigi Conti per l'anno fiscale 2022,
                depositata il 30 giugno 2023 ai sensi dell'art. 14 del d.lgs. 33/2013.
                Il dichiarante non riporta partecipazioni in società commerciali. Non risultano
                indicazioni relative a quote possedute da parenti entro il secondo grado. La
                sezione relativa ai conflitti di interesse risulta compilata con la formula
                "nulla da dichiarare". Il documento è pubblicato sul portale Amministrazione
                Trasparente del Comune di Brescia.
                """
            ),
            new Fixture(
                "luxhold-bilancio-2022.txt",
                "company_filing",
                List.of("c-002", "c-001", "p-001", "p-003"),
                """
                LuxHold SA — Bilancio consolidato 2022 depositato presso il Registre de Commerce
                et des Sociétés del Lussemburgo. Capitale sociale: 1.250.000 euro. Soci dichiarati:
                Marco Ferretti (77%), Mario Conti (15%), Esposito Offshore Ltd (8%). L'unica
                controllata operativa è Costruzioni Ferretti Srl con sede in Italia, settore
                edilizia e opere pubbliche. Ricavi consolidati 2022: 4.300.000 euro, +38% rispetto
                al 2021. La crescita è prevalentemente legata ai contratti pubblici aggiudicati
                in Lombardia.
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
