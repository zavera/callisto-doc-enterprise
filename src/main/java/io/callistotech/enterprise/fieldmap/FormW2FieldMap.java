package io.callistotech.enterprise.fieldmap;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Field map for IRS Form W-2 (Wage and Tax Statement).
 * Covers standard box labels and newline-key variants produced by Azure DI.
 */
@Component
public class FormW2FieldMap implements FieldMap {

    private static final Map<String, String> ENTRIES = Map.ofEntries(
            // Box 1 - Wages, tips, other compensation
            Map.entry("1", "w2_box1_wages"),
            Map.entry("Box 1", "w2_box1_wages"),
            Map.entry("Wages, tips, other comp.", "w2_box1_wages"),
            Map.entry("Wages, tips, other compensation", "w2_box1_wages"),
            Map.entry("1\nWages, tips, other comp.", "w2_box1_wages"),

            // Box 2 - Federal income tax withheld
            Map.entry("2", "w2_box2_federal_withheld"),
            Map.entry("Box 2", "w2_box2_federal_withheld"),
            Map.entry("Federal income tax withheld", "w2_box2_federal_withheld"),
            Map.entry("2\nFederal income tax withheld", "w2_box2_federal_withheld"),

            // Box 3 - Social security wages
            Map.entry("3", "w2_box3_ss_wages"),
            Map.entry("Box 3", "w2_box3_ss_wages"),
            Map.entry("Social security wages", "w2_box3_ss_wages"),
            Map.entry("3\nSocial security wages", "w2_box3_ss_wages"),

            // Box 4 - Social security tax withheld
            Map.entry("4", "w2_box4_ss_withheld"),
            Map.entry("Box 4", "w2_box4_ss_withheld"),
            Map.entry("Social security tax withheld", "w2_box4_ss_withheld"),
            Map.entry("4\nSocial security tax withheld", "w2_box4_ss_withheld"),

            // Box 5 - Medicare wages and tips
            Map.entry("5", "w2_box5_medicare_wages"),
            Map.entry("Box 5", "w2_box5_medicare_wages"),
            Map.entry("Medicare wages and tips", "w2_box5_medicare_wages"),
            Map.entry("5\nMedicare wages and tips", "w2_box5_medicare_wages"),

            // Box 6 - Medicare tax withheld
            Map.entry("6", "w2_box6_medicare_withheld"),
            Map.entry("Box 6", "w2_box6_medicare_withheld"),
            Map.entry("Medicare tax withheld", "w2_box6_medicare_withheld"),
            Map.entry("6\nMedicare tax withheld", "w2_box6_medicare_withheld"),

            // Box 12 - Deferred compensation codes (may be multi-value; capture total)
            Map.entry("12", "w2_box12_deferred_compensation"),
            Map.entry("Box 12", "w2_box12_deferred_compensation"),
            Map.entry("12\nSee instructions for box 12", "w2_box12_deferred_compensation"),

            // Box 14 - Other
            Map.entry("14", "w2_box14_other"),
            Map.entry("Box 14", "w2_box14_other"),
            Map.entry("14\nOther", "w2_box14_other"),

            // Box 16 - State wages, tips
            Map.entry("16", "w2_box16_state_wages"),
            Map.entry("Box 16", "w2_box16_state_wages"),
            Map.entry("State wages, tips, etc.", "w2_box16_state_wages"),
            Map.entry("16\nState wages, tips, etc.", "w2_box16_state_wages"),

            // Employer info
            Map.entry("Employer's name, address, and ZIP code", "w2_employer_name"),
            Map.entry("b Employer identification number (EIN)", "w2_employer_ein"),
            Map.entry("Employer identification number", "w2_employer_ein")
    );

    @Override
    public String name() {
        return "form_w2";
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
