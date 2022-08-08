/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow.creation.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.beans.container.KubernetesContainerTaskUtils;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowPhaseHelper {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private WorkflowServiceHelper workflowServiceHelper;
  @Inject private ServiceResourceService serviceResourceService;

  public WorkflowPhase createRollbackPhase(WorkflowPhase workflowPhase) {
    return aWorkflowPhase()
        .name(WorkflowServiceHelper.ROLLBACK_PREFIX + workflowPhase.getName())
        .rollback(true)
        .serviceId(workflowPhase.getServiceId())
        .computeProviderId(workflowPhase.getComputeProviderId())
        .infraMappingName(workflowPhase.getInfraMappingName())
        .phaseNameForRollback(workflowPhase.getName())
        .deploymentType(workflowPhase.getDeploymentType())
        .infraMappingId(workflowPhase.getInfraMappingId())
        .infraDefinitionId(workflowPhase.getInfraDefinitionId())
        .build();
  }

  public void setCloudProvider(String appId, WorkflowPhase workflowPhase) {
    workflowServiceHelper.setCloudProviderInfraRefactor(appId, workflowPhase);
  }

  public void addK8sEmptyPhaseStep(WorkflowPhase workflowPhase) {
    List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
    phaseSteps.add(aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.DEPLOY).build());
  }

  public void addK8sEmptyRollbackPhaseStep(WorkflowPhase rollbackPhase) {
    List<PhaseStep> rollbackPhaseSteps = rollbackPhase.getPhaseSteps();
    rollbackPhaseSteps.add(aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.DEPLOY)
                               .withPhaseStepNameForRollback(WorkflowServiceHelper.DEPLOY)
                               .withRollback(true)
                               .build());
  }

  public boolean isDaemonSet(String appId, String serviceId) {
    KubernetesContainerTask containerTask =
        (KubernetesContainerTask) serviceResourceService.getContainerTaskByDeploymentType(
            appId, serviceId, KUBERNETES.name());
    return containerTask != null && KubernetesContainerTaskUtils.checkDaemonSet(containerTask.getAdvancedConfig());
  }

  public boolean isStatefulSet(String appId, String serviceId) {
    KubernetesContainerTask containerTask =
        (KubernetesContainerTask) serviceResourceService.getContainerTaskByDeploymentType(
            appId, serviceId, KUBERNETES.name());
    return containerTask != null && KubernetesContainerTaskUtils.checkStatefulSet(containerTask.getAdvancedConfig());
  }

  public void setCloudProviderIfNeeded(Workflow workflow, WorkflowPhase workflowPhase) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.needCloudProvider()) {
      setCloudProvider(workflow.getAppId(), workflowPhase);
    }
  }

  public boolean addPhaseIfStepsGenerated(Workflow workflow, WorkflowPhase workflowPhase) {
    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
    // No need to generate phase steps if it's already created
    if (isNotEmpty(workflowPhase.getPhaseSteps()) && orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      ((CanaryOrchestrationWorkflow) orchestrationWorkflow).getWorkflowPhases().add(workflowPhase);
      return true;
    } else {
      return false;
    }
  }
}
