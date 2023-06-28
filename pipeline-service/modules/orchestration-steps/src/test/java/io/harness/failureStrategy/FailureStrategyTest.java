/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.failureStrategy;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHENTICATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.AUTHORIZATION_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.CONNECTIVITY_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.TIMEOUT_ERROR;
import static io.harness.yaml.core.failurestrategy.NGFailureType.VERIFICATION_ERROR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.FailureStrategiesUtils;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.ProceedWithDefaultValuesFailureActionConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualFailureSpecConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualFailureSpecConfig.ManualFailureSpecConfigBuilder;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig.ManualInterventionFailureActionConfigBuilder;
import io.harness.yaml.core.failurestrategy.manualintervention.OnTimeoutConfig;
import io.harness.yaml.core.failurestrategy.marksuccess.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.OnRetryFailureConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig.RetryFailureActionConfigBuilder;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureSpecConfig.RetryFailureSpecConfigBuilder;
import io.harness.yaml.core.failurestrategy.rollback.StageRollbackFailureActionConfig;
import io.harness.yaml.core.failurestrategy.rollback.StepGroupFailureActionConfig;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class FailureStrategyTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testprioritySameErrorsDifferentAction() {
    NGFailureType sameErrorForStepStageAndStepGroup = AUTHENTICATION_ERROR;
    NGFailureType sameErrorForStageAndStepGroup = AUTHORIZATION_ERROR;
    NGFailureType errorOnlyForStage = TIMEOUT_ERROR;

    IgnoreFailureActionConfig ExpectedStepAction = IgnoreFailureActionConfig.builder().build();
    MarkAsSuccessFailureActionConfig ExpectedStepStageAction = MarkAsSuccessFailureActionConfig.builder().build();
    AbortFailureActionConfig ExpectedStageAction = AbortFailureActionConfig.builder().build();

    List<FailureStrategyConfig> stageFailureStrategies = new ArrayList<>();
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(sameErrorForStepStageAndStepGroup))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(sameErrorForStageAndStepGroup))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(errorOnlyForStage))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());

    List<FailureStrategyConfig> stepFailureStrategies = new ArrayList<>();
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(sameErrorForStepStageAndStepGroup))
                                                 .action(IgnoreFailureActionConfig.builder().build())
                                                 .build())
                                  .build());

    List<FailureStrategyConfig> stepGroupFailureStrategies = new ArrayList<>();
    stepGroupFailureStrategies.add(
        FailureStrategyConfig.builder()
            .onFailure(OnFailureConfig.builder()
                           .errors(Collections.singletonList(sameErrorForStepStageAndStepGroup))
                           .action(MarkAsSuccessFailureActionConfig.builder().build())
                           .build())
            .build());
    stepGroupFailureStrategies.add(FailureStrategyConfig.builder()
                                       .onFailure(OnFailureConfig.builder()
                                                      .errors(Collections.singletonList(sameErrorForStageAndStepGroup))
                                                      .action(MarkAsSuccessFailureActionConfig.builder().build())
                                                      .build())
                                       .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(
            stepFailureStrategies, stepGroupFailureStrategies, stageFailureStrategies);

    assertThat(actionMap.get(ExpectedStepAction).contains(FailureType.AUTHENTICATION_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(ExpectedStepStageAction).contains(FailureType.AUTHORIZATION_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(ExpectedStageAction).contains(FailureType.TIMEOUT_FAILURE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testpriorityDifferentErrorsDifferentAction() {
    IgnoreFailureActionConfig ignoreAction = IgnoreFailureActionConfig.builder().build();
    MarkAsSuccessFailureActionConfig markAsSuccessAction = MarkAsSuccessFailureActionConfig.builder().build();
    AbortFailureActionConfig abortAction = AbortFailureActionConfig.builder().build();

    List<FailureStrategyConfig> stageFailureStrategies = new ArrayList<>();
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(AUTHENTICATION_ERROR))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());
    stageFailureStrategies.add(FailureStrategyConfig.builder()
                                   .onFailure(OnFailureConfig.builder()
                                                  .errors(Collections.singletonList(CONNECTIVITY_ERROR))
                                                  .action(AbortFailureActionConfig.builder().build())
                                                  .build())
                                   .build());

    List<FailureStrategyConfig> stepFailureStrategies = new ArrayList<>();
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(TIMEOUT_ERROR))
                                                 .action(IgnoreFailureActionConfig.builder().build())
                                                 .build())
                                  .build());
    stepFailureStrategies.add(FailureStrategyConfig.builder()
                                  .onFailure(OnFailureConfig.builder()
                                                 .errors(Collections.singletonList(AUTHORIZATION_ERROR))
                                                 .action(IgnoreFailureActionConfig.builder().build())
                                                 .build())
                                  .build());

    List<FailureStrategyConfig> stepGroupFailureStrategies = new ArrayList<>();
    stepGroupFailureStrategies.add(FailureStrategyConfig.builder()
                                       .onFailure(OnFailureConfig.builder()
                                                      .errors(Collections.singletonList(VERIFICATION_ERROR))
                                                      .action(MarkAsSuccessFailureActionConfig.builder().build())
                                                      .build())
                                       .build());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap =
        FailureStrategiesUtils.priorityMergeFailureStrategies(
            stepFailureStrategies, stepGroupFailureStrategies, stageFailureStrategies);

    assertThat(actionMap.get(abortAction).contains(FailureType.AUTHENTICATION_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(abortAction).contains(FailureType.CONNECTIVITY_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(markAsSuccessAction).contains(FailureType.VERIFICATION_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(ignoreAction).contains(FailureType.AUTHORIZATION_FAILURE)).isEqualTo(true);
    assertThat(actionMap.get(ignoreAction).contains(FailureType.TIMEOUT_FAILURE)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void validateActionAfterOnRetryAction() {
    boolean ans =
        FailureStrategiesUtils.validateActionAfterRetryFailure(MarkAsSuccessFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);

    ans = FailureStrategiesUtils.validateActionAfterRetryFailure(RetryFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(false);

    ans = FailureStrategiesUtils.validateActionAfterRetryFailure(AbortFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);

    ans = FailureStrategiesUtils.validateActionAfterRetryFailure(IgnoreFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);

    ans =
        FailureStrategiesUtils.validateActionAfterRetryFailure(ManualInterventionFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);

    ans = FailureStrategiesUtils.validateActionAfterRetryFailure(StageRollbackFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);

    ans = FailureStrategiesUtils.validateActionAfterRetryFailure(StepGroupFailureActionConfig.builder().build());
    assertThat(ans).isEqualTo(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateRetryFailureAction() {
    RetryFailureSpecConfigBuilder specBuilder =
        RetryFailureSpecConfig.builder()
            .retryCount(ParameterField.createValueField(2))
            .retryIntervals(ParameterField.createValueField(
                Collections.singletonList(Timeout.builder().timeoutString("10s").build())))
            .onRetryFailure(
                OnRetryFailureConfig.builder().action(MarkAsSuccessFailureActionConfig.builder().build()).build());
    RetryFailureActionConfigBuilder configBuilder = RetryFailureActionConfig.builder().specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateRetryFailureAction(configBuilder.build()))
        .doesNotThrowAnyException();

    specBuilder.onRetryFailure(
        OnRetryFailureConfig.builder().action(IgnoreFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateRetryFailureAction(configBuilder.build()))
        .doesNotThrowAnyException();

    specBuilder.onRetryFailure(
        OnRetryFailureConfig.builder().action(ProceedWithDefaultValuesFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateRetryFailureAction(configBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Retry action cannot have post retry failure action as ProceedWithDefaultValues");

    specBuilder.onRetryFailure(
        OnRetryFailureConfig.builder().action(RetryFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateRetryFailureAction(configBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Retry action cannot have post retry failure action as Retry");

    specBuilder.retryIntervals(ParameterField.createExpressionField(true, "", null, true));
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateRetryFailureAction(configBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Retry Interval cannot be null or empty");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateManualInterventionFailureAction() {
    ManualFailureSpecConfigBuilder specBuilder =
        ManualFailureSpecConfig.builder()
            .timeout(ParameterField.createValueField(Timeout.builder().timeoutString("20s").build()))
            .onTimeout(OnTimeoutConfig.builder().action(MarkAsSuccessFailureActionConfig.builder().build()).build());
    ManualInterventionFailureActionConfigBuilder configBuilder =
        ManualInterventionFailureActionConfig.builder().specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateManualInterventionFailureAction(configBuilder.build()))
        .doesNotThrowAnyException();

    specBuilder.onTimeout(OnTimeoutConfig.builder().action(IgnoreFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateManualInterventionFailureAction(configBuilder.build()))
        .doesNotThrowAnyException();

    specBuilder.onTimeout(
        OnTimeoutConfig.builder().action(ProceedWithDefaultValuesFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateManualInterventionFailureAction(configBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ProceedWithDefaultValues is not allowed as post timeout action in Manual intervention");

    specBuilder.onTimeout(OnTimeoutConfig.builder().action(RetryFailureActionConfig.builder().build()).build());
    configBuilder.specConfig(specBuilder.build());
    assertThatCode(() -> FailureStrategiesUtils.validateManualInterventionFailureAction(configBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Retry is not allowed as post timeout action in Manual intervention as it can lead to an infinite loop");
  }
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void validateManualRetryManualActionWorkflow() {
    RetryFailureSpecConfig retryFailureSpecConfig =
        RetryFailureSpecConfig.builder()
            .retryCount(ParameterField.createValueField(4))
            .retryIntervals(ParameterField.createValueField(Collections.singletonList(Timeout.fromString("2s"))))
            .onRetryFailure(
                OnRetryFailureConfig.builder().action(ManualInterventionFailureActionConfig.builder().build()).build())
            .build();
    boolean ans = FailureStrategiesUtils.validateManualActionUnderRetryAction(retryFailureSpecConfig);

    assertThat(ans).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void validateRetryManualRetryActionWorkflow() {
    ManualFailureSpecConfig manualFailureSpecConfig =
        ManualFailureSpecConfig.builder()
            .onTimeout(OnTimeoutConfig.builder().action(RetryFailureActionConfig.builder().build()).build())
            .build();
    boolean ans = FailureStrategiesUtils.validateRetryActionUnderManualAction(manualFailureSpecConfig);

    assertThat(ans).isEqualTo(true);
  }
}
