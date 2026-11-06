package io.github.lorencmarkja.tradelens.proposal;

import java.util.Arrays;

/** The only offer fields a proposal may change. */
public enum ProposalField {
    SALE_PRICE("sale_price", "Sale price", "USD/mt"),
    LOGISTICS("logistics_cost", "Logistics (transport & handling)", "USD/mt"),
    FINANCING_DAYS("financing_days", "Financing days", "days"),
    QUOTE_VALID_UNTIL("quote_valid_until", "Quote valid until", "");

    private final String extractionName;
    private final String label;
    private final String unit;

    ProposalField(String extractionName, String label, String unit) {
        this.extractionName = extractionName;
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public static ProposalField fromExtraction(String name) {
        return Arrays.stream(values()).filter(f -> f.extractionName.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported field " + name));
    }
}
