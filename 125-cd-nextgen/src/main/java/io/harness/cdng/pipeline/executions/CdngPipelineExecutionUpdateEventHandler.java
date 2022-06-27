/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;

import static io.harness.executions.steps.StepSpecTypeConstants.K8S_ROLLING_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TERRAFORM_APPLY;
import static io.harness.executions.steps.StepSpecTypeConstants.TERRAFORM_DESTROY;
import static io.harness.executions.steps.StepSpecTypeConstants.TERRAFORM_PLAN;
import static io.harness.executions.steps.StepSpecTypeConstants.TERRAFORM_ROLLBACK;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.EXPIRED;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CdngPipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  private static final Set<String> k8sSteps =
      Sets.newHashSet(StepSpecTypeConstants.GITOPS_CREATE_PR, StepSpecTypeConstants.GITOPS_MERGE_PR,
              StepSpecTypeConstants.K8S_ROLLING_DEPLOY,
          StepSpecTypeConstants.K8S_ROLLING_ROLLBACK, StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY,
          StepSpecTypeConstants.K8S_APPLY, StepSpecTypeConstants.K8S_SCALE, StepSpecTypeConstants.K8S_BG_SWAP_SERVICES,
          StepSpecTypeConstants.K8S_CANARY_DELETE, StepSpecTypeConstants.K8S_CANARY_DEPLOY,
          StepSpecTypeConstants.K8S_DELETE, StepSpecTypeConstants.HELM_DEPLOY, StepSpecTypeConstants.HELM_ROLLBACK);

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private StepHelper stepHelper;
  @Inject private AccountService accountService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    try {
      if (isExpiredOrAborted(event.getStatus()) && (isK8sOrTerraformRollback(event.getAmbiance()))) {
        String accountName = accountService.getAccount(AmbianceUtils.getAccountId(event.getAmbiance())).getName();
        stepHelper.sendRollbackTelemetryEvent(event.getAmbiance(), event.getStatus(), accountName);
      }

      if (updateK8sLogStreams(event)) {
        List<String> logKeys = StepUtils.generateLogKeys(event.getAmbiance(), Collections.emptyList());
        if (EmptyPredicate.isNotEmpty(logKeys)) {
          String prefix = logKeys.get(0);
          ILogStreamingStepClient logStreamingStepClient =
              logStreamingStepClientFactory.getLogStreamingStepClient(event.getAmbiance());
          logStreamingStepClient.closeAllOpenStreamsWithPrefix(prefix);
        }
      }
    } catch (Exception ex) {
      log.error("Unable to close log streams", ex);
    }
  }

  private boolean isK8sOrTerraformRollback(Ambiance ambiance) {
    return isK8sRollingRollbackStep(ambiance) || isTerraformRollbackStep(ambiance);
  }

  private boolean isK8sRollingRollbackStep(Ambiance ambiance) {
    return isStepType(ambiance, K8S_ROLLING_ROLLBACK);
  }

  private boolean isTerraformRollbackStep(Ambiance ambiance) {
    return isStepType(ambiance, TERRAFORM_ROLLBACK);
  }

  // currently not used, but left here for future use.
  private boolean isInfrastructureRollback(Ambiance ambiance) {
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    return level.getIdentifier().equals(NGCommonUtilPlanCreationConstants.INFRA_ROLLBACK_NODE_IDENTIFIER);
  }

  // currently not used, but left here for future use.
  private boolean isDeploymentRollback(Ambiance ambiance) {
    boolean hasInfraRollbackNodeStep = false;
    boolean hasRollbackStepsStep = false;

    for (Level level : ambiance.getLevelsList()) {
      if (!hasRollbackStepsStep && level.getIdentifier().equals(YAMLFieldNameConstants.ROLLBACK_STEPS)) {
        hasRollbackStepsStep = true;
      }

      // if we find the infra rollback node identifier then it must be an infrastructure rollback and not the deployment
      // one
      if (level.getIdentifier().equals(NGCommonUtilPlanCreationConstants.INFRA_ROLLBACK_NODE_IDENTIFIER)) {
        hasInfraRollbackNodeStep = true;
        break;
      }
    }

    return hasRollbackStepsStep && !hasInfraRollbackNodeStep;
  }

  // currently not used, but left here for future use.
  private boolean isTerraformInfrastructureRollback(Ambiance ambiance) {
    boolean isTFStep = isStepType(ambiance, TERRAFORM_ROLLBACK) || isStepType(ambiance, TERRAFORM_DESTROY)
        || isStepType(ambiance, TERRAFORM_PLAN) || isStepType(ambiance, TERRAFORM_APPLY);

    if (isTFStep) {
      for (Level level : ambiance.getLevelsList()) {
        if (level.getIdentifier().equals(NGCommonUtilPlanCreationConstants.INFRA_ROLLBACK_NODE_IDENTIFIER)) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isExpiredOrAborted(Status status) {
    return EXPIRED.equals(status) || ABORTED.equals(status);
  }

  private boolean isStepType(Ambiance ambiance, String stepSpecType) {
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    return level != null && level.getStepType() != null && stepSpecType.equals(level.getStepType().getType());
  }

  private boolean updateK8sLogStreams(OrchestrationEvent event) {
    return StatusUtils.isFinalStatus(event.getStatus())
        && k8sSteps.contains(
            Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(event.getAmbiance())).getStepType().getType());
  }
}
