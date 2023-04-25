/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.pipeline;

import static io.harness.rule.OwnerRule.DHRUV;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.api.DeploymentType.SSH;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.POST_DEPLOYMENT;
import static software.wings.beans.PhaseStepType.PRE_DEPLOYMENT;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.ArtifactType.WAR;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.api.CloudProviderType;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.Yaml;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.rules.SetupScheduler;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.yaml.WorkflowYAMLHelper;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.handler.workflow.ApprovalStepYamlBuilder;
import software.wings.service.impl.yaml.handler.workflow.PipelineStageYamlHandler;
import software.wings.service.impl.yaml.handler.workflow.PipelineYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.sm.StateType;
import software.wings.utils.WingsTestConstants.MockChecker;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * @author rktummala on 1/9/18
 */
@OwnedBy(HarnessTeam.CDC)
@SetupScheduler
public class PipelineYamlHandler2Test extends YamlHandlerTestBase {
  private final String APP_NAME = "app1";
  private final String PIPELINE_NAME = "pipeline1";
  private final String resourcePath = "400-rest/src/test/resources/pipeline";
  @Mock AppService appService;
  @Mock YamlGitService yamlGitService;
  @Mock InfrastructureMappingService infrastructureMappingService;
  @Mock InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock ServiceResourceService serviceResourceService;
  @InjectMocks @Inject YamlHelper yamlHelper;
  @InjectMocks @Inject YamlDirectoryService yamlDirectoryService;
  @InjectMocks @Inject WorkflowServiceHelper workflowServiceHelper;
  @Mock private Account account;
  @Mock private AccountService accountService;
  @Mock private EnvironmentService environmentService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private UserGroupService userGroupService;
  @Mock private PipelineService pipelineService;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowYAMLHelper workflowYAMLHelper;
  @InjectMocks @Inject private PipelineYamlHandler yamlHandler;
  @InjectMocks @Inject private PipelineStageYamlHandler pipelineStageYamlHandler;
  @Mock private ApprovalStepYamlBuilder approvalStepYamlBuilder;

  private String validYamlFilePath = "Setup/Applications/" + APP_NAME + "/Pipelines/" + PIPELINE_NAME + ".yaml";

  ArgumentCaptor<Pipeline> captor = ArgumentCaptor.forClass(Pipeline.class);

  @Before
  public void setUp() throws IOException {
    InfrastructureDefinition awsInfraDefinition = InfrastructureDefinition.builder()
                                                      .uuid(INFRA_DEFINITION_ID)
                                                      .deploymentType(SSH)
                                                      .cloudProviderType(CloudProviderType.AWS)
                                                      .infrastructure(AwsInstanceInfrastructure.builder().build())
                                                      .build();
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(awsInfraDefinition);

    UserGroup userGroup = new UserGroup();
    userGroup.setName("Account Administrator");
    userGroup.setUuid("dIyaCXXVRp65abGOlN5Fmg");
    UserGroup userGroup2 = new UserGroup();
    userGroup2.setName("Non-Production Support");
    userGroup2.setUuid("s6dYbwVXQ1-Bgq234fbznw");
    when(userGroupService.fetchUserGroupByName(any(), eq("Account Administrator"))).thenReturn(userGroup);
    when(userGroupService.fetchUserGroupByName(any(), eq("test")))
        .thenReturn(UserGroup.builder().name("test").uuid("test").build());
    when(userGroupService.fetchUserGroupByName(any(), eq("Non-Production Support"))).thenReturn(userGroup2);
    when(userGroupService.get(any(), eq("test"))).thenReturn(UserGroup.builder().name("test").uuid("test").build());
    when(userGroupService.get(any(), eq("dIyaCXXVRp65abGOlN5Fmg"))).thenReturn(userGroup);
    when(userGroupService.get(any(), eq("s6dYbwVXQ1-Bgq234fbznw"))).thenReturn(userGroup2);

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

    Service service = Service.builder().name(SERVICE_NAME).uuid(SERVICE_ID).artifactType(WAR).build();
    when(serviceResourceService.get(APP_ID, SERVICE_ID, false)).thenReturn(service);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(accountService.get(anyString())).thenReturn(account);
    when(limitCheckerFactory.getInstance(new Action(Mockito.anyString(), ActionType.CREATE_WORKFLOW)))
        .thenReturn(new MockChecker(true, ActionType.CREATE_WORKFLOW));

    Application application =
        Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(any(), any())).thenReturn(application);

    when(appService.get(APP_ID)).thenReturn(application);

    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).appId(APP_ID).build();
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(environment);

    when(environmentService.getEnvironmentByName(any(), any(), anyBoolean())).thenReturn(environment);
    when(serviceResourceService.getServiceByName(any(), any(), anyBoolean())).thenReturn(service);

    when(yamlGitService.get(any(), any(), any())).thenReturn(null);

    Workflow workflow1 = aWorkflow()
                             .envId(ENV_ID)
                             .name(WORKFLOW_NAME)
                             .appId(APP_ID)
                             .workflowType(WorkflowType.ORCHESTRATION)
                             .orchestrationWorkflow(aCanaryOrchestrationWorkflow()
                                                        .withPreDeploymentSteps(aPhaseStep(PRE_DEPLOYMENT).build())
                                                        .addWorkflowPhase(aWorkflowPhase()
                                                                              .infraDefinitionId(INFRA_DEFINITION_ID)
                                                                              .serviceId(SERVICE_ID)
                                                                              .deploymentType(SSH)
                                                                              .build())
                                                        .withPostDeploymentSteps(aPhaseStep(POST_DEPLOYMENT).build())
                                                        .build())
                             .build();
    workflowService.createWorkflow(workflow1);

    when(workflowYAMLHelper.getWorkflowVariableValueBean(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(4, String.class));
    when(workflowYAMLHelper.getWorkflowVariableValueYaml(any(), any(), any(), anyBoolean()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1, String.class));
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testCRUDAndGetUserGroup() throws Exception {
    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineUserGroup));
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testCRUDAndGetUserGroupTemplatized() throws Exception {
    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineUserGroupTemplatized));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDPipelineTemplatized() throws Exception {
    List<Variable> variables = Arrays.asList(aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build(),
        aVariable().name("Service").entityType(EntityType.SERVICE).build(),
        aVariable().name("InfraDefinition_KUBERNETES").entityType(EntityType.INFRASTRUCTURE_DEFINITION).build());

    Workflow workflow = aWorkflow()
                            .name("K8s-roll")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineTemplatized));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDPipelineRuntime() throws Exception {
    List<Variable> variables = Arrays.asList(aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build(),
        aVariable().name("Service").entityType(EntityType.SERVICE).build(),
        aVariable().name("InfraDefinition_KUBERNETES").entityType(EntityType.INFRASTRUCTURE_DEFINITION).build());

    Workflow workflow = aWorkflow()
                            .name("K8s-roll")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineRuntime));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDPipelineNewRelic() throws Exception {
    List<Variable> variables =
        Arrays.asList(aVariable().name("NewRelic_Server").entityType(EntityType.NEWRELIC_CONFIGID).build(),
            aVariable().name("NewRelic_Application").entityType(EntityType.NEWRELIC_APPID).build());

    Workflow workflow = aWorkflow()
                            .name("New-relic")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineNewRelic));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDPipelineThreeStages() throws Exception {
    List<Variable> variables =
        Arrays.asList(aVariable().name("NewRelic_Server").entityType(EntityType.NEWRELIC_CONFIGID).build(),
            aVariable().name("NewRelic_Application").entityType(EntityType.NEWRELIC_APPID).build());

    Workflow workflow = aWorkflow()
                            .name("New-relic")
                            .uuid("New-relic")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();
    when(workflowService.readWorkflowByName(any(), eq("New-relic"))).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), eq("New-relic"))).thenReturn(workflow);
    List<Variable> variables2 = Arrays.asList(aVariable().name("wvar").type(VariableType.TEXT).build(),
        aVariable().name("wvar2").type(VariableType.TEXT).build());
    Workflow workflow2 =
        aWorkflow()
            .name("Build")
            .uuid("Build")
            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables2).build())
            .build();
    when(workflowService.readWorkflowByName(any(), eq("Build"))).thenReturn(workflow2);
    when(workflowService.readWorkflow(any(), eq("Build"))).thenReturn(workflow2);

    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineThreeStages));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDSnowApproval() throws Exception {
    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineSnowApproval));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDApprovalVariable() throws Exception {
    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineApprovalVariable));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDMissingStageName() throws Exception {
    String validYamlContent = readYamlStringInFile(InValidPipelineFiles.pipelineWithoutStageName);
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PIPELINE);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow =
        aWorkflow().name("K8s-roll").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);

    when(pipelineService.save(any())).thenThrow(new InvalidRequestException("Stage name must not be empty"));
    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Stage name must not be empty");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDInvalidWorkflowName() {
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(null);

    assertThatThrownBy(() -> testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineTemplatized)))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Invalid workflow with the given name:K8s-roll");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDInvalidUserGroup() {
    when(userGroupService.fetchUserGroupByName(any(), eq("test"))).thenReturn(null);
    assertThatThrownBy(() -> testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineApprovalVariable)))
        .isInstanceOf(GeneralException.class)
        .hasMessage("User group test doesn't exist");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDInvalidTimeout() throws IOException {
    String validYamlContent = readYamlStringInFile(InValidPipelineFiles.pipelineApprovalWithInvalidTimeout);
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PIPELINE);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    Workflow workflow =
        aWorkflow().name("K8s-roll").orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);

    when(pipelineService.save(any())).thenThrow(new InvalidRequestException("Timeout must be an integer"));
    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Timeout must be an integer");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDInvalidEntityName() {
    List<Variable> variables = Arrays.asList(aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build(),
        aVariable().name("Service").entityType(EntityType.SERVICE).build(),
        aVariable().name("InfraDefinition_KUBERNETES").entityType(EntityType.INFRASTRUCTURE_DEFINITION).build());

    Workflow workflow = aWorkflow()
                            .name("K8s-roll")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();

    when(workflowYAMLHelper.getWorkflowVariableValueBean(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenThrow(new InvalidRequestException("Environment qa does not exist"));

    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    assertThatThrownBy(() -> testCRUDpipeline(readYamlStringInFile(InValidPipelineFiles.pipelineInvalidEntityName)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Environment qa does not exist");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testCRUDPipelineTemplatizedEdit() throws Exception {
    List<Variable> variables = Arrays.asList(aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build(),
        aVariable().name("Service").entityType(EntityType.SERVICE).build(),
        aVariable().name("InfraDefinition_KUBERNETES").entityType(EntityType.INFRASTRUCTURE_DEFINITION).build());

    Workflow workflow = aWorkflow()
                            .name("K8s-roll")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().withUserVariables(variables).build())
                            .build();
    when(workflowService.readWorkflowByName(any(), any())).thenReturn(workflow);
    when(workflowService.readWorkflow(any(), any())).thenReturn(workflow);

    when(pipelineService.update(any(), eq(false), eq(true))).thenAnswer(invocationOnMock -> {
      Pipeline pipeline = invocationOnMock.getArgument(0, Pipeline.class);
      pipeline.getPipelineStages().get(0).setName("Original name");
      pipeline.getPipelineStages().get(0).getPipelineStageElements().get(0).setWorkflowVariables(
          Collections.emptyMap());
      pipeline.getPipelineStages()
          .stream()
          .flatMap(ps -> ps.getPipelineStageElements().stream())
          .filter(pse -> pse.getType().equals(StateType.ENV_STATE.name()))
          .forEach(pse -> pse.getProperties().put("workflowId", pse.getName()));
      return pipeline;
    });

    testCRUDpipeline(readYamlStringInFile(ValidPipelineFiles.pipelineTemplatized));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnCRUDFromYaml() throws IOException {
    String validYamlContent = readYamlStringInFile(ValidPipelineFiles.pipelineUserGroup);
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);
    gitFileChange.setSyncFromGit(true);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PIPELINE);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    when(pipelineService.save(any())).thenAnswer(invocationOnMock -> {
      Pipeline pipeline = invocationOnMock.getArgument(0, Pipeline.class);
      pipeline.getPipelineStages()
          .stream()
          .flatMap(ps -> ps.getPipelineStageElements().stream())
          .filter(pse -> pse.getType().equals(StateType.ENV_STATE.name()))
          .forEach(pse -> pse.getProperties().put("workflowId", pse.getName()));
      return pipeline;
    });

    yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(pipelineService).save(captor.capture());
    Pipeline capturedPipeline = captor.getValue();
    assertThat(capturedPipeline).isNotNull();
    assertThat(capturedPipeline.isSyncFromGit()).isTrue();

    when(pipelineService.getPipelineByName(anyString(), anyString()))
        .thenReturn(Pipeline.builder().appId(APP_ID).uuid(PIPELINE_ID).build());
    yamlHandler.delete(changeContext);
    verify(pipelineService).deleteByYamlGit(APP_ID, PIPELINE_ID, true);
  }

  private void testCRUDpipeline(String validYamlContent) throws IOException {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PIPELINE);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    when(pipelineService.save(any())).thenAnswer(invocationOnMock -> {
      Pipeline pipeline = invocationOnMock.getArgument(0, Pipeline.class);
      pipeline.getPipelineStages()
          .stream()
          .flatMap(ps -> ps.getPipelineStageElements().stream())
          .filter(pse -> pse.getType().equals(StateType.ENV_STATE.name()))
          .forEach(pse -> pse.getProperties().put("workflowId", pse.getName()));
      return pipeline;
    });
    Pipeline savedPipeline = yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext));

    Yaml yaml = yamlHandler.toYaml(savedPipeline, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    yamlHandler.delete(changeContext);

    Pipeline pipeline = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(pipeline).isNull();
  }

  protected String readYamlStringInFile(String yamlContentResourcePath) throws IOException {
    File yamlFile = new File(resourcePath + PATH_DELIMITER + yamlContentResourcePath);
    assertThat(yamlFile).isNotNull();
    return FileUtils.readFileToString(yamlFile, "UTF-8");
  }

  @UtilityClass
  private static class ValidPipelineFiles {
    private static final String pipelineTemplatized = "pipelineTemplatized.yaml";
    private static final String pipelineRuntime = "pipelineRuntimeVars.yaml";
    private static final String pipelineNewRelic = "pipelineNewRelic.yaml";
    private static final String pipelineThreeStages = "pipelineThreeStages.yaml";
    private static final String pipelineSnowApproval = "pipelineSnowApproval.yaml";
    private static final String pipelineApprovalVariable = "pipelineApprovalVariable.yaml";
    private static final String pipelineUserGroup = "pipelineUserGroup.yaml";
    private static final String pipelineUserGroupTemplatized = "pipelineUserGroupTemplatized.yaml";
  }

  @UtilityClass
  private static class InValidPipelineFiles {
    private static final String pipelineWithoutStageName = "pipelineWithoutStageName.yaml";
    private static final String pipelineApprovalWithInvalidTimeout = "pipelineApprovalWithInvalidTimeOut.yaml";
    private static final String pipelineInvalidEntityName = "pipelineInvalidEntityName.yaml";
  }
}
