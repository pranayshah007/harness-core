/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.api.DeploymentType.PCF;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.PCF_SETUP;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.command.PcfCommandTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.mongodb.DBCursor;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class PcfCommandTemplateProcessorTest extends TemplateBaseTestHelper {
  @Mock private WorkflowService workflowService;
  @Mock private MorphiaIterator<Workflow, Workflow> workflowIterator;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private Query<Workflow> query;
  @Mock private FieldEnd end;
  @Mock private DBCursor dbCursor;
  @Inject private PcfCommandTemplateProcessor pcfCommandTemplateProcessor;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldSavePcfCommandTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    PcfCommandTemplate pcfCommandTemplate = PcfCommandTemplate.builder()
                                                .scriptString("echo ${var1}\n"
                                                    + "export A=\"aaa\"\n"
                                                    + "export B=\"bbb\"")
                                                .build();
    Template template = Template.builder()
                            .templateObject(pcfCommandTemplate)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .name("Pcf Command")
                            .variables(asList(aVariable().type(TEXT).name("var1").mandatory(true).build()))
                            .build();
    Template savedTemplate = templateService.save(template);
    assertSavedTemplate(template, savedTemplate);
    PcfCommandTemplate savedPcfCommandTemplate = (PcfCommandTemplate) savedTemplate.getTemplateObject();
    assertThat(savedPcfCommandTemplate).isNotNull();
    assertThat(savedPcfCommandTemplate.getTimeoutIntervalInMinutes()).isEqualTo(5);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdatePcfCommandTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());

    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder);
    Template savedTemplate = templateService.save(template);
    assertSavedTemplate(template, savedTemplate);

    PcfCommandTemplate savedPcfCommandTemplate = (PcfCommandTemplate) savedTemplate.getTemplateObject();
    assertThat(savedPcfCommandTemplate).isNotNull();
    assertThat(savedPcfCommandTemplate.getScriptString()).isNotEmpty();
    assertThat(savedPcfCommandTemplate.getTimeoutIntervalInMinutes()).isEqualTo(5);

    PcfCommandTemplate updatedPcfCommandTemplate = PcfCommandTemplate.builder().timeoutIntervalInMinutes(6).build();
    savedTemplate.setTemplateObject(updatedPcfCommandTemplate);
    Template updatedTemplate = templateService.update(savedTemplate);

    updatedPcfCommandTemplate = (PcfCommandTemplate) updatedTemplate.getTemplateObject();
    assertThat(updatedTemplate).isNotNull();
    assertThat(updatedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(updatedTemplate.getVersion()).isEqualTo(2L);
    assertThat(updatedTemplate.getTemplateObject()).isNotNull();
    assertThat(((PcfCommandTemplate) updatedTemplate.getTemplateObject()).getTimeoutIntervalInMinutes()).isEqualTo(6);
    assertThat(updatedTemplate.getVariables()).extracting("name").contains("var1");
    assertThat(updatedPcfCommandTemplate).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateEntitiesLinked() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());

    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder);
    Template savedTemplate = templateService.save(template);

    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVariables()).extracting("name").contains("var1");

    PcfCommandTemplate savedPcfCommandTemplate = (PcfCommandTemplate) savedTemplate.getTemplateObject();
    assertThat(savedPcfCommandTemplate).isNotNull();
    GraphNode step = pcfCommandTemplateProcessor.constructEntityFromTemplate(savedTemplate, EntityType.WORKFLOW);
    step.setTemplateVersion(LATEST_TAG);

    final Workflow workflow = createWorkflow(savedTemplate, step);

    validateWorkflow(savedTemplate, workflow);
    verify(workflowService, times(1)).updateWorkflow(workflow, false);
  }

  private void assertSavedTemplate(Template template, Template savedTemplate) {
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getKeywords()).isNotEmpty();
    assertThat(savedTemplate.getKeywords()).contains(template.getName().toLowerCase());
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    assertThat(savedTemplate.getVariables()).isNotEmpty();
    assertThat(savedTemplate.getVariables()).extracting("name").contains("var1");
  }

  private Template getTemplate(TemplateFolder parentFolder) {
    PcfCommandTemplate httpTemplate =
        PcfCommandTemplate.builder()
            .scriptString("echo \"Hello World ${var1}\"\n export A=\"aaa\"\n export B=\"bbb\"")
            .build();
    return Template.builder()
        .templateObject(httpTemplate)
        .folderId(parentFolder.getUuid())
        .appId(GLOBAL_APP_ID)
        .accountId(GLOBAL_ACCOUNT_ID)
        .name("PCF Command")
        .variables(asList(aVariable().type(TEXT).name("var1").mandatory(true).build()))
        .build();
  }

  private void validateWorkflow(Template savedTemplate, Workflow workflow) {
    on(pcfCommandTemplateProcessor).set("wingsPersistence", wingsPersistence);
    on(pcfCommandTemplateProcessor).set("workflowService", workflowService);

    when(wingsPersistence.createQuery(Workflow.class, excludeAuthority)).thenReturn(query);

    when(query.filter(WorkflowKeys.linkedTemplateUuids, savedTemplate.getUuid())).thenReturn(query);
    when(query.fetch()).thenReturn(workflowIterator);
    when(workflowIterator.getCursor()).thenReturn(dbCursor);
    when(workflowIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(workflowIterator.next()).thenReturn(workflow);

    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

    PcfCommandTemplate savedTemplateObject = (PcfCommandTemplate) savedTemplate.getTemplateObject();
    on(savedTemplateObject).set("scriptString", "updated script");
    on(savedTemplateObject).set("timeoutIntervalInMinutes", 10);
    templateService.updateLinkedEntities(savedTemplate);
    verify(workflowService, times(1)).updateWorkflow(any(), anyBoolean());
    verify(workflowService).readWorkflow(APP_ID, WORKFLOW_ID);
  }

  private Workflow createWorkflow(Template savedTemplate, GraphNode step) {
    return aWorkflow()
        .name(WORKFLOW_NAME)
        .appId(APP_ID)
        .uuid(WORKFLOW_ID)
        .workflowType(WorkflowType.ORCHESTRATION)
        .orchestrationWorkflow(
            aCanaryOrchestrationWorkflow()
                .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).addStep(step).build())
                .addWorkflowPhase(aWorkflowPhase()
                                      .infraMappingId(INFRA_MAPPING_ID)
                                      .serviceId(SERVICE_ID)
                                      .deploymentType(PCF)
                                      .phaseSteps(asList(
                                          aPhaseStep(PCF_SETUP, WorkflowServiceHelper.PCF_SETUP).addStep(step).build()))
                                      .build())
                .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).addStep(step).build())
                .build())
        .linkedTemplateUuids(asList(savedTemplate.getUuid()))
        .build();
  }
}
