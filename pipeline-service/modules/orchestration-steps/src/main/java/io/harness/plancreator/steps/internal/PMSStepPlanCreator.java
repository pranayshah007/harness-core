/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.manualIntervention.ManualInterventionAdviserRollbackParameters;
import io.harness.advisers.manualIntervention.ManualInterventionAdviserWithRollback;
import io.harness.advisers.retry.RetryAdviserRollbackParameters;
import io.harness.advisers.retry.RetryAdviserWithRollback;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.GenericStepPMSPlanCreator;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class PMSStepPlanCreator extends GenericStepPMSPlanCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet();
  }

  @Override
  protected AdviserObtainment getRetryAdviserObtainment(Set<FailureType> failureTypes, String nextNodeUuid,
      AdviserObtainment.Builder adviserObtainmentBuilder, RetryFailureActionConfig retryAction,
      ParameterField<Integer> retryCount, FailureStrategyActionConfig actionUnderRetry, YamlField currentField) {
    return adviserObtainmentBuilder.setType(RetryAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(
            kryoSerializer.asBytes(RetryAdviserRollbackParameters.builder()
                                       .applicableFailureTypes(failureTypes)
                                       .nextNodeId(nextNodeUuid)
                                       .repairActionCodeAfterRetry(toRepairAction(actionUnderRetry))
                                       .retryActionConfig(actionUnderRetry)
                                       .retryCount(retryCount.getValue())
                                       .strategyToUuid(getRollbackStrategyMap(currentField))
                                       .waitIntervalList(retryAction.getSpecConfig()
                                                             .getRetryIntervals()
                                                             .getValue()
                                                             .stream()
                                                             .map(s -> (int) TimeoutUtils.getTimeoutInSeconds(s, 0))
                                                             .collect(Collectors.toList()))
                                       .build())))
        .build();
  }

  @Override
  protected AdviserObtainment getManualInterventionAdviserObtainment(Set<FailureType> failureTypes,
      AdviserObtainment.Builder adviserObtainmentBuilder, ManualInterventionFailureActionConfig actionConfig,
      FailureStrategyActionConfig actionUnderManualIntervention, YamlField currentField) {
    return adviserObtainmentBuilder.setType(ManualInterventionAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
            ManualInterventionAdviserRollbackParameters.builder()
                .applicableFailureTypes(failureTypes)
                .timeoutAction(toRepairAction(actionUnderManualIntervention))
                .timeout((int) TimeoutUtils.getTimeoutInSeconds(actionConfig.getSpecConfig().getTimeout(), 0))
                .build())))
        .build();
  }
}
