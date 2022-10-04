/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.pms.sdk.PmsSdkInitValidator.validatePlanCreators;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.PmsSdkPlanCreatorValidationException;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PmsSdkInitValidatorTest {
  private static final String ANY = "__any__";
  private static final String EMAIL = "email";
  private static final String PIPELINE = "pipeline";
  private static final String STEP_GROUP = "stepGroup";
  private static final String STEP = "step";
  private static final String JIRA = "jira";
  private static final String HTTP = "http";
  private static final String POLICY = "policy";
  private static final String QUEUE = "queue";

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorAndSucceed() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, EMAIL, JIRA, HTTP));
    planCreators.add(createPlanCreator(PIPELINE, ANY));
    planCreators.add(createPlanCreator(STEP_GROUP, EMAIL, HTTP));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, EMAIL, JIRA, HTTP));
    filterCreators.add(createFilterCreator(STEP_GROUP, EMAIL, HTTP));
    filterCreators.add(createFilterCreator(PIPELINE, ANY));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, EMAIL, JIRA, HTTP));
    variableCreators.add(createVariableCreator(PIPELINE, ANY));
    variableCreators.add(createVariableCreator(STEP_GROUP, EMAIL, HTTP));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    assertThatCode(() -> validatePlanCreators(pipelineServiceInfoProvider)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenFilterHasSameSize() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, QUEUE));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL, HTTP));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, POLICY, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).hasSize(1);
    assertThat(unsupportedFilters.get(STEP)).containsOnly(HTTP);
    assertThat(unsupportedVariables).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenFilterHasLessElements() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, QUEUE));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, POLICY, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).hasSize(1);
    assertThat(unsupportedFilters.get(STEP)).containsOnly(QUEUE);
    assertThat(unsupportedVariables).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenFilterHasMoreElements() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, QUEUE));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL, HTTP, QUEUE));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, POLICY, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).hasSize(1);
    assertThat(unsupportedFilters.get(STEP)).containsOnly(HTTP);
    assertThat(unsupportedVariables).isEmpty();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenVariableHasSameSize() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, HTTP));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL, HTTP));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, POLICY, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).isEmpty();
    assertThat(unsupportedVariables).hasSize(1);
    assertThat(unsupportedVariables.get(STEP)).containsOnly(QUEUE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenVariableHasLessElements() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, HTTP));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL, HTTP));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).isEmpty();
    assertThat(unsupportedVariables).hasSize(1);
    assertThat(unsupportedVariables.get(STEP)).containsOnly(POLICY, HTTP);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorWhenVariableHasMoreElements() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, POLICY, EMAIL, HTTP));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, POLICY, EMAIL, HTTP));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, EMAIL, QUEUE, POLICY, HTTP));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).isEmpty();
    assertThat(unsupportedVariables).hasSize(1);
    assertThat(unsupportedVariables.get(STEP)).containsOnly(QUEUE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidatePlanCreatorAndDetectMissingIdentifiers() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(createPlanCreator(STEP, EMAIL, JIRA, HTTP));
    planCreators.add(createPlanCreator(PIPELINE, ANY));
    planCreators.add(createPlanCreator(STEP_GROUP, EMAIL, HTTP));

    List<FilterJsonCreator> filterCreators = new ArrayList<>();
    filterCreators.add(createFilterCreator(STEP, EMAIL, JIRA));
    filterCreators.add(createFilterCreator(STEP_GROUP, EMAIL));

    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(createVariableCreator(STEP, POLICY, EMAIL, QUEUE));

    PipelineServiceInfoProvider pipelineServiceInfoProvider =
        createPipelineServiceInfoProvider(planCreators, filterCreators, variableCreators);

    final PmsSdkPlanCreatorValidationException ex = doValidatePlanCreators(pipelineServiceInfoProvider);
    final Map<String, Set<String>> unsupportedFilters = ex.getUnsupportedFilters();
    final Map<String, Set<String>> unsupportedVariables = ex.getUnsupportedVariables();
    assertThat(unsupportedFilters).hasSize(3);
    assertThat(unsupportedFilters).containsKeys(STEP, PIPELINE, STEP_GROUP);
    assertThat(unsupportedFilters.get(STEP)).containsOnly(HTTP);
    assertThat(unsupportedFilters.get(PIPELINE)).isEmpty();
    assertThat(unsupportedFilters.get(STEP_GROUP)).containsOnly(HTTP);
    assertThat(unsupportedVariables).hasSize(3);
    assertThat(unsupportedVariables.get(STEP)).containsOnly(QUEUE, POLICY);
    assertThat(unsupportedVariables.get(PIPELINE)).isEmpty();
    assertThat(unsupportedVariables.get(STEP_GROUP)).isEmpty();
  }

  // --
  // PRIVATE METHODS

  private PartialPlanCreator<?> createPlanCreator(String name, String... elements) {
    PartialPlanCreator<?> creator = mock(PartialPlanCreator.class);
    when(creator.getSupportedTypes()).thenReturn(createSupportedTypes(name, elements));
    return creator;
  }

  private FilterJsonCreator<?> createFilterCreator(String name, String... elements) {
    FilterJsonCreator<?> creator = mock(FilterJsonCreator.class);
    when(creator.getSupportedTypes()).thenReturn(createSupportedTypes(name, elements));
    return creator;
  }

  private VariableCreator<?> createVariableCreator(String name, String... elements) {
    VariableCreator<?> creator = mock(VariableCreator.class);
    when(creator.getSupportedTypes()).thenReturn(createSupportedTypes(name, elements));
    return creator;
  }

  private Map<String, Set<String>> createSupportedTypes(String name, String... elements) {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put(name, new HashSet(Arrays.asList(elements)));
    return supportedTypes;
  }

  @NotNull
  private PipelineServiceInfoProvider createPipelineServiceInfoProvider(List<PartialPlanCreator<?>> planCreators,
      List<FilterJsonCreator> filterCreators, List<VariableCreator> variableCreators) {
    PipelineServiceInfoProvider pipelineServiceInfoProvider = mock(PipelineServiceInfoProvider.class);
    when(pipelineServiceInfoProvider.getPlanCreators()).thenReturn(planCreators);
    when(pipelineServiceInfoProvider.getFilterJsonCreators()).thenReturn(filterCreators);
    when(pipelineServiceInfoProvider.getVariableCreators()).thenReturn(variableCreators);
    return pipelineServiceInfoProvider;
  }

  /**
   * Call the validatePlanCreators method, ensure exception is throw and return it.
   */
  private PmsSdkPlanCreatorValidationException doValidatePlanCreators(
      PipelineServiceInfoProvider pipelineServiceInfoProvider) {
    final Throwable ex = catchThrowable(() -> validatePlanCreators(pipelineServiceInfoProvider));
    assertThat(ex)
        .isInstanceOf(PmsSdkPlanCreatorValidationException.class)
        .hasMessage("Plan creators has unsupported filters or unsupported variables");

    return (PmsSdkPlanCreatorValidationException) ex;
  }
}
