package io.callistotech.enterprise.api.dto;

import io.callistotech.enterprise.classification.DocumentClassification;

import java.util.List;

/**
 * Response payload for document classification — one entry per requested document,
 * in request order.
 *
 * @param classifications IRS form type(s) found per document
 */
public record ClassificationResponse(List<DocumentClassification> classifications) {}
