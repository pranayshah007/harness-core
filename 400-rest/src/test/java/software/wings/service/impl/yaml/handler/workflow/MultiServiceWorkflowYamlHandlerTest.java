/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.MULTI_SERVICE_INVALID_YAML_CONTENT;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.MULTI_SERVICE_INVALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.MULTI_SERVICE_VALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.MULTI_SERVICE_VALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.MULTI_SERVICE_VARIABLE_OVERRIDE_RESOURCE_PATH;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.MultiServiceOrchestrationWorkflow;
import software.wings.beans.NameValuePair;
import software.wings.beans.Workflow;
import software.wings.beans.concurrency.ConcurrencyStrategy.UnitType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.utils.WingsTestConstants.MockChecker;
import software.wings.yaml.workflow.MultiServiceWorkflowYaml;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 1/10/18
 */
public class MultiServiceWorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  private String workflowName = "multiService";

  @InjectMocks @Inject private MultiServiceWorkflowYamlHandler yamlHandler;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  @Before
  public void runBeforeTest() {
    setup(MULTI_SERVICE_VALID_YAML_FILE_PATH, workflowName);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    String yamlContentFromFile = readYamlStringInFile(MULTI_SERVICE_VALID_YAML_CONTENT_RESOURCE_PATH);
    ChangeContext<MultiServiceWorkflowYaml> changeContext =
        getChangeContext(yamlContentFromFile, MULTI_SERVICE_VALID_YAML_FILE_PATH, yamlHandler);

    MultiServiceWorkflowYaml yamlObject =
        (MultiServiceWorkflowYaml) getYaml(yamlContentFromFile, MultiServiceWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getUnitType()).isEqualTo(UnitType.NONE);

    MultiServiceWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("MULTI_SERVICE");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlContentFromFile);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    // TODO find out why this couldn't be called
    //    Workflow savedWorkflow = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, MULTI_SERVICE_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceVariableOverride() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    String yamlContentFromFile = readYamlStringInFile(MULTI_SERVICE_VARIABLE_OVERRIDE_RESOURCE_PATH);
    ChangeContext<MultiServiceWorkflowYaml> changeContext =
        getChangeContext(yamlContentFromFile, MULTI_SERVICE_VALID_YAML_FILE_PATH, yamlHandler);

    MultiServiceWorkflowYaml yamlObject =
        (MultiServiceWorkflowYaml) getYaml(yamlContentFromFile, MultiServiceWorkflowYaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();
    MultiServiceOrchestrationWorkflow orchestrationWorkflow =
        (MultiServiceOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    assertThat(orchestrationWorkflow.getWorkflowPhases()).isNotNull().hasSize(2);
    assertThat(orchestrationWorkflow.getWorkflowPhases().get(0).getVariableOverrides())
        .isNotNull()
        .hasSize(3)
        .containsExactly(NameValuePair.builder().name("Var1").value("").build(),
            NameValuePair.builder().name("Var2").value("").build(),
            NameValuePair.builder().name("Var3").value("value").build());
    assertThat(orchestrationWorkflow.getWorkflowPhases().get(1).getVariableOverrides()).isEmpty();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    testFailures(readYamlStringInFile(MULTI_SERVICE_VALID_YAML_CONTENT_RESOURCE_PATH),
        MULTI_SERVICE_VALID_YAML_FILE_PATH, MULTI_SERVICE_INVALID_YAML_CONTENT, MULTI_SERVICE_INVALID_YAML_FILE_PATH,
        yamlHandler, MultiServiceWorkflowYaml.class);
  }
}
