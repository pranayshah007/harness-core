/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DHRUV;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_INVALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_INVALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_JIRA_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_LINKED_SHELL_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_SERVICENOW_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_YAML_CONTENT_INFRA_DEF_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_YAML_USER_GROUP_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BUILD_VALID_YAML_USER_GROUP_TEMPLATIZED2_RESOURCE_PATH;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.Workflow;
import software.wings.beans.template.Template;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.intfc.template.TemplateService;
import software.wings.utils.WingsTestConstants.MockChecker;
import software.wings.yaml.workflow.BuildWorkflowYaml;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 1/10/18
 */
@OwnedBy(CDC)
public class BuildWorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  private String workflowName = "build";

  @InjectMocks @Inject private BuildWorkflowYamlHandler yamlHandler;
  @Mock private WorkflowServiceTemplateHelper workflowServiceTemplateHelper;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private TemplateService templateService;
  @Mock private FeatureFlagService featureFlagService;
  @Before
  public void runBeforeTest() {
    doNothing().when(workflowServiceTemplateHelper).populatePropertiesFromWorkflow(any());
    doNothing().when(workflowServiceTemplateHelper).setServiceTemplateExpressionMetadata(any(), any());
    setup(BUILD_VALID_YAML_FILE_PATH, workflowName);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    String yamlContentFromFile = readYamlStringInFile(BUILD_VALID_YAML_CONTENT_RESOURCE_PATH);
    ChangeContext<BuildWorkflowYaml> changeContext =
        getChangeContext(yamlContentFromFile, BUILD_VALID_YAML_FILE_PATH, yamlHandler);

    BuildWorkflowYaml yamlObject = (BuildWorkflowYaml) getYaml(yamlContentFromFile, BuildWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());

    BuildWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("BUILD");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlContent);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    // TODO find out why this couldn't be called
    //    Workflow savedWorkflow = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, BUILD_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testCRUDAndGetInfrDef() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    String yamlStringInFile = readYamlStringInFile(BUILD_VALID_YAML_CONTENT_INFRA_DEF_RESOURCE_PATH);
    ChangeContext<BuildWorkflowYaml> changeContext =
        getChangeContext(yamlStringInFile, BUILD_VALID_YAML_FILE_PATH, yamlHandler);

    BuildWorkflowYaml yamlObject = (BuildWorkflowYaml) getYaml(yamlStringInFile, BuildWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());

    BuildWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("BUILD");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlStringInFile);

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, BUILD_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    testFailures(readYamlStringInFile(BUILD_VALID_YAML_CONTENT_RESOURCE_PATH), BUILD_VALID_YAML_FILE_PATH,
        BUILD_INVALID_YAML_CONTENT_RESOURCE_PATH, BUILD_INVALID_YAML_FILE_PATH, yamlHandler, BuildWorkflowYaml.class);
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testCRUDForUserGroupTemplatized() throws Exception {
    testCRUD(BUILD_VALID_YAML_USER_GROUP_TEMPLATIZED2_RESOURCE_PATH);
  }

  private void testCRUD(String buildValidYamlUserGroupTemplatized2ResourcePath)
      throws IOException, io.harness.exception.HarnessException {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    String yamlFileContent = readYamlStringInFile(buildValidYamlUserGroupTemplatized2ResourcePath);
    ChangeContext<BuildWorkflowYaml> changeContext =
        getChangeContext(yamlFileContent, BUILD_VALID_YAML_FILE_PATH, yamlHandler);

    BuildWorkflowYaml yamlObject = (BuildWorkflowYaml) getYaml(yamlFileContent, BuildWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());

    BuildWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("BUILD");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlFileContent);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, BUILD_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testCRUDForUserGroup() throws Exception {
    testCRUD(BUILD_VALID_YAML_USER_GROUP_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDJira() throws Exception {
    testCRUD(BUILD_VALID_JIRA_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDServiceNow() throws Exception {
    testCRUD(BUILD_VALID_SERVICENOW_RESOURCE_PATH);
  }
  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDTemplateLibraryShellScript() throws Exception {
    when(templateService.fetchTemplateIdFromUri(any(), any(), any())).thenReturn("template_id");
    when(templateService.fetchTemplateVersionFromUri(any(), any())).thenReturn(null);
    when(templateService.makeNamespacedTemplareUri(any(), any())).thenReturn("App/harness-dev-3/git-shell:latest");
    when(templateService.get("template_id")).thenReturn(Template.builder().build());
    testCRUD(BUILD_VALID_LINKED_SHELL_RESOURCE_PATH);
  }
}
