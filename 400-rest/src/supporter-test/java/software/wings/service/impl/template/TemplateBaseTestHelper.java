/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.VERIFY_SERVICE;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;

import io.harness.beans.WorkflowType;

import software.wings.WingsBaseTest;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.template.Template;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.template.TemplateFolderService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import org.junit.Before;

public class TemplateBaseTestHelper extends WingsBaseTest {
  @Inject protected TemplateFolderService templateFolderService;
  @Inject protected TemplateService templateService;
  @Inject protected TemplateGalleryService templateGalleryService;
  @Inject protected TemplateFolderServiceImpl templateFolderServiceImpl;

  @Before
  public void setUp() {
    templateGalleryService.loadHarnessGallery();
    templateGalleryService.saveHarnessCommandLibraryGalleryToAccount(GLOBAL_ACCOUNT_ID, ACCOUNT_NAME);
  }

  protected Workflow generateWorkflow(Template savedTemplate, GraphNode step) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .uuid(WORKFLOW_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).addStep(step).build())
                .addWorkflowPhase(
                    aWorkflowPhase()
                        .infraMappingId(INFRA_MAPPING_ID)
                        .serviceId(SERVICE_ID)
                        .deploymentType(SSH)
                        .phaseSteps(asList(
                            aPhaseStep(VERIFY_SERVICE, WorkflowServiceHelper.VERIFY_SERVICE).addStep(step).build()))
                        .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).addStep(step).build())
                .build())
        .linkedTemplateUuids(asList(savedTemplate.getUuid()))
        .build();
  }
}
