/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.failureStrategy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.yaml.core.failurestrategy.NGFailureType.ALL_ERRORS;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHORIZATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.CONNECTIVITY_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.TIMEOUT_ERROR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.FailureStrategiesUtils;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.marksuccess.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
@Slf4j
public class StageFailureStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAtleastAllErrorsFailureStrategyExists() {
    List<FailureStrategyConfig> stageFailureStrategies1, stageFailureStrategies2, stageFailureStrategies3;
    // Not containing error type as ALL_ERRORS
    stageFailureStrategies1 = Collections.singletonList(
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

    boolean ans = GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies1);
    assertThat(ans).isEqualTo(false);

    // Containing error type as ALL_ERRORS only
    stageFailureStrategies2 =
        Collections.singletonList(FailureStrategyConfig.builder()
                                      .onFailure(OnFailureConfig.builder()
                                                     .errors(Collections.singletonList(ALL_ERRORS))
                                                     .action(AbortFailureActionConfig.builder().build())
                                                     .build())
                                      .build());

    ans = GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies2);
    assertThat(ans).isEqualTo(true);

    // Containing other error along with ALL_ERRORS
    List<NGFailureType> test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(ALL_ERRORS);
    stageFailureStrategies3 = Collections.singletonList(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder().errors(test).action(AbortFailureActionConfig.builder().build()).build())
            .build());
    ans = GenericStepPMSPlanCreator.containsOnlyAllErrorsInSomeConfig(stageFailureStrategies3);
    assertThat(ans).isEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMultipleActionsSingleError() {
    List<FailureStrategyConfig> stageFailureStrategies = new ArrayList<>();
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                  .action(IgnoreFailureActionConfig.builder().build())
                                                  .build())
                                   .build());

    assertThatThrownBy(() -> FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies))
        .isInstanceOf(InvalidRequestException.class);

    List<NGFailureType> test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(CONNECTIVITY_ERROR);
    List<FailureStrategyConfig> stageFailureStrategies2 = new ArrayList<>();
    stageFailureStrategies2.add(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder().errors(test).action(AbortFailureActionConfig.builder().build()).build())
            .build());
    stageFailureStrategies2.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());
    assertThatThrownBy(() -> FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies2))
        .isInstanceOf(InvalidRequestException.class);

    // Check no other error can be clubbed with AllErrors
    test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(ALL_ERRORS);
    List<FailureStrategyConfig> stageFailureStrategies3 = new ArrayList<>();
    stageFailureStrategies3.add(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder().errors(test).action(AbortFailureActionConfig.builder().build()).build())
            .build());

    assertThatThrownBy(() -> FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies3))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMultipleErrorsSingleAction() {
    List<FailureStrategyConfig> stageFailureStrategies1 = new ArrayList<>();
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(CONNECTIVITY_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(TIMEOUT_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMapExpected =
        FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies1);

    List<NGFailureType> test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(CONNECTIVITY_ERROR);
    test.add(TIMEOUT_ERROR);
    FailureStrategyActionConfig testAction = IgnoreFailureActionConfig.builder().build();
    List<FailureStrategyConfig> stageFailureStrategies2 = new ArrayList<>();
    stageFailureStrategies2.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder().errors(test).action(testAction).build())
                                    .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMapResult =
        FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies2);

    assertThat(actionMapExpected.keySet().equals(actionMapResult.keySet())).isEqualTo(true);
    assertThat(actionMapExpected.get(testAction).equals(actionMapResult.get(testAction))).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAllErrorsWithOtherErrors() {
    List<FailureStrategyConfig> stageFailureStrategies1 = new ArrayList<>();
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(ALL_ERRORS))
                                                   .action(MarkAsSuccessFailureActionConfig.builder().build())
                                                   .build())
                                    .build());
    stageFailureStrategies1.add(FailureStrategyConfig.builder()
                                    .onFailure(OnFailureConfig.builder()
                                                   .errors(Collections.singletonList(TIMEOUT_ERROR))
                                                   .action(IgnoreFailureActionConfig.builder().build())
                                                   .build())
                                    .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMapExpected =
        FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stageFailureStrategies1);

    assertThat(actionMapExpected.get(IgnoreFailureActionConfig.builder().build()))
        .contains(FailureType.AUTHORIZATION_FAILURE);
    assertThat(actionMapExpected.get(IgnoreFailureActionConfig.builder().build()))
        .contains(FailureType.TIMEOUT_FAILURE);
  }
}
