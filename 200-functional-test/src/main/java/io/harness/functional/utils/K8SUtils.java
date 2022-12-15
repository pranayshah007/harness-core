/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.utils;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateType.K8S_DELETE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.generator.OwnerManager;
import io.harness.testframework.restutils.ArtifactStreamRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.RollingOrchestrationWorkflow;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.WorkflowExecutionServiceImpl;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;

@Singleton
public class K8SUtils {
  @Inject private WorkflowExecutionServiceImpl workflowExecutionService;
  public static Workflow createK8sV2Workflow(String appId, String envId, String serviceId, String infraDefinitionId,
      String name, OrchestrationWorkflowType orchestrationWorkflowType, String bearerToken, String accountId) {
    Workflow workflow = aWorkflow()
                            .name(name)
                            .appId(appId)
                            .envId(envId)
                            .serviceId(serviceId)
                            .infraDefinitionId(infraDefinitionId)
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .build();
    workflow.getOrchestrationWorkflow().setOrchestrationWorkflowType(orchestrationWorkflowType);
    return WorkflowRestUtils.createWorkflow(bearerToken, accountId, appId, workflow);
  }

  public static Workflow createK8sCleanupWorkflow(String appId, String envId, String serviceId, String infraMappingId,
      String name, String bearerToken, String accountId) {
    List<PhaseStep> phaseSteps = new ArrayList<>();
    Map<String, Object> defaultDeleteProperties = new HashMap<>();
    defaultDeleteProperties.put("resources", "*");

    phaseSteps.add(aPhaseStep(K8S_PHASE_STEP, "Cleanup")
                       .addStep(GraphNode.builder()
                                    .id(generateUuid())
                                    .type(K8S_DELETE.name())
                                    .name("Delete Resources")
                                    .properties(defaultDeleteProperties)
                                    .build())
                       .build());

    Workflow cleanupWorkflow = createK8sV2Workflow(appId, envId, serviceId, infraMappingId, "Cleanup-" + name,
        OrchestrationWorkflowType.ROLLING, bearerToken, accountId);

    cleanupWorkflow.getOrchestrationWorkflow().setOrchestrationWorkflowType(OrchestrationWorkflowType.ROLLING);

    WorkflowPhase phase =
        ((RollingOrchestrationWorkflow) cleanupWorkflow.getOrchestrationWorkflow()).getWorkflowPhases().get(0);

    phase.setPhaseSteps(phaseSteps);

    WorkflowRestUtils.saveWorkflowPhase(bearerToken, appId, cleanupWorkflow.getUuid(), phase.getUuid(), phase);
    return cleanupWorkflow;
  }

  public static Workflow createWorkflow(String appId, String envId, String svcId, String infradefinitionId,
      String workflowName, OrchestrationWorkflowType workflowType, String bearerToken, String accountId) {
    Workflow savedWorkflow = K8SUtils.createK8sV2Workflow(
        appId, envId, svcId, infradefinitionId, workflowName, workflowType, bearerToken, accountId);

    assertThat(savedWorkflow).isNotNull();
    assertThat(savedWorkflow.getUuid()).isNotEmpty();
    assertThat(savedWorkflow.getWorkflowType()).isEqualTo(ORCHESTRATION);

    return savedWorkflow;
  }

  public void waitForWorkflowExecution(WorkflowExecution workflowExecution, int i, String appId) {
    Awaitility.await()
        .atMost(i, TimeUnit.MINUTES)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(()
                   -> workflowExecutionService.getWorkflowExecution(appId, workflowExecution.getUuid()).getStatus()
                == ExecutionStatus.SUCCESS);
  }

  public static ExecutionArgs createExecutionArgs(OwnerManager.Owners owners, Workflow workflow, String bearerToken) {
    return createExecutionArgs(owners, workflow, bearerToken, ImmutableMap.of());
  }

  public static ExecutionArgs createExecutionArgs(
      OwnerManager.Owners owners, Workflow workflow, String bearerToken, Map<String, String> workflowVariables) {
    Application application = owners.obtainApplication();
    Service service = owners.obtainService();
    Environment environment = owners.obtainEnvironment();
    String artifactId = ArtifactStreamRestUtils.getArtifactStreamId(
        bearerToken, application.getUuid(), environment.getUuid(), service.getUuid());
    Artifact artifact = new Artifact();
    artifact.setUuid(artifactId);

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setServiceId(service.getUuid());
    executionArgs.setCommandName("START");
    executionArgs.setWorkflowVariables(workflowVariables);
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    return executionArgs;
  }
}
