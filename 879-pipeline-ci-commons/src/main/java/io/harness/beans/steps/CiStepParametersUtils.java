/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.StepStatusMetadata.StepStatusMetadataKeys;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIStepStatusRepository;
import io.harness.utils.TimeoutUtils;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;

@OwnedBy(HarnessTeam.CI)
public class CiStepParametersUtils {
  @Inject protected CIStepStatusRepository ciStepStatusRepository;
  @Inject protected HPersistence persistence;

  public static StepElementParametersBuilder getStepParameters(CIAbstractStepNode stepNode) {
    StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
    stepBuilder.name(stepNode.getName());
    stepBuilder.identifier(stepNode.getIdentifier());
    stepBuilder.delegateSelectors(stepNode.getDelegateSelectors());
    stepBuilder.description(stepNode.getDescription());
    stepBuilder.skipCondition(stepNode.getSkipCondition());
    stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepNode.getTimeout())));
    stepBuilder.when(stepNode.getWhen() != null ? stepNode.getWhen().getValue() : null);
    stepBuilder.type(stepNode.getType());
    stepBuilder.uuid(stepNode.getUuid());
    stepBuilder.enforce(stepNode.getEnforce());

    return stepBuilder;
  }

  public static StepElementParametersBuilder getStepParameters(
      CIAbstractStepNode stepNode, OnFailRollbackParameters failRollbackParameters) {
    return getStepParameters(stepNode);
  }

  public void saveCIStepStatusInfo(Ambiance ambiance, StepExecutionStatus status, String stepIdentifier) {
    Query<StepStatusMetadata> query = persistence.createQuery(StepStatusMetadata.class)
                                          .field(StepStatusMetadataKeys.stageExecutionId)
                                          .equal(ambiance.getStageExecutionId());

    UpdateOperations<StepStatusMetadata> update = persistence.createUpdateOperations(StepStatusMetadata.class)
                                                      .set(StepStatusMetadataKeys.status, status)
                                                      .push(StepStatusMetadataKeys.failedSteps, stepIdentifier);
    persistence.upsert(query, update);
  }
}
