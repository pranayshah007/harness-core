/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_INVALID_YAML_CONTENT;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_INVALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_VALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_VALID_YAML_CONTENT_TEMPLATIZED_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_VALID_YAML_CONTENT_WITH_MULTILINE_USER_INPUT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BASIC_VALID_YAML_FILE_PATH_PREFIX;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.Workflow;
import software.wings.beans.concurrency.ConcurrencyStrategy.UnitType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.common.InfrastructureConstants;
import software.wings.utils.WingsTestConstants.MockChecker;
import software.wings.yaml.workflow.BasicWorkflowYaml;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 1/9/18
 */
public class BasicWorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @InjectMocks @Inject private BasicWorkflowYamlHandler yamlHandler;

  @Before
  public void runBeforeTest() {
    setup(BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic1.yaml", "basic1");
    setup(BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic2.yaml", "basic2");
    setup(BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic3.yaml", "basic3");
    setup(BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic4.yaml", "basic4");
    setup(BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic5.yaml", "basic5");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    testCRUD(readYamlStringInFile(BASIC_VALID_YAML_CONTENT_RESOURCE_PATH), "basic1");
    testCRUD(readYamlStringInFile(BASIC_VALID_YAML_CONTENT_TEMPLATIZED_RESOURCE_PATH), "basic2");
    testCRUDWithYamlWithMultilineUserInput();
  }

  private void testCRUDWithYamlWithMultilineUserInput() throws Exception {
    String yamlString = readYamlStringInFile(BASIC_VALID_YAML_CONTENT_WITH_MULTILINE_USER_INPUT_RESOURCE_PATH);

    for (int count = 0; count < 3; count++) {
      String workflowName = "basic" + Integer.toString(count + 3);
      ChangeContext<BasicWorkflowYaml> changeContext =
          getChangeContext(yamlString, BASIC_VALID_YAML_FILE_PATH_PREFIX + workflowName + ".yaml", yamlHandler);

      BasicWorkflowYaml yamlObject = (BasicWorkflowYaml) getYaml(yamlString, BasicWorkflowYaml.class);
      changeContext.setYaml(yamlObject);

      Workflow workflow = yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
      assertThat(workflow).isNotNull();
      assertThat(workflowName).isEqualTo(workflow.getName());

      BasicWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
      assertThat(yaml).isNotNull();
      assertThat(yaml.getType()).isEqualTo("BASIC");

      String yamlContent = getYamlContent(yaml);
      String finalYamlString = yamlContent.substring(0, yamlContent.length() - 1);
      assertThat(finalYamlString).isEqualTo(yamlString);
    }
  }

  private void testCRUD(String yamlString, String workflowName) throws IOException {
    ChangeContext<BasicWorkflowYaml> changeContext =
        getChangeContext(yamlString, BASIC_VALID_YAML_FILE_PATH_PREFIX + workflowName + ".yaml", yamlHandler);

    BasicWorkflowYaml yamlObject = (BasicWorkflowYaml) getYaml(yamlString, BasicWorkflowYaml.class);
    changeContext.setYaml(yamlObject);
    Workflow workflow;
    try {
      workflow = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage(), e);
    }
    assertThat(workflow).isNotNull();
    assertThat(workflowName).isEqualTo(workflow.getName());
    assertThat(workflow.getOrchestrationWorkflow()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy()).isNotNull();
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getUnitType()).isEqualTo(UnitType.INFRA);
    assertThat(workflow.getOrchestrationWorkflow().getConcurrencyStrategy().getResourceUnit())
        .isEqualTo(InfrastructureConstants.INFRA_ID_EXPRESSION);

    BasicWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("BASIC");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    // TODO find out why this couldn't be called
    //    Workflow savedWorkflow = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());
    try {
      yamlHandler.delete(changeContext);
    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage(), e);
    }
    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, BASIC_VALID_YAML_FILE_PATH_PREFIX);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    testFailures(readYamlStringInFile(BASIC_VALID_YAML_CONTENT_RESOURCE_PATH),
        BASIC_VALID_YAML_FILE_PATH_PREFIX + "basic.yaml", BASIC_INVALID_YAML_CONTENT, BASIC_INVALID_YAML_FILE_PATH,
        yamlHandler, BasicWorkflowYaml.class);
  }
}
