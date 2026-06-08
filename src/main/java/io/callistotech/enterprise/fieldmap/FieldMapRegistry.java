package io.callistotech.enterprise.fieldmap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holds all registered FieldMap beans and provides lookup by name + taxYear.
 * Lookup precedence: exact name+year match, then name+"any" match.
 */
@Slf4j
@Component
public class FieldMapRegistry {

    private final Map<String, FieldMap> byNameAndYear;

    public FieldMapRegistry(List<FieldMap> fieldMaps) {
        this.byNameAndYear = fieldMaps.stream()
                .collect(Collectors.toMap(
                        fm -> registryKey(fm.name(), fm.taxYear()),
                        Function.identity()
                ));
        log.info("FieldMapRegistry loaded {} field maps: {}", fieldMaps.size(),
                fieldMaps.stream().map(fm -> fm.name() + "@" + fm.taxYear()).toList());
    }

    /**
     * Looks up a FieldMap by name and taxYear.
     * Falls back to name+"any" if no exact year match is found.
     *
     * @param name    field map name
     * @param taxYear 4-digit tax year string
     * @return the matching FieldMap, or empty if none found
     */
    public Optional<FieldMap> find(String name, String taxYear) {
        FieldMap exact = byNameAndYear.get(registryKey(name, taxYear));
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(byNameAndYear.get(registryKey(name, "any")));
    }

    /**
     * Returns the FieldMap for the given name, preferring the given year.
     * Throws if no match found.
     */
    public FieldMap require(String name, String taxYear) {
        return find(name, taxYear)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No FieldMap registered for name=[" + name + "] taxYear=[" + taxYear + "]"));
    }

    private static String registryKey(String name, String taxYear) {
        return name + "::" + taxYear;
    }
}
