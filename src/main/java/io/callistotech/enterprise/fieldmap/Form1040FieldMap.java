package io.callistotech.enterprise.fieldmap;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Field map for IRS Form 1040 (U.S. Individual Income Tax Return).
 * Keys are raw Azure DI key variants; values are canonical field names.
 * Applies to all tax years ("any") — extend with year-specific subclasses if line numbers change.
 */
@Component
public class Form1040FieldMap implements FieldMap {

    private static final Map<String, String> ENTRIES = Map.ofEntries(
            // Line 1a - Total wages, salaries, tips
            Map.entry("1a", "wages_salaries_tips"),
            Map.entry("Line 1a", "wages_salaries_tips"),
            Map.entry("Wages, salaries, tips, etc.", "wages_salaries_tips"),
            Map.entry("Total amount from Schedule 1", "wages_salaries_tips"),

            // Line 2b - Taxable interest
            Map.entry("2b", "taxable_interest"),
            Map.entry("Line 2b", "taxable_interest"),
            Map.entry("Taxable interest", "taxable_interest"),

            // Line 3b - Ordinary dividends
            Map.entry("3b", "ordinary_dividends"),
            Map.entry("Line 3b", "ordinary_dividends"),
            Map.entry("Ordinary dividends", "ordinary_dividends"),

            // Line 4b - IRA distributions taxable amount
            Map.entry("4b", "ira_distributions_taxable"),
            Map.entry("Line 4b", "ira_distributions_taxable"),
            Map.entry("IRA distributions taxable amount", "ira_distributions_taxable"),

            // Line 5b - Pension and annuities taxable amount
            Map.entry("5b", "pensions_annuities_taxable"),
            Map.entry("Line 5b", "pensions_annuities_taxable"),
            Map.entry("Pensions and annuities taxable amount", "pensions_annuities_taxable"),

            // Line 6b - Social security benefits taxable
            Map.entry("6b", "social_security_taxable"),
            Map.entry("Line 6b", "social_security_taxable"),
            Map.entry("Social security benefits taxable", "social_security_taxable"),

            // Line 7 - Capital gain or loss
            Map.entry("7", "capital_gain_or_loss"),
            Map.entry("Line 7", "capital_gain_or_loss"),
            Map.entry("Capital gain or (loss)", "capital_gain_or_loss"),

            // Line 8 - Additional income from Schedule 1
            Map.entry("8", "additional_income_schedule1"),
            Map.entry("Line 8", "additional_income_schedule1"),
            Map.entry("Additional income from Schedule 1", "additional_income_schedule1"),

            // Line 9 - Total income
            Map.entry("9", "total_income"),
            Map.entry("Line 9", "total_income"),
            Map.entry("Total income", "total_income"),

            // Line 10 - Adjustments to income from Schedule 1
            Map.entry("10", "adjustments_to_income"),
            Map.entry("Line 10", "adjustments_to_income"),
            Map.entry("Adjustments to income", "adjustments_to_income"),

            // Line 11 - Adjusted gross income
            Map.entry("11", "adjusted_gross_income"),
            Map.entry("Line 11", "adjusted_gross_income"),
            Map.entry("Adjusted gross income", "adjusted_gross_income"),
            Map.entry("AGI", "adjusted_gross_income"),

            // Line 12 - Standard or itemized deduction
            Map.entry("12", "standard_or_itemized_deduction"),
            Map.entry("Line 12", "standard_or_itemized_deduction"),
            Map.entry("Standard deduction or itemized deductions", "standard_or_itemized_deduction"),

            // Line 15 - Taxable income
            Map.entry("15", "taxable_income"),
            Map.entry("Line 15", "taxable_income"),
            Map.entry("Taxable income", "taxable_income"),

            // Line 16 - Tax
            Map.entry("16", "total_tax_liability"),
            Map.entry("Line 16", "total_tax_liability"),
            Map.entry("Tax", "total_tax_liability"),

            // Line 24 - Total tax
            Map.entry("24", "total_tax"),
            Map.entry("Line 24", "total_tax"),
            Map.entry("Total tax", "total_tax"),

            // Line 25a - Federal income tax withheld (W-2)
            Map.entry("25a", "federal_tax_withheld_w2"),
            Map.entry("Line 25a", "federal_tax_withheld_w2"),
            Map.entry("Federal income tax withheld from Form W-2", "federal_tax_withheld_w2"),

            // Line 25b - Federal income tax withheld (1099)
            Map.entry("25b", "federal_tax_withheld_1099"),
            Map.entry("Line 25b", "federal_tax_withheld_1099"),
            Map.entry("Federal income tax withheld from Form 1099", "federal_tax_withheld_1099"),

            // Line 33 - Total payments
            Map.entry("33", "total_payments"),
            Map.entry("Line 33", "total_payments"),
            Map.entry("Total payments", "total_payments"),

            // Line 34 - Overpayment / refund
            Map.entry("34", "overpayment"),
            Map.entry("Line 34", "overpayment"),
            Map.entry("Amount overpaid", "overpayment"),

            // Line 37 - Amount owed
            Map.entry("37", "amount_owed"),
            Map.entry("Line 37", "amount_owed"),
            Map.entry("Amount you owe", "amount_owed"),

            // Filing status
            Map.entry("Filing status", "filing_status"),
            Map.entry("Single", "filing_status"),
            Map.entry("Married filing jointly", "filing_status"),
            Map.entry("Married filing separately", "filing_status"),
            Map.entry("Head of household", "filing_status"),

            // Dependents
            Map.entry("Dependents", "dependents_count"),
            Map.entry("Number of dependents", "dependents_count")
    );

    @Override
    public String name() {
        return "form_1040";
    }

    @Override
    public String taxYear() {
        return "any";
    }

    @Override
    public Map<String, String> entries() {
        return ENTRIES;
    }
}
