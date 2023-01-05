/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.template;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import software.wings.beans.template.TemplateType;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import com.mongodb.DBCursor;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class HttpTemplateProcessorTest extends TemplateBaseTestHelper {
  @Mock private WorkflowService workflowService;

  @Mock private MorphiaIterator<Workflow, Workflow> workflowIterator;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private Query<Workflow> query;
  @Mock private FieldEnd end;
  @Mock private DBCursor dbCursor;

  @Inject private HttpTemplateProcessor httpTemplateProcessor;
  @Inject private TemplateService templateService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldLoadDefaultHttpTemplates() {
    templateService.loadDefaultTemplates(TemplateType.HTTP, GLOBAL_ACCOUNT_ID, HARNESS_GALLERY);
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder);
    assertThat(template).isNotNull();
    assertThat(template.getTemplateObject()).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSaveHttpTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    HttpTemplate httpTemplate =
        HttpTemplate.builder().url("{Url}").method("GET").header("Authorization:${Header}").assertion("200 ok").build();
    Template template = Template.builder()
                            .templateObject(httpTemplate)
                            .folderId(parentFolder.getUuid())
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .name("Enable Instance")
                            .variables(asList(aVariable().type(TEXT).name("Url").mandatory(true).build(),
                                aVariable().type(TEXT).name("Header").mandatory(true).build()))
                            .build();
    Template savedTemplate = templateService.save(template);
    assertSavedTemplate(template, savedTemplate);
    HttpTemplate savedHttpTemplate = (HttpTemplate) savedTemplate.getTemplateObject();
    assertThat(savedHttpTemplate).isNotNull();
    assertThat(savedHttpTemplate.getAssertion()).isNotEmpty();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateHttpTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder);

    Template savedTemplate = templateService.save(template);

    assertSavedTemplate(template, savedTemplate);

    HttpTemplate savedHttpTemplate = (HttpTemplate) savedTemplate.getTemplateObject();
    assertThat(savedHttpTemplate).isNotNull();
    assertThat(savedHttpTemplate.getAssertion()).isNotEmpty();

    HttpTemplate updatedHttpTemplate = HttpTemplate.builder()
                                           .assertion(savedHttpTemplate.getAssertion())
                                           .url("http://${workflow.variables.F5_URL}/mgmt/tm/${foo}/members")
                                           .method(savedHttpTemplate.getMethod())
                                           .build();
    savedTemplate.setTemplateObject(updatedHttpTemplate);
    Template updatedTemplate = templateService.update(savedTemplate);

    updatedHttpTemplate = (HttpTemplate) updatedTemplate.getTemplateObject();
    assertThat(updatedTemplate).isNotNull();
    assertThat(updatedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(updatedTemplate.getVersion()).isEqualTo(2L);
    assertThat(updatedTemplate.getTemplateObject()).isNotNull();
    assertThat(updatedTemplate.getVariables()).extracting("name").contains("Url", "Header");
    assertThat(updatedTemplate.getKeywords()).isNotEmpty();
    assertThat(updatedTemplate.getKeywords()).contains(template.getName().toLowerCase());
    assertThat(updatedHttpTemplate).isNotNull();
  }

  private void assertSavedTemplate(Template template, Template savedTemplate) {
    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getKeywords()).isNotEmpty();
    assertThat(savedTemplate.getKeywords()).contains(template.getName().toLowerCase());
    assertThat(savedTemplate.getVersion()).isEqualTo(1);
    assertThat(savedTemplate.getVariables()).isNotEmpty();
    assertThat(savedTemplate.getVariables()).extracting("name").contains("Url", "Header");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotUpdateEntitiesIfNotLinked() {
    Template savedTemplate = createHttpTemplate();

    Workflow workflow = aWorkflow()
                            .name(WORKFLOW_NAME)
                            .appId(APP_ID)
                            .uuid(WORKFLOW_ID)
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                       .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                       .addWorkflowPhase(aWorkflowPhase()
                                                                             .infraMappingId(INFRA_MAPPING_ID)
                                                                             .serviceId(SERVICE_ID)
                                                                             .deploymentType(SSH)
                                                                             .build())
                                                       .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                       .build())
                            .linkedTemplateUuids(asList(savedTemplate.getUuid()))
                            .build();

    validateWorkflow(savedTemplate, workflow);
    verify(workflowService, times(0)).updateWorkflow(workflow, false);
  }

  private void validateWorkflow(Template savedTemplate, Workflow workflow) {
    on(httpTemplateProcessor).set("wingsPersistence", wingsPersistence);
    on(httpTemplateProcessor).set("workflowService", workflowService);

    when(wingsPersistence.createQuery(Workflow.class, excludeAuthority)).thenReturn(query);

    when(query.filter(WorkflowKeys.linkedTemplateUuids, savedTemplate.getUuid())).thenReturn(query);
    when(query.fetch()).thenReturn(workflowIterator);
    when(workflowIterator.getCursor()).thenReturn(dbCursor);
    when(workflowIterator.hasNext()).thenReturn(true).thenReturn(false);

    when(workflowIterator.next()).thenReturn(workflow);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

    templateService.updateLinkedEntities(savedTemplate);

    verify(workflowService).readWorkflow(APP_ID, WORKFLOW_ID);
  }

  private Template createHttpTemplate() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder);

    Template savedTemplate = templateService.save(template);

    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(GLOBAL_APP_ID);
    assertThat(savedTemplate.getVariables()).extracting("name").contains("Url", "Header");

    HttpTemplate savedHttpTemplate = (HttpTemplate) savedTemplate.getTemplateObject();
    assertThat(savedHttpTemplate).isNotNull();
    assertThat(savedHttpTemplate.getAssertion()).isNotEmpty();
    return savedTemplate;
  }

  private Template getTemplate(TemplateFolder parentFolder) {
    return getTemplate(parentFolder, GLOBAL_APP_ID);
  }

  private Template getTemplate(TemplateFolder parentFolder, String appId) {
    HttpTemplate httpTemplate = HttpTemplate.builder()
                                    .url("http://$workflow.variables.F5_URL}/mgmt/tm/${foo}/members")
                                    .method("GET")
                                    .body("{ \"kind\": ${LB_TYPE}}")
                                    .header("Authorization:${workflow.variables.F5_AUTH_BASE64}")
                                    .assertion("200 ok")
                                    .build();
    return Template.builder()
        .templateObject(httpTemplate)
        .folderId(parentFolder.getUuid())
        .appId(appId)
        .accountId(GLOBAL_ACCOUNT_ID)
        .name("Enable Instance")
        .variables(asList(aVariable().type(TEXT).name("Url").mandatory(true).build(),
            aVariable().type(TEXT).name("Header").mandatory(true).build()))
        .build();
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
    assertThat(template).isNotNull();
    updateLinkedEntities(template);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldUpdateEntitiesWhenLinkedAppTemplateUpdated() {
    TemplateGallery templateGallery =
        templateGalleryService.getByAccount(GLOBAL_ACCOUNT_ID, templateGalleryService.getAccountGalleryKey());
    TemplateFolder parentFolder =
        templateFolderService.getByFolderPath(GLOBAL_ACCOUNT_ID, HARNESS_GALLERY, templateGallery.getUuid());
    Template template = getTemplate(parentFolder, APP_ID);
    assertThat(template).isNotNull();
    updateLinkedEntities(template);
  }

  private void updateLinkedEntities(Template template) {
    Template savedTemplate = templateService.save(template);

    assertThat(savedTemplate).isNotNull();
    assertThat(savedTemplate.getAppId()).isNotNull().isEqualTo(template.getAppId());
    assertThat(savedTemplate.getVariables()).extracting("name").contains("Url", "Header");

    HttpTemplate savedHttpTemplate = (HttpTemplate) savedTemplate.getTemplateObject();
    assertThat(savedHttpTemplate).isNotNull();
    assertThat(savedHttpTemplate.getAssertion()).isNotEmpty();
    GraphNode step = httpTemplateProcessor.constructEntityFromTemplate(savedTemplate, EntityType.WORKFLOW);
    step.setTemplateVersion(LATEST_TAG);

    Workflow workflow = generateWorkflow(savedTemplate, step);

    validateWorkflow(savedTemplate, workflow);
    verify(workflowService).updateWorkflow(workflow, false);
  }
}
