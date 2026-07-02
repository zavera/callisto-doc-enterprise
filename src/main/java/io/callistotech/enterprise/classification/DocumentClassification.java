package io.callistotech.enterprise.classification;

import java.util.List;

/**
 * IRS form type(s) identified within a single document's content payload.
 *
 * @param documentId caller-supplied document identifier, echoed back unchanged
 * @param formTypes  IRS form type names present in the document, in the order they
 *                   appear (e.g. {@code ["Schedule C", "Schedule SE"]}); empty when
 *                   classification failed or found nothing
 */
public record DocumentClassification(String documentId, List<String> formTypes) {}
