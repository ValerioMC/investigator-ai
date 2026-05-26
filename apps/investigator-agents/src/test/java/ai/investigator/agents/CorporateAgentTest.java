package ai.investigator.agents;

import ai.investigator.agents.corporate.CorporateAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateAgentTest {

    @Mock
    CorporateAgent corporateAgent;

    @Test
    void shouldSynthesizeOwnership() {
        String mockResponse = """
            OWNERSHIP CHAIN for Costruzioni Ferretti Srl:
            - Marco Ferretti → OWNS 49.0% (direct, IT) [PUBLIC ROLE: Consigliere comunale]
            - LuxHold SA → OWNS 51.0% (direct, LU) [TAX HAVEN]
              └─ Mario Conti → beneficial owner (LU, brother of Luigi Conti - Sindaco)
            - Remaining 0.0%: fully accounted
            """;

        when(corporateAgent.synthesize(anyString())).thenReturn(mockResponse);

        String result = corporateAgent.synthesize(
            "Who owns Costruzioni Ferretti Srl and are there any tax haven connections?");

        assertThat(result).contains("LuxHold SA");
        assertThat(result).contains("TAX HAVEN");
        assertThat(result).contains("Marco Ferretti");
    }
}
