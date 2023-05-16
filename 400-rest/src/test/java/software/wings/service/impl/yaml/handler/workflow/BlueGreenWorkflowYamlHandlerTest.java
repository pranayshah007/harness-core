/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.MILOS;

import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructGKInfraDef;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BLUE_GREEN_INVALID_YAML_CONTENT;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BLUE_GREEN_INVALID_YAML_FILE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BLUE_GREEN_VALID_YAML_CONTENT_RESOURCE_PATH;
import static software.wings.service.impl.yaml.handler.workflow.WorkflowYamlConstant.BLUE_GREEN_VALID_YAML_FILE_PATH;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import software.wings.service.impl.workflow.creation.K8V2BlueGreenWorkflowCreator;
import software.wings.service.impl.workflow.creation.abstractfactories.AbstractWorkflowFactory;
import software.wings.service.impl.workflow.creation.abstractfactories.K8sV2WorkflowFactory;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.workflow.BlueGreenWorkflowYaml;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class BlueGreenWorkflowYamlHandlerTest extends WorkflowYamlHandlerTestBase {
  private String workflowName = "blueGreen";

  @InjectMocks @Inject private BlueGreenWorkflowYamlHandler yamlHandler;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private WorkflowServiceHelper workflowServiceHelper;
  @Spy @Inject private AbstractWorkflowFactory abstractWorkflowFactory;
  @InjectMocks @Inject private K8V2BlueGreenWorkflowCreator k8V2BlueGreenWorkflowCreator;

  @Before
  public void runBeforeTest() {
    setup(BLUE_GREEN_VALID_YAML_FILE_PATH, workflowName);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(limitCheckerFactory.getInstance(new Action(any(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new WingsTestConstants.MockChecker(true, ActionType.CREATE_WORKFLOW));
    when(workflowServiceHelper.isK8sV2Service(any(), any())).thenReturn(true);
    when(workflowServiceHelper.getCategory(any(), any())).thenReturn(AbstractWorkflowFactory.Category.K8S_V2);

    K8sV2WorkflowFactory k8sV2WorkflowFactory = mock(K8sV2WorkflowFactory.class);
    when(abstractWorkflowFactory.getWorkflowCreatorFactory(AbstractWorkflowFactory.Category.K8S_V2))
        .thenReturn(k8sV2WorkflowFactory);
    when(k8sV2WorkflowFactory.getWorkflowCreator(any())).thenReturn(k8V2BlueGreenWorkflowCreator);
    when(infrastructureDefinitionService.getInfraDefById(any(), any())).thenReturn(constructGKInfraDef());

    String validYamlContent = readYamlStringInFile(BLUE_GREEN_VALID_YAML_CONTENT_RESOURCE_PATH);
    ChangeContext<BlueGreenWorkflowYaml> changeContext =
        getChangeContext(validYamlContent, BLUE_GREEN_VALID_YAML_FILE_PATH, yamlHandler);

    BlueGreenWorkflowYaml yamlObject = (BlueGreenWorkflowYaml) getYaml(validYamlContent, BlueGreenWorkflowYaml.class);
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

    BlueGreenWorkflowYaml yaml = yamlHandler.toYaml(workflow, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("BLUE_GREEN");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    Workflow savedWorkflow = workflowService.readWorkflowByName(APP_ID, workflowName);
    assertThat(savedWorkflow).isNotNull();
    assertThat(workflowName).isEqualTo(savedWorkflow.getName());

    yamlHandler.delete(changeContext);

    Workflow deletedWorkflow = yamlHandler.get(ACCOUNT_ID, BLUE_GREEN_VALID_YAML_FILE_PATH);
    assertThat(deletedWorkflow).isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    testFailures(readYamlStringInFile(BLUE_GREEN_VALID_YAML_CONTENT_RESOURCE_PATH), BLUE_GREEN_VALID_YAML_FILE_PATH,
        BLUE_GREEN_INVALID_YAML_CONTENT, BLUE_GREEN_INVALID_YAML_FILE_PATH, yamlHandler, BlueGreenWorkflowYaml.class);
  }
}
