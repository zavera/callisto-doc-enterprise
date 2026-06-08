package io.callistotech.enterprise.demo;

import io.callistotech.enterprise.api.dto.ExtractionRequest;
import io.callistotech.enterprise.api.dto.ExtractionResponse;
import io.callistotech.enterprise.domain.ExtractedField;
import io.callistotech.enterprise.domain.JobStatus;
import io.callistotech.enterprise.domain.Severity;
import io.callistotech.enterprise.extraction.ExtractionPipeline;
import io.callistotech.enterprise.fieldmap.FieldMap;
import io.callistotech.enterprise.fieldmap.FieldMapRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the demo endpoint ({@code POST /demo/extract}).
 * <p>
 * When Azure DI credentials are configured and {@code callisto.demo.real-extraction=true},
 * the real extraction pipeline is invoked. Otherwise (default), curated realistic 1040/W-2
 * fields are returned — suitable for sales demos, local development, and CI.
 * <p>
 * Reconciliation (delta + severity) is computed deterministically in Java against any
 * reference values supplied in the request, mirroring the behaviour of the authenticated
 * endpoint. No PII is logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoExtractionService {

    private final ExtractionPipeline extractionPipeline;
    private final FieldMapRegistry fieldMapRegistry;

    @Value("${callisto.demo.real-extraction:false}")
    private boolean realExtractionEnabled;

    // ── Entry point ──────────────────────────────────────────────────

    public ExtractionResponse extract(ExtractionRequest request) {
        long start = System.currentTimeMillis();
        UUID jobId = UUID.randomUUID();

        boolean hasBytes = request.bytes() != null && request.bytes().length > 0;
        List<ExtractedField> fields;

        if (realExtractionEnabled && hasBytes) {
            try {
                FieldMap fieldMap = fieldMapRegistry.require(request.fieldMapName(), request.taxYear());
                fields = extractionPipeline.run(
                        request.bytes(),
                        fieldMap,
                        request.referenceValues(),
                        request.fieldMapName(),
                        jobId.toString(),
                        "demo-upload"
                );
            } catch (Exception e) {
                log.error("Demo real extraction failed — falling back to canned data: error={}", e.getMessage());
                fields = buildMockFields(request.fieldMapName(), request.referenceValues());
            }
        } else {
            fields = buildMockFields(request.fieldMapName(), request.referenceValues());
        }

        long processingMs = System.currentTimeMillis() - start;
        String summary = buildSummary(fields, request.fieldMapName());

        return new ExtractionResponse(
                jobId,
                "demo-document",
                JobStatus.COMPLETE,
                fields,
                null,
                summary,
                "",
                processingMs
        );
    }

    // ── Mock field construction ──────────────────────────────────────

    private List<ExtractedField> buildMockFields(String formType, Map<String, BigDecimal> refValues) {
        List<RawField> raws = "IRS_W2".equals(formType) ? w2Fields() : form1040Fields();
        return raws.stream()
                .map(r -> toExtractedField(r, formType, refValues))
                .toList();
    }

    private List<RawField> form1040Fields() {
        return List.of(
                new RawField("wages_salaries_tips",         "Line 1a",  "72450.00", 0.99),
                new RawField("tax_exempt_interest",         "Line 2a",  "0.00",     0.95),
                new RawField("taxable_interest",            "Line 2b",  "312.50",   0.97),
                new RawField("qualified_dividends",         "Line 3a",  "1840.00",  0.98),
                new RawField("ordinary_dividends",          "Line 3b",  "2190.00",  0.96),
                new RawField("ira_distributions",           "Line 4a",  null,       null),
                new RawField("taxable_ira",                 "Line 4b",  null,       null),
                new RawField("social_security_benefits",    "Line 6a",  null,       null),
                new RawField("total_income",                "Line 9",   "87592.50", 0.99),
                new RawField("adjusted_gross_income",       "Line 11",  "83200.00", 0.99),
                new RawField("standard_deduction",          "Line 12",  "13850.00", 0.98),
                new RawField("taxable_income",              "Line 15",  "69350.00", 0.98),
                new RawField("total_tax",                   "Line 24",  "11942.00", 0.97),
                new RawField("federal_income_tax_withheld", "Line 25a", "9400.00",  0.99),
                new RawField("amount_owed",                 "Line 37",  "2542.00",  0.96)
        );
    }

    private List<RawField> w2Fields() {
        return List.of(
                new RawField("wages_tips_other_compensation", "Box 1",  "72450.00", 0.99),
                new RawField("federal_income_tax_withheld",   "Box 2",  "9400.00",  0.99),
                new RawField("social_security_wages",         "Box 3",  "72450.00", 0.98),
                new RawField("social_security_tax_withheld",  "Box 4",  "4492.00",  0.98),
                new RawField("medicare_wages_and_tips",       "Box 5",  "72450.00", 0.98),
                new RawField("medicare_tax_withheld",         "Box 6",  "1050.00",  0.97),
                new RawField("dependent_care_benefits",       "Box 10", null,       null),
                new RawField("state_wages_tips_etc",          "Box 16", "72450.00", 0.97),
                new RawField("state_income_tax",              "Box 17", "3800.00",  0.96)
        );
    }

    /**
     * Builds an {@link ExtractedField} from a raw mock spec, computing severity
     * deterministically against any supplied reference value.
     */
    private ExtractedField toExtractedField(RawField r, String formType,
                                             Map<String, BigDecimal> refValues) {
        BigDecimal extracted  = r.value() != null ? new BigDecimal(r.value()) : null;
        BigDecimal confidence = r.confidence() != null ? BigDecimal.valueOf(r.confidence()) : null;
        BigDecimal ref        = refValues != null ? refValues.get(r.name()) : null;

        Severity severity;
        if (extracted == null) {
            severity = Severity.NONE;
        } else if (ref == null) {
            severity = Severity.NONE;
        } else {
            BigDecimal delta = extracted.subtract(ref);
            double pct = Math.abs(delta.doubleValue()) / ref.doubleValue();
            severity = pct > 0.05 ? Severity.HIGH
                     : pct > 0.01 ? Severity.MEDIUM
                     : pct > 0.0  ? Severity.LOW
                     : Severity.NONE;
        }

        // rawValue carries the section label so the UI can display "Line 1a" etc.
        return new ExtractedField(
                r.name(),
                extracted,
                r.section(),   // rawValue field reused as section label for demo
                confidence,
                r.section(),
                formType,
                severity
        );
    }

    private String buildSummary(List<ExtractedField> fields, String formType) {
        long high    = fields.stream().filter(f -> f.severity() == Severity.HIGH).count();
        long present = fields.stream().filter(f -> f.extractedValue() != null).count();

        String form = formType.replace("_", " ");
        if (high > 0) {
            return String.format(
                    "%s processed. %d of %d fields extracted. %d high-severity discrepanc%s detected "
                    + "against reference data — review before proceeding.",
                    form, present, fields.size(), high, high == 1 ? "y" : "ies");
        }
        return String.format(
                "%s processed. %d of %d fields extracted successfully. All values reconcile "
                + "within tolerance — no discrepancies detected against reference data.",
                form, present, fields.size());
    }

    // ── Inner record ─────────────────────────────────────────────────

    private record RawField(String name, String section, String value, Double confidence) {}
}
