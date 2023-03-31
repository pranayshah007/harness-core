/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.BuildWorkflow.BuildOrchestrationWorkflowBuilder.aBuildOrchestrationWorkflow;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PHASE_ID;
import static software.wings.utils.WingsTestConstants.PHASE_STEP;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Workflow;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class MigrateServiceNowCriteriaInWorkflowsTest extends WingsBaseTest {
  @InjectMocks @Inject private MigrateServiceNowCriteriaInWorkflows migrateSNOW;
  @Mock private WorkflowService workflowService;

  private final String APPROVAL_PARAMS = "approvalStateParams";

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void migrateBuildWorkflowWithSnowApproval() {
    PhaseStep approvalStep =
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    Workflow buildWorkflow =
        aWorkflow()
            .name(WORKFLOW_NAME)
            .uuid(WORKFLOW_ID)
            .appId(APP_ID)
            .orchestrationWorkflow(
                aBuildOrchestrationWorkflow()
                    .addWorkflowPhase(aWorkflowPhase().phaseSteps(Collections.singletonList(approvalStep)).build())
                    .build())
            .build();

    migrateSNOW.migrate(buildWorkflow);

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) buildWorkflow.getOrchestrationWorkflow();
    GraphNode approvalNode =
        canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0).getSteps().get(0);
    Map<String, Object> serviceNowApprovalParams =
        (Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
            .get("serviceNowApprovalParams");
    assertSnowMigration(serviceNowApprovalParams);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void migrateBasicWorkflowWithSnowApproval() {
    PhaseStep approvalStep =
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    PhaseStep approvalStep2 =
        aPhaseStep(PhaseStepType.VERIFY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    PhaseStep approvalStep3 =
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    Workflow buildWorkflow =
        aWorkflow()
            .name(WORKFLOW_NAME)
            .appId(APP_ID)
            .serviceId(SERVICE_ID)
            .infraMappingId(INFRA_MAPPING_ID)
            .workflowType(WorkflowType.ORCHESTRATION)
            .envId(ENV_ID)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow()
                                       .addWorkflowPhase(aWorkflowPhase()
                                                             .uuid(PHASE_ID)
                                                             .name("PHASE")
                                                             .phaseSteps(Arrays.asList(approvalStep, approvalStep2))
                                                             .build())
                                       .withRollbackWorkflowPhaseIdMap(Collections.singletonMap(PHASE_STEP,
                                           aWorkflowPhase()
                                               .phaseSteps(Collections.singletonList(approvalStep3))
                                               .rollback(true)
                                               .phaseNameForRollback("PHASE")
                                               .build()))
                                       .build())
            .build();

    migrateSNOW.migrate(buildWorkflow);

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) buildWorkflow.getOrchestrationWorkflow();
    GraphNode approvalNode =
        canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0).getSteps().get(0);
    Map<String, Object> serviceNowApprovalParams =
        (Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
            .get("serviceNowApprovalParams");
    assertSnowMigration(serviceNowApprovalParams);
    approvalNode = canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(1).getSteps().get(0);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));

    approvalNode = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap()
                       .get(PHASE_STEP)
                       .getPhaseSteps()
                       .get(0)
                       .getSteps()
                       .get(0);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void migrateCanaryWorkflowWithSnowApproval() {
    PhaseStep approvalStep3 =
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    PhaseStep approvalStep4 =
        aPhaseStep(PhaseStepType.DEPLOY_SERVICE)
            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
            .build();

    Workflow canaryWorkflow =
        aWorkflow()
            .uuid(WORKFLOW_ID)
            .name(WORKFLOW_NAME)
            .appId(APP_ID)
            .serviceId(SERVICE_ID)
            .infraMappingId(INFRA_MAPPING_ID)
            .workflowType(WorkflowType.ORCHESTRATION)
            .envId(ENV_ID)
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow()
                    .withPreDeploymentSteps(
                        aPhaseStep(PRE_DEPLOYMENT)
                            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
                            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
                            .build())
                    .addWorkflowPhase(
                        aWorkflowPhase().uuid(PHASE_ID).name("PHASE").phaseSteps(Arrays.asList(approvalStep3)).build())
                    .withPostDeploymentSteps(
                        aPhaseStep(PRE_DEPLOYMENT)
                            .addStep(GraphNode.builder().type("APPROVAL").properties(fetchApprovalProperties()).build())
                            .build())
                    .withRollbackWorkflowPhaseIdMap(Collections.singletonMap(PHASE_STEP,
                        aWorkflowPhase()
                            .phaseSteps(Collections.singletonList(approvalStep4))
                            .rollback(true)
                            .phaseNameForRollback("PHASE")
                            .build()))
                    .build())
            .build();

    when(workflowService.readWorkflow(anyString(), anyString())).thenReturn(canaryWorkflow);
    migrateSNOW.migrate(canaryWorkflow);

    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) canaryWorkflow.getOrchestrationWorkflow();
    GraphNode approvalNode = canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps().get(0);
    Map<String, Object> serviceNowApprovalParams =
        (Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
            .get("serviceNowApprovalParams");
    assertSnowMigration(serviceNowApprovalParams);

    approvalNode = canaryOrchestrationWorkflow.getPreDeploymentSteps().getSteps().get(1);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));

    approvalNode = canaryOrchestrationWorkflow.getWorkflowPhases().get(0).getPhaseSteps().get(0).getSteps().get(0);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));

    approvalNode = canaryOrchestrationWorkflow.getPostDeploymentSteps().getSteps().get(0);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));

    approvalNode = canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap()
                       .get(PHASE_STEP)
                       .getPhaseSteps()
                       .get(0)
                       .getSteps()
                       .get(0);
    assertSnowMigration((Map<String, Object>) ((Map<String, Object>) approvalNode.getProperties().get(APPROVAL_PARAMS))
                            .get("serviceNowApprovalParams"));
  }

  private void assertSnowMigration(Map<String, Object> serviceNowApprovalParams) {
    assertThat(serviceNowApprovalParams.get("approval"))
        .isEqualTo(ImmutableMap.of(
            "conditions", ImmutableMap.of("state", Collections.singletonList("Closed")), "operator", "AND"));
    assertThat(serviceNowApprovalParams.get("rejection"))
        .isEqualTo(ImmutableMap.of(
            "conditions", ImmutableMap.of("state", Collections.singletonList("Canceled")), "operator", "AND"));
  }

  private Map<String, Object> fetchApprovalProperties() {
    Map<String, Object> properties = new HashMap<>();
    properties.put("approvalStateType", "SERVICENOW");
    Map<String, Object> serviceNowApprovalParams = new HashMap<>();
    serviceNowApprovalParams.put("approvalField", "state");
    serviceNowApprovalParams.put("approvalValue", "Closed");
    serviceNowApprovalParams.put("rejectionField", "state");
    serviceNowApprovalParams.put("rejectionValue", "Canceled");
    properties.put(APPROVAL_PARAMS, Collections.singletonMap("serviceNowApprovalParams", serviceNowApprovalParams));
    return properties;
  }
}
