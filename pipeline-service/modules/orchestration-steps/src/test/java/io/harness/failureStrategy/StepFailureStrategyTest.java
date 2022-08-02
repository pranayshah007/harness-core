/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.failureStrategy;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHORIZATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.CONNECTIVITY_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.TIMEOUT_ERROR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.FailureStrategiesUtils;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class StepFailureStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMultipleActionsSingleError() {
    List<FailureStrategyConfig> stepFailureStrategies = new ArrayList<>();
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                 .action(AbortFailureActionConfig.builder().build())
                                                 .build())
                                  .build());
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                 .action(IgnoreFailureActionConfig.builder().build())
                                                 .build())
                                  .build());

    assertThatThrownBy(() -> FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stepFailureStrategies))
        .isInstanceOf(InvalidRequestException.class);

    List<NGFailureType> test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(CONNECTIVITY_ERROR);
    List<FailureStrategyConfig> stepFailureStrategies2 = new ArrayList<>();
    stepFailureStrategies2.add(
        FailureStrategyConfig.builder()
            .onFailure(
                OnFailureConfig.builder().errors(test).action(AbortFailureActionConfig.builder().build()).build())
            .build());
    stepFailureStrategies2.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                  .action(IgnoreFailureActionConfig.builder().build())
                                                  .build())
                                   .build());

    assertThatThrownBy(() -> FailureStrategiesUtils.priorityMergeFailureStrategies(null, null, stepFailureStrategies2))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMultipleErrorsSingleAction() {
    List<FailureStrategyConfig> stepFailureStrategies1 = new ArrayList<>();
    stepFailureStrategies1.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                  .action(IgnoreFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stepFailureStrategies1.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(CONNECTIVITY_ERROR))
                                                  .action(IgnoreFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stepFailureStrategies1.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(TIMEOUT_ERROR))
                                                  .action(IgnoreFailureActionConfig.builder().build())
                                                  .build())
                                   .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMapExpected =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies1, null, null);

    List<NGFailureType> test = new ArrayList<>();
    test.add(AUTHORIZATION_ERROR);
    test.add(CONNECTIVITY_ERROR);
    test.add(TIMEOUT_ERROR);
    FailureStrategyActionConfig testAction = IgnoreFailureActionConfig.builder().build();
    List<FailureStrategyConfig> stepFailureStrategies2 = new ArrayList<>();
    stepFailureStrategies2.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder().errors(test).action(testAction).build())
                                   .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMapResult =
        FailureStrategiesUtils.priorityMergeFailureStrategies(stepFailureStrategies2, null, null);

    assertThat(actionMapExpected.keySet().equals(actionMapResult.keySet())).isEqualTo(true);
    assertThat(actionMapExpected.get(testAction).equals(actionMapResult.get(testAction))).isEqualTo(true);
  }
}
