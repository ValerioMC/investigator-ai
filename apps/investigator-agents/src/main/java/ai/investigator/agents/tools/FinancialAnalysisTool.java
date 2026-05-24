package ai.investigator.agents.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Analyzes financial data (balance sheets, dividend flows) for anomalies.
 * Operates purely on data passed in by the LLM — no external DB calls.
 * The LLM is expected to pass structured JSON strings.
 */
@Component
public class FinancialAnalysisTool {

    private static final double SECTOR_AVERAGE_MARGIN = 0.08; // 8% construction sector average
    private static final double ANOMALY_THRESHOLD = 2.0; // flag if margin > 2x sector average

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Analyze a company's balance sheet for anomalies. Input must be a JSON string with " +
          "fields: companyName (string), years (array of {year, revenue, operatingProfit, " +
          "dividends}). Returns a structured analysis highlighting margin anomalies, " +
          "unusual dividend distributions, or revenue spikes.")
    public String analyzeBalanceSheet(String balanceSheetJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(balanceSheetJson, Map.class);
            String companyName = (String) data.get("companyName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> years = (List<Map<String, Object>>) data.get("years");

            if (years == null || years.isEmpty()) {
                return "No financial data provided for " + companyName;
            }

            StringBuilder sb = new StringBuilder("FINANCIAL ANALYSIS for ").append(companyName).append(":\n");
            boolean anomalyFound = false;

            for (var year : years) {
                int y = ((Number) year.get("year")).intValue();
                double revenue = ((Number) year.get("revenue")).doubleValue();
                double opProfit = ((Number) year.getOrDefault("operatingProfit", 0)).doubleValue();
                double dividends = ((Number) year.getOrDefault("dividends", 0)).doubleValue();

                double margin = revenue > 0 ? opProfit / revenue : 0;
                boolean highMargin = margin > SECTOR_AVERAGE_MARGIN * ANOMALY_THRESHOLD;
                boolean highDividends = dividends > opProfit * 0.9;

                sb.append("  ").append(y).append(": revenue=€").append(String.format("%,.0f", revenue))
                    .append(", margin=").append(String.format("%.1f%%", margin * 100));

                if (highMargin) {
                    sb.append(" [ANOMALY: margin ").append(String.format("%.1fx", margin / SECTOR_AVERAGE_MARGIN))
                        .append(" sector avg]");
                    anomalyFound = true;
                }
                if (dividends > 0) {
                    sb.append(", dividends=€").append(String.format("%,.0f", dividends));
                    if (highDividends) {
                        sb.append(" [ANOMALY: dividends exceed operating profit]");
                        anomalyFound = true;
                    }
                }
                sb.append("\n");
            }

            sb.append(anomalyFound
                ? "VERDICT: Anomalies detected — recommend deeper investigation.\n"
                : "VERDICT: No major anomalies detected within normal sector ranges.\n");

            return sb.toString();
        } catch (Exception e) {
            return "Failed to parse balance sheet data: " + e.getMessage() +
                "\nExpected JSON format: {\"companyName\":\"...\",\"years\":[{\"year\":2021,\"revenue\":1000000,\"operatingProfit\":80000,\"dividends\":0}]}";
        }
    }

    @Tool("Detect revenue spikes that correlate with public contract awards. Input is a JSON " +
          "string with fields: companyName, contractAwards (array of {year, contractAmount}), " +
          "revenues (array of {year, revenue}). Returns correlation analysis.")
    public String detectRevenueContractCorrelation(String dataJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(dataJson, Map.class);
            String companyName = (String) data.get("companyName");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> awards = (List<Map<String, Object>>) data.get("contractAwards");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> revenues = (List<Map<String, Object>>) data.get("revenues");

            if (awards == null || revenues == null) {
                return "Missing contractAwards or revenues data for " + companyName;
            }

            StringBuilder sb = new StringBuilder("CONTRACT-REVENUE CORRELATION for ")
                .append(companyName).append(":\n");
            int correlations = 0;

            for (var award : awards) {
                int awardYear = ((Number) award.get("year")).intValue();
                double awardAmt = ((Number) award.get("contractAmount")).doubleValue();

                double revAward = revenueForYear(revenues, awardYear);
                double revNext = revenueForYear(revenues, awardYear + 1);
                double revPrev = revenueForYear(revenues, awardYear - 1);

                if (revPrev > 0 && revAward > revPrev * 1.3) {
                    sb.append("  ").append(awardYear)
                        .append(": contract €").append(String.format("%,.0f", awardAmt))
                        .append(" | revenue jumped ").append(String.format("%.0f%%",
                            (revAward / revPrev - 1) * 100))
                        .append(" vs prior year [CORRELATED]\n");
                    correlations++;
                }
            }

            if (correlations == 0) {
                sb.append("  No strong correlation detected between contract awards and revenue spikes.\n");
            } else {
                sb.append("VERDICT: ").append(correlations)
                    .append(" correlation(s) detected — possible revenue concentration on public contracts.\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Failed to parse data: " + e.getMessage();
        }
    }

    private double revenueForYear(List<Map<String, Object>> revenues, int year) {
        return revenues.stream()
            .filter(r -> ((Number) r.get("year")).intValue() == year)
            .mapToDouble(r -> ((Number) r.get("revenue")).doubleValue())
            .findFirst()
            .orElse(0.0);
    }
}
