/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Conditions;
import org.modelmapper.MappingException;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public class MapperUtils {
  static final String TEMPLATE_VARIABLE_ENTRY = "templateVariables";
  static final String VARIABLE_DESCRIPTION_FIELD = "description";
  static final String VARIABLE_VALUE_FIELD = "value";

  private MapperUtils() {
    throw new UnsupportedOperationException();
  }

  public static void mapObject(Object from, Object to) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    modelMapper.map(from, to);
  }

  public static void mapObjectOnlyNonNull(Object from, Object to) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
    modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
    modelMapper.map(from, to);
  }

  public static void mapProperties(Map<String, Object> source, Object target) {
    try {
      mapObject(source, target);

    } catch (MappingException e) {
      log.debug("Got model mapping exception when copy properties from {} to {}", source, target, e);

      // ITERATE THE SOURCE ELEMENTS AND MAP TO THE SAME TARGET
      mapEntries(source, target);
    }
  }

  private static void mapEntries(Map<String, Object> source, Object target) {
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      try {
        mapObject(sanitizeEntry(entry), target);

      } catch (MappingException e) {
        log.error(String.format("Failure when mapping entry <%s> to the target <%s>", entry, target), e);
        throw e;
      }
    }
  }

  @VisibleForTesting
  static Map<String, Object> sanitizeEntry(Map.Entry<String, Object> entry) {
    //
    // ONLY SOME FIELDS NEED SANITIZATION, WHEN NOT REQUIRED THE SAME ENTRY IS RETURNED.
    //
    Map<String, Object> result = null;

    if (TEMPLATE_VARIABLE_ENTRY.equals(entry.getKey())) {
      result = sanitizeTemplateVariables(entry);
    }

    // ADVICE: USING singletonMap BECAUSE ENTRY VALUE CAN BE NULL
    return Optional.ofNullable(result).orElseGet(() -> Collections.singletonMap(entry.getKey(), entry.getValue()));
  }

  @VisibleForTesting
  static Map<String, Object> sanitizeTemplateVariables(final Map.Entry<String, Object> entry) {
    //
    // THE ROOT CAUSE OF MAPPING EXCEPTION IS AN ISSUE IN ModelMapper LIBRARY WHERE IS EXPECTED TO EVERY MAP
    // ELEMENT OF THE SAME FIELD HAS THE SAME KEYS, NOT THE COUNT, BUT THE KEYS. MORE DETAILS AT CDS-54824.
    //
    if (entry.getValue() instanceof List) {
      try {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> elements = (List<Map<String, String>>) entry.getValue();
        final List<Map<String, String>> result = new ArrayList<>();

        final boolean hasDescription = elements.stream().anyMatch(e -> e.containsKey(VARIABLE_DESCRIPTION_FIELD));
        final boolean hasValue = elements.stream().anyMatch(e -> e.containsKey(VARIABLE_VALUE_FIELD));

        elements.forEach(e -> {
          Map<String, String> content = new HashMap<>(e);
          if (hasDescription) {
            content.putIfAbsent(VARIABLE_DESCRIPTION_FIELD, StringUtils.EMPTY);
          }
          if (hasValue) {
            content.putIfAbsent(VARIABLE_VALUE_FIELD, StringUtils.EMPTY);
          }
          result.add(content);
        });

        if (!result.isEmpty()) {
          return Collections.singletonMap(TEMPLATE_VARIABLE_ENTRY, result);
        }

      } catch (ClassCastException e) {
        log.warn(
            String.format("Unable to cast [%s] to expected type [java.util.List]", entry.getValue().getClass()), e);
      } catch (RuntimeException e) {
        log.warn(String.format("Unable to sanitize field [%s]", TEMPLATE_VARIABLE_ENTRY), e);
      }
    }

    // FALLBACK. THE OUTPUT IS THE SAME AS INPUT
    final Map<String, Object> fallback = new HashMap<>();
    fallback.put(entry.getKey(), entry.getValue());
    return fallback;
  }
}
