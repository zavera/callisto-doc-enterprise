package io.callistotech.enterprise.fieldmap;

import java.util.Map;

/**
 * A FieldMap provides the canonical field definitions for a specific document type and tax year.
 * Keys are raw Azure DI key variants; values are canonical field names.
 * Implementations are registered as Spring beans and discovered by FieldMapRegistry.
 */
public interface FieldMap {

    /** Unique name identifying this field map (e.g. "form_1040", "form_w2"). */
    String name();

    /**
     * Tax year this field map applies to (4-digit string, e.g. "2023").
     * Use "any" to indicate the map applies to all years.
     */
    String taxYear();

    /**
     * Map of raw Azure DI key variants → canonical field name.
     * May include multiple raw key variants that all resolve to the same canonical name.
     */
    Map<String, String> entries();
}
