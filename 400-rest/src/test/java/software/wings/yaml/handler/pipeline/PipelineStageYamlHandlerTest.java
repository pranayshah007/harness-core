/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.pipeline;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.PipelineStage;
import software.wings.beans.Variable;
import software.wings.beans.Variable.VariableBuilder;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.workflow.ApprovalStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.PipelineStageYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class PipelineStageYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private PipelineStageYamlHandler pipelineStageYamlHandler;
  @Mock private YamlHelper yamlHelper;
  @Mock private AppService appService;
  @Mock private UserGroupService userGroupService;
  @Mock private WorkflowService workflowService;
  @Mock private ApprovalStepYamlBuilder approvalStepYamlBuilder;

  @UtilityClass
  private static class PipelineStageYamlFiles {
    private static final String Stage1 = "approvalStage.yaml";
    private static final String Stage2 = "envStage.yaml";
  }

  private static final String yamlFilePath = "Setup/Applications/APP_NAME/Pipelines/pipeline.yaml";
  private static final String resourcePath = "400-rest/src/test/resources/yaml";

  private static final String userGroupName = "USER_GROUP";
  private static final String workflowName = "WORKFLOW_NAME";

  @Before
  public void setup() {
    when(yamlHelper.getAppId(any(), any())).thenReturn(APP_ID);
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    when(userGroupService.fetchUserGroupByName(eq(ACCOUNT_ID), any()))
        .thenReturn(UserGroup.builder().uuid(USER_GROUP_ID).build());
    when(userGroupService.get(ACCOUNT_ID, USER_GROUP_ID)).thenReturn(UserGroup.builder().name(userGroupName).build());
    when(userGroupService.get(USER_GROUP_ID)).thenReturn(UserGroup.builder().name(userGroupName).build());

    doAnswer(invocationOnMock -> {
      Object[] args = invocationOnMock.getArguments();
      Map<String, Object> properties = (Map<String, Object>) args[2];
      properties.put((String) args[0], args[1]);
      return null;
    })
        .when(approvalStepYamlBuilder)
        .convertNameToIdForKnownTypes(any(), any(), any(), any(), any(), any());

    doAnswer(invocationOnMock -> {
      Object[] args = invocationOnMock.getArguments();
      Map<String, Object> properties = (Map<String, Object>) args[2];
      properties.put((String) args[0], args[1]);
      return null;
    })
        .when(approvalStepYamlBuilder)
        .convertIdToNameForKnownTypes(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testApprovalStageYaml() throws IOException {
    testCRUD(PipelineStageYamlFiles.Stage1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testEnvStageYaml() throws IOException {
    Workflow workflow =
        aWorkflow()
            .name(workflowName)
            .envId(ENV_ID)
            .uuid(WORKFLOW_ID)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow().withUserVariables(Collections.EMPTY_LIST).build())
            .build();
    when(workflowService.readWorkflowByName(any(), anyString())).thenReturn(workflow);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    testCRUD(PipelineStageYamlFiles.Stage2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testEnvStageYamlWithOneVariableWithEmptyValue() throws IOException {
    Variable emptyVariable = VariableBuilder.aVariable().name("var1").type(VariableType.TEXT).build();
    Workflow workflow =
        aWorkflow()
            .name(workflowName)
            .envId(ENV_ID)
            .uuid(WORKFLOW_ID)
            .orchestrationWorkflow(
                aBasicOrchestrationWorkflow().withUserVariables(Collections.singletonList(emptyVariable)).build())
            .build();
    when(workflowService.readWorkflowByName(any(), anyString())).thenReturn(workflow);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    testCRUD(PipelineStageYamlFiles.Stage2);
  }

  private void testCRUD(String yamlFileName) throws IOException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");

    ChangeContext<PipelineStage.Yaml> changeContext = getChangeContext(yamlString);
    PipelineStage.Yaml yaml = (PipelineStage.Yaml) getYaml(yamlString, PipelineStage.Yaml.class);
    changeContext.setYaml(yaml);
    PipelineStage pipelineStage = pipelineStageYamlHandler.toBean(changeContext);
    assertThat(pipelineStage).isNotNull();

    PipelineStage.Yaml displayYaml = pipelineStageYamlHandler.toYaml(pipelineStage, APP_ID);
    assertThat(displayYaml).isNotNull();

    String yamlContent = getYamlContent(displayYaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);
  }

  private ChangeContext<PipelineStage.Yaml> getChangeContext(String validYamlContent) {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<PipelineStage.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PIPELINE_STAGE);
    changeContext.setYamlSyncHandler(pipelineStageYamlHandler);
    return changeContext;
  }
}
