package ai.investigator.api;

import ai.investigator.agents.supervisor.SupervisorAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end test for the investigation API.
 * Stubs the SupervisorAgent (no live LLM) to return a canned report.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvestigationE2EIT {

    @MockBean
    SupervisorAgent supervisor;

    @Autowired
    MockMvc mockMvc;

    private static final String CANNED_REPORT = """
        {
          "query": "Chi controlla Costruzioni Ferretti Srl e ci sono conflitti di interessi?",
          "summary": "Marco Ferretti detiene il 49% di Costruzioni Ferretti Srl.",
          "findings": [
            {
              "claim": "Marco Ferretti ha votato su contratti pubblici senza dichiarare il conflitto di interessi",
              "confidence": "HIGH",
              "evidence": ["GraphPath: Ferretti->HELD_PUBLIC_ROLE->Comune di Brescia"],
              "agentSource": "PersonProfileAgent"
            }
          ],
          "entityMap": {
            "persons": ["Marco Ferretti"],
            "companies": ["Costruzioni Ferretti Srl"],
            "contracts": []
          },
          "recommendedFollowUps": ["Verificare le dichiarazioni patrimoniali di Marco Ferretti"],
          "disclaimer": "This report is a journalistic aid. Claims require editorial verification before publication."
        }
        """;

    @BeforeEach
    void setup() {
        when(supervisor.investigate(anyString())).thenReturn(CANNED_REPORT);
    }

    @Test
    void investigate_shouldReturn200WithReport() throws Exception {
        mockMvc.perform(post("/api/v1/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "Chi controlla Costruzioni Ferretti Srl?",
                      "depth": 3,
                      "focus_entities": ["Marco Ferretti"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.query").exists())
            .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void investigate_shouldReturn400ForBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/investigate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "",
                      "depth": 3,
                      "focus_entities": []
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void investigationStats_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/investigation-stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.as_of").exists());
    }

    @Test
    void healthActuator_shouldExist() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isAnyOf(200, 503));
    }
}
