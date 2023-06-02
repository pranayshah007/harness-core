/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_BAMBOO_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_GCB_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_INVALID_YAML_CONTENT;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_INVALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_JENKINS_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_RESOURCE_CONSTRAINT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_RESOURCE_CONSTRAINT_RESOURCE_PATH2;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_VALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.ROLLING_VALID_YAML_FILE_PATH;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.Workflow;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.beans.yaml.ChangeContext;
import software.wings.common.InfrastructureConstants;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.workflow.RollingWorkflowYaml;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class RollingWorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  private String workflowName = "rolling";

  @InjectMocks @Inject private RollingWorkflowYamlHandler yamlHandler;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Inject @Spy private WorkflowServiceHelper workflowServiceHelperSpy;
  @Mock WorkflowServiceHelper workflowServiceHelper;

  @Before
  public void runBeforeTest() {
    setup(ROLLING_VALID_YAML_FILE_PATH, workflowName);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    testCRUDrolling(ROLLING_VALID_YAML_CONTENT_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRollingJenkins() throws Exception {
    testCRUDrolling(ROLLING_JENKINS_YAML_CONTENT_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRollingGcb() throws Exception {
    testCRUDrolling(ROLLING_GCB_YAML_CONTENT_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRollingBamboo() throws Exception {
    testCRUDrolling(ROLLING_BAMBOO_YAML_CONTENT_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testResourceConstraint() throws Exception {
    testCRUDrolling(ROLLING_RESOURCE_CONSTRAINT_RESOURCE_PATH);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testRollingBambooEdit() throws Exception {
    String yamlFileContent1 = readYamlStringInFile(ROLLING_RESOURCE_CONSTRAINT_RESOURCE_PATH);
    ChangeContext<RollingWorkflowYaml> changeContext1 =
        getChangeContext(yamlFileContent1, ROLLING_VALID_YAML_FILE_PATH, yamlHandler);

    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new WingsTestConstants.MockChecker(true, ActionType.CREATE_WORKFLOW));
    doReturn(true).when(workflowServiceHelperSpy).isK8sV2Service(any(), any());
    when(workflowServiceHelperSpy.getCategory(any(), any())).thenReturn(AbstractWorkflowFactory.Category.GENERAL);
    on(workflowService).set("workflowServiceHelper", workflowServiceHelperSpy);

    RollingWorkflowYaml yamlObject1 = (RollingWorkflowYaml) getYaml(yamlFileContent1, RollingWorkflowYaml.class);
    changeContext1.setYaml(yamlObject1);

    Workflow workflow1 = yamlHandler.upsertFromYaml(changeContext1, asList(changeContext1));

    when(yamlHelper.getWorkflow(anyString(), anyString())).thenReturn(workflow1);

    String yamlFileContent = readYamlStringInFile(ROLLING_RESOURCE_CONSTRAINT_RESOURCE_PATH2);
    ChangeContext<RollingWorkflowYaml> changeContext =
        getChangeContext(yamlFileContent, ROLLING_VALID_YAML_FILE_PATH, yamlHandler);

    RollingWorkflowYaml yamlObject = (RollingWorkflowYaml) getYaml(yamlFileContent, RollingWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));

    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getUnitType())
        .isEqualTo(ConcurrencyStrategy.UnitType.INFRA);
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getResourceUnit())
        .isEqualTo(InfrastructureConstants.INFRA_ID_EXPRESSION);

    RollingWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("ROLLING");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlFileContent);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());
  }

  private void testCRUDrolling(String rollingYamlContentResourcePath)
      throws IOException, io.harness.exception.HarnessException {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new WingsTestConstants.MockChecker(true, ActionType.CREATE_WORKFLOW));
    when(workflowServiceHelper.isK8sV2Service(any(), any())).thenReturn(true);
    when(workflowServiceHelper.getCategory(any(), any())).thenReturn(AbstractWorkflowFactory.Category.GENERAL);

    String yamlFileContent = readYamlStringInFile(rollingYamlContentResourcePath);
    ChangeContext<RollingWorkflowYaml> changeContext =
        getChangeContext(yamlFileContent, ROLLING_VALID_YAML_FILE_PATH, yamlHandler);

    RollingWorkflowYaml yamlObject = (RollingWorkflowYaml) getYaml(yamlFileContent, RollingWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));

    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getUnitType())
        .isEqualTo(ConcurrencyStrategy.UnitType.INFRA);
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getResourceUnit())
        .isEqualTo(InfrastructureConstants.INFRA_ID_EXPRESSION);

    RollingWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("ROLLING");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlFileContent);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, ROLLING_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    testFailures(readYamlStringInFile(ROLLING_VALID_YAML_CONTENT_RESOURCE_PATH), ROLLING_VALID_YAML_FILE_PATH,
        ROLLING_INVALID_YAML_CONTENT, ROLLING_INVALID_YAML_FILE_PATH, yamlHandler, RollingWorkflowYaml.class);
  }
}
