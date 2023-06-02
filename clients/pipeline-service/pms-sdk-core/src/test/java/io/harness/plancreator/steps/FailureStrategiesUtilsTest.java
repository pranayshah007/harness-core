/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.ANKIT_TIWARI;
import static io.harness.yaml.core.failurestrategy.NGFailureType.ALL_ERRORS;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHENTICATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHORIZATION_ERROR;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FailureStrategiesUtilsTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldReturnEmptyMapWhenFailureStrategiesAreMissing() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;
    actionConfigCollectionMap = FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, null);
    assertThat(actionConfigCollectionMap).isEmpty();
    actionConfigCollectionMap = FailureStrategiesUtils.priorityMergeFailureStrategies(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertThat(actionConfigCollectionMap).isEmpty();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategies() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                           .action(RetryFailureActionConfig.builder()
                                       .specConfig(RetryFailureSpecConfig.builder()
                                                       .retryCount(ParameterField.createValueField(2))
                                                       .retryIntervals(ParameterField.createValueField(
                                                           asList(Timeout.fromString("2s"), Timeout.fromString("20s"))))
                                                       .build())
                                       .build())
                           .build())
            .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                           .action(RetryFailureActionConfig.builder()
                                       .specConfig(RetryFailureSpecConfig.builder()
                                                       .retryCount(ParameterField.createValueField(2))
                                                       .retryIntervals(ParameterField.createValueField(
                                                           asList(Timeout.fromString("2s"), Timeout.fromString("20s"))))
                                                       .build())
                                       .build())
                           .build())
            .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(
        actionConfigCollectionMap.get(RetryFailureActionConfig.builder()
                                          .specConfig(RetryFailureSpecConfig.builder()
                                                          .retryCount(ParameterField.createValueField(2))
                                                          .retryIntervals(ParameterField.createValueField(asList(
                                                              Timeout.fromString("2s"), Timeout.fromString("20s"))))
                                                          .build())
                                          .build()))
        .contains(FailureType.AUTHENTICATION_FAILURE, FailureType.AUTHORIZATION_FAILURE);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategiesWithSameTypeActionButDifferentActionConfiguration() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                           .action(RetryFailureActionConfig.builder()
                                       .specConfig(RetryFailureSpecConfig.builder()
                                                       .retryCount(ParameterField.createValueField(2))
                                                       .retryIntervals(ParameterField.createValueField(
                                                           asList(Timeout.fromString("2s"), Timeout.fromString("20s"))))
                                                       .build())
                                       .build())
                           .build())
            .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                           .action(RetryFailureActionConfig.builder()
                                       .specConfig(RetryFailureSpecConfig.builder()
                                                       .retryCount(ParameterField.createValueField(4))
                                                       .retryIntervals(ParameterField.createValueField(
                                                           Collections.singletonList(Timeout.fromString("2s"))))
                                                       .build())
                                       .build())
                           .build())
            .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(
        actionConfigCollectionMap.get(RetryFailureActionConfig.builder()
                                          .specConfig(RetryFailureSpecConfig.builder()
                                                          .retryCount(ParameterField.createValueField(2))
                                                          .retryIntervals(ParameterField.createValueField(asList(
                                                              Timeout.fromString("2s"), Timeout.fromString("20s"))))
                                                          .build())
                                          .build()))
        .contains(FailureType.AUTHENTICATION_FAILURE);
    assertThat(
        actionConfigCollectionMap.get(
            RetryFailureActionConfig.builder()
                .specConfig(RetryFailureSpecConfig.builder()
                                .retryCount(ParameterField.createValueField(4))
                                .retryIntervals(ParameterField.createValueField(asList(Timeout.fromString("2s"))))
                                .build())
                .build()))
        .contains(FailureType.AUTHORIZATION_FAILURE);
  }
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldMergeFailureStrategiesOnOtherErrors() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies;
    stepFailureStrategies =
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder()
                                                     .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                                                     .action(IgnoreFailureActionConfig.builder().build())
                                                     .build())
                                      .build());

    List<FailureStrategyConfig> stageFailureStrategies;
    stageFailureStrategies =
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder()
                                                     .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                                     .action(AbortFailureActionConfig.builder().build())
                                                     .build())
                                      .build());
    actionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, stageFailureStrategies);
    assertThat(actionConfigCollectionMap).isNotEmpty();
    Collection<FailureType> failureTypesAbort =
        actionConfigCollectionMap.get(AbortFailureActionConfig.builder().build());
    assertThat(failureTypesAbort).doesNotContain(FailureType.AUTHENTICATION_FAILURE);
    Collection<FailureType> failureTypesIgnore =
        actionConfigCollectionMap.get(IgnoreFailureActionConfig.builder().build());
    assertThat(failureTypesIgnore).containsOnly(FailureType.AUTHENTICATION_FAILURE);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void shouldThrowErrorForMultipleAllErrorsForStepFailures() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepFailureStrategies = new ArrayList<>();
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(ALL_ERRORS))
                                                 .action(IgnoreFailureActionConfig.builder().build())
                                                 .build())
                                  .build());

    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                                 .action(AbortFailureActionConfig.builder().build())
                                                 .build())
                                  .build());

    try {
      FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies, null, null);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("AllErrors are defined multiple times either in stage or step failure strategies.");
    }
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void shouldNotThrowErrorForMultipleAllErrorsForStepGroupFailures() {
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionConfigCollectionMap;

    List<FailureStrategyConfig> stepGroupFailureStrategies = new ArrayList<>();
    stepGroupFailureStrategies.add(FailureStrategyConfig.builder()
                                       .onFailure(OnFailureConfig.builder()
                                                      .errors(Collections.singletonList(ALL_ERRORS))
                                                      .action(IgnoreFailureActionConfig.builder().build())
                                                      .build())
                                       .build());

    stepGroupFailureStrategies.add(FailureStrategyConfig.builder()
                                       .onFailure(OnFailureConfig.builder()
                                                      .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
                                                      .action(AbortFailureActionConfig.builder().build())
                                                      .build())
                                       .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> failureStrategyActionConfigCollectionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(null, stepGroupFailureStrategies, null);
    assertThat(failureStrategyActionConfigCollectionMap).isNotEmpty();
  }
}
