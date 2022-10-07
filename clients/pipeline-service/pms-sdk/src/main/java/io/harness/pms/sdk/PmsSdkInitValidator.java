/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static org.apache.commons.collections4.CollectionUtils.subtract;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.PmsSdkPlanCreatorValidationException;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.VariableCreator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Execute startup validation rules to enforce if PMS SDK is set and can initialize.
 */
@UtilityClass
@Slf4j
public class PmsSdkInitValidator {
  /**
   * Is expected that every {@link PartialPlanCreator} has your own {@link FilterJsonCreator} and {@link
   * VariableCreator}. For this moment we only detect and notify the differences without change current final list of
   * supported types.
   *
   * @throws PmsSdkPlanCreatorValidationException when any difference is detected.
   */
  public static Map<String, Set<String>> validatePlanCreators(PipelineServiceInfoProvider pipelineServiceInfoProvider) {
    final Map<String, Set<String>> supportedPlan =
        supportedTypesPlanCreator(pipelineServiceInfoProvider.getPlanCreators());
    final Map<String, Set<String>> supportedFilters =
        supportedTypesFilterCreator(pipelineServiceInfoProvider.getFilterJsonCreators());
    final Map<String, Set<String>> supportedVariables =
        supportedTypesVariableCreator(pipelineServiceInfoProvider.getVariableCreators());

    Map<String, Set<String>> unsupportedFilters = new HashMap<>();
    Map<String, Set<String>> unsupportedVariables = new HashMap<>();

    supportedPlan.entrySet().forEach(entry -> {
      detectUnsupportedTypes("FilterJsonCreators", entry, supportedFilters, unsupportedFilters);
      detectUnsupportedTypes("VariableJsonCreators", entry, supportedVariables, unsupportedVariables);
    });

    if (MapUtils.isNotEmpty(unsupportedFilters) || MapUtils.isNotEmpty(unsupportedVariables)) {
      throw new PmsSdkPlanCreatorValidationException(unsupportedFilters, unsupportedVariables);
    }
    return supportedPlan;
  }

  private static void detectUnsupportedTypes(String label, Map.Entry<String, Set<String>> planCreatorEntry,
      Map<String, Set<String>> supported, Map<String, Set<String>> unsupported) {
    final String planKey = planCreatorEntry.getKey();

    if (supported.containsKey(planKey)) {
      final Set<String> content = supported.get(planKey);
      final Set<String> planValues = planCreatorEntry.getValue();

      final Collection<String> notFound = getDifferences(content, planValues);
      if (!notFound.isEmpty()) {
        log.error("[PMS-SDK] Identifier [{}] has missing supported types {} inside {}", planKey, notFound, label);
        unsupported.put(planKey, new HashSet<>(notFound));
      }
    } else {
      log.error("[PMS-SDK] Identifier [{}] is missing inside the {} supported types", planKey, label);
      unsupported.put(planKey, Collections.emptySet());
    }
  }

  @NotNull
  private static Collection<String> getDifferences(Set<String> filters, Set<String> planValues) {
    final Collection<String> diff;
    if (planValues.size() == filters.size()) {
      diff = subtract(filters, planValues);

    } else if (planValues.size() > filters.size()) {
      diff = subtract(planValues, filters);

    } else {
      diff = subtract(filters, planValues);
    }
    return diff;
  }

  /**
   * Extract the supported types from a list of {@link PartialPlanCreator}s.
   */
  private static Map<String, Set<String>> supportedTypesPlanCreator(List<PartialPlanCreator<?>> planCreators) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    for (PartialPlanCreator<?> planCreator : planCreators) {
      populateSupportedTypes(supportedTypes, planCreator.getSupportedTypes());
    }
    return supportedTypes;
  }

  private static Map<String, Set<String>> supportedTypesVariableCreator(List<VariableCreator> variableCreators) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    for (VariableCreator<?> variableCreator : variableCreators) {
      populateSupportedTypes(supportedTypes, variableCreator.getSupportedTypes());
    }
    return supportedTypes;
  }

  private static Map<String, Set<String>> supportedTypesFilterCreator(List<FilterJsonCreator> filterCreators) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    for (FilterJsonCreator<?> filterCreator : filterCreators) {
      populateSupportedTypes(supportedTypes, filterCreator.getSupportedTypes());
    }
    return supportedTypes;
  }

  private static void populateSupportedTypes(
      Map<String, Set<String>> supportedTypes, Map<String, Set<String>> currTypes) {
    if (EmptyPredicate.isNotEmpty(currTypes)) {
      currTypes.forEach((k, v) -> {
        if (EmptyPredicate.isEmpty(v)) {
          return;
        }
        if (supportedTypes.containsKey(k)) {
          supportedTypes.get(k).addAll(v);
        } else {
          supportedTypes.put(k, new HashSet<>(v));
        }
      });
    }
  }
}
