/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.MASKED;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.states.pcf.PcfStateTestHelper.ORG;
import static software.wings.sm.states.pcf.PcfStateTestHelper.SPACE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.URL;
import static software.wings.utils.WingsTestConstants.USER_NAME_DECRYPTED;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.pcf.PcfPluginStateExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.template.TemplateUtils;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.common.InfrastructureConstants;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ServiceHelper;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.collect.ImmutableMap;
import dev.morphia.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PcfPluginStateTest extends WingsBaseTest {
  private static final String BASE_URL = "https://env.harness.io/";

  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private ServiceHelper serviceHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private transient ServiceTemplateHelper serviceTemplateHelper;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private TemplateUtils templateUtils;
  @Mock private StateExecutionService stateExecutionService;
  @InjectMocks @Spy private PcfStateHelper pcfStateHelper;
  @Mock private MainConfiguration configuration;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Spy @InjectMocks private PcfPluginState pcfPluginState = new PcfPluginState("name");
  private PcfStateTestHelper pcfStateTestHelper = new PcfStateTestHelper();
  private ServiceElement serviceElement = pcfStateTestHelper.getServiceElement();

  @InjectMocks private PhaseElement phaseElement = pcfStateTestHelper.getPhaseElement(serviceElement);

  private String outputName = InfrastructureConstants.PHASE_INFRA_MAPPING_KEY_NAME + phaseElement.getUuid();

  private SweepingOutputInstance sweepingOutputInstance =
      SweepingOutputInstance.builder()
          .appId(APP_ID)
          .name(outputName)
          .uuid(generateUuid())
          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
          .stateExecutionId(null)
          .pipelineExecutionId(null)
          .value(InfraMappingSweepingOutput.builder().infraMappingId(INFRA_MAPPING_ID).build())
          .build();

  private SettingAttribute pcfConfig = aSettingAttribute()
                                           .withValue(PcfConfig.builder()
                                                          .endpointUrl(URL)
                                                          .password(PASSWORD)
                                                          .username(USER_NAME_DECRYPTED)
                                                          .accountId(ACCOUNT_ID)
                                                          .build())
                                           .build();
  private Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();

  private WorkflowStandardParams workflowStandardParams = pcfStateTestHelper.getWorkflowStandardParams();

  private StateExecutionInstance stateExecutionInstance =
      pcfStateTestHelper.getStateExecutionInstanceForPluginState(workflowStandardParams, phaseElement, serviceElement);

  private List<ServiceVariable> serviceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("value2".toCharArray())
          .build());

  private List<ServiceVariable> safeDisplayServiceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("*******".toCharArray())
          .build());

  private Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();

  private Artifact artifact =
      anArtifact()
          .withArtifactSourceName("source")
          .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "bn")))
          .withArtifactStreamId(ARTIFACT_STREAM_ID)
          .build();

  private Service service = Service.builder()
                                .appId(APP_ID)
                                .uuid(SERVICE_ID)
                                .name(SERVICE_NAME)
                                .artifactStreamIds(singletonList(ARTIFACT_STREAM_ID))
                                .build();

  private ExecutionContext context;

  @Before
  public void setup() throws IllegalAccessException {
    when(appService.get(APP_ID)).thenReturn(app);
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(app);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.get(SERVICE_ID)).thenReturn(service);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.SETUP).withName("Setup Service Cluster").build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "Setup Service Cluster"))
        .thenReturn(serviceCommand);

    EmbeddedUser currentUser = EmbeddedUser.builder().name("test").email("test@harness.io").build();
    workflowStandardParams.setCurrentUser(currentUser);

    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);
    when(activityService.get(any(), any())).thenReturn(activity);

    InfrastructureMapping infrastructureMapping = PcfInfrastructureMapping.builder()
                                                      .organization(ORG)
                                                      .space(SPACE)
                                                      .computeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                      .build();
    infrastructureMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);
    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.PCF)
            .uuid(INFRA_DEFINITION_ID)
            .infrastructure(PcfInfraStructure.builder().organization(ORG).space(SPACE).build())
            .build();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);

    when(settingsService.get(any())).thenReturn(pcfConfig);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(serviceVariableList);
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, MASKED))
        .thenReturn(safeDisplayServiceVariableList);
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("settingsService", settingsService);
    on(context).set("evaluator", evaluator);
    on(context).set("sweepingOutputService", sweepingOutputService);

    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    doNothing().when(serviceHelper).addPlaceholderTexts(any());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        spy(new WorkflowStandardParamsExtensionService(appService, null, artifactService, environmentService,
            artifactStreamServiceBindingService, null, featureFlagService));

    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(pcfPluginState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, workflowExecutionService, artifactService, artifactStreamService,
        applicationManifestService, featureFlagService, null, workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_findPathFromScript() {
    String script =
        "echo '${service.manifest.repoRoot}/abc/test any text sfkjsdfk \n /wrong/path \\${service.manifest.repoRoot}/xyz.json some text' ";
    final List<String> pathFromScript = pcfPluginState.findPathFromScript(script, "/");
    assertThat(pathFromScript.size()).isEqualTo(2);
    assertThat(pathFromScript).contains("/abc/test", "/xyz.json");
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_executeGitTask() {
    final DelegateTask delegateTask =
        DelegateTask.builder()
            .data(TaskData.builder().parameters(new Object[] {GitFetchFilesTaskParams.builder().build()}).build())
            .description("desc")
            .build();
    doReturn(delegateTask)
        .when(pcfStateHelper)
        .createGitFetchFileAsyncTask(any(ExecutionContext.class), anyMap(), anyString(), eq(true));
    when(applicationManifestUtils.createGitFetchFilesTaskParams(any(), any(), any()))
        .thenReturn(GitFetchFilesTaskParams.builder().build());
    final ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(Remote)
            .gitFileConfig(GitFileConfig.builder().filePath("app/sample_application").build())
            .build();
    when(applicationManifestUtils.getApplicationManifestForService(context)).thenReturn(applicationManifest);
    on(context).set("serviceTemplateService", serviceTemplateService);

    pcfPluginState.setScriptString(
        "echo '${service.manifest.repoRoot}/abc/test any ${service.manifest}/manifest.yml text sfkjsdfk \n /wrong/path \\${service.manifest}/xyz.json some text' ");
    final ExecutionResponse executionResponse = pcfPluginState.execute(context);
    assertThat(executionResponse.isAsync()).isTrue();
    final PcfPluginStateExecutionData stateExecutionData =
        (PcfPluginStateExecutionData) (executionResponse.getStateExecutionData());

    assertThat(stateExecutionData.getRepoRoot()).isEqualTo("/app/sample_application");
    assertThat(stateExecutionData.getFilePathsInScript()).contains("/app/sample_application/manifest.yml");
    assertThat(stateExecutionData.getFilePathsInScript()).isNotEmpty();
    assertThat(stateExecutionData.getRenderedScriptString()).isNotEmpty();
    verify(delegateService, times(1)).queueTaskV2(delegateTask);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_executeGitTaskForLinkedCommand() {
    final DelegateTask delegateTask =
        DelegateTask.builder()
            .data(TaskData.builder().parameters(new Object[] {GitFetchFilesTaskParams.builder().build()}).build())
            .description("desc")
            .build();
    doReturn(delegateTask)
        .when(pcfStateHelper)
        .createGitFetchFileAsyncTask(any(ExecutionContext.class), anyMap(), anyString(), eq(true));
    when(applicationManifestUtils.createGitFetchFilesTaskParams(any(), any(), any()))
        .thenReturn(GitFetchFilesTaskParams.builder().build());
    final ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(Remote)
            .gitFileConfig(GitFileConfig.builder().filePath("app/sample_application").build())
            .build();
    when(applicationManifestUtils.getApplicationManifestForService(context)).thenReturn(applicationManifest);
    on(context).set("serviceTemplateService", serviceTemplateService);

    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("manifest", "manifest.yml");
    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> ((String) (i.getArguments()[0])).replace("${manifest}", "manifest.yml"));

    pcfPluginState.setScriptString(
        "echo '${service.manifest.repoRoot}/abc/test any ${service.manifest}/${manifest} text sfkjsdfk \n /wrong/path \\${service.manifest}/xyz.json some text' ");
    final ExecutionResponse executionResponse = pcfPluginState.execute(context);
    assertThat(executionResponse.isAsync()).isTrue();
    final PcfPluginStateExecutionData stateExecutionData =
        (PcfPluginStateExecutionData) (executionResponse.getStateExecutionData());

    assertThat(stateExecutionData.getRepoRoot()).isEqualTo("/app/sample_application");
    // now we render all the variables on the delegate side
    assertThat(stateExecutionData.getFilePathsInScript()).contains("/app/sample_application/${manifest}");
    assertThat(stateExecutionData.getFilePathsInScript()).isNotEmpty();
    assertThat(stateExecutionData.getRenderedScriptString()).isNotEmpty();
    verify(delegateService, times(1)).queueTaskV2(delegateTask);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_executePcfPluginTask() {
    on(context).set("serviceTemplateService", serviceTemplateService);

    final ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).gitFileConfig(GitFileConfig.builder().build()).build();
    when(applicationManifestUtils.getApplicationManifestForService(context)).thenReturn(applicationManifest);
    on(context).set("serviceTemplateService", serviceTemplateService);
    when(applicationManifestUtils.createGitFetchFilesTaskParams(any(), any(), any()))
        .thenReturn(GitFetchFilesTaskParams.builder().build());
    pcfPluginState.setScriptString(
        "echo '${service.manifest}/abc/test any text sfkjsdfk \n /wrong/path \\${service.manifest}/xyz.json some text' ");
    final ExecutionResponse executionResponse = pcfPluginState.execute(context);
    assertThat(executionResponse.isAsync()).isTrue();
    final PcfPluginStateExecutionData stateExecutionData =
        (PcfPluginStateExecutionData) (executionResponse.getStateExecutionData());
    verify(delegateService, times(1)).queueTaskV2(any(DelegateTask.class));
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_executePcfPluginTaskForLinkedCommand() {
    on(context).set("serviceTemplateService", serviceTemplateService);

    final ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).gitFileConfig(GitFileConfig.builder().build()).build();
    when(applicationManifestUtils.getApplicationManifestForService(context)).thenReturn(applicationManifest);
    on(context).set("serviceTemplateService", serviceTemplateService);
    when(applicationManifestUtils.createGitFetchFilesTaskParams(any(), any(), any()))
        .thenReturn(GitFetchFilesTaskParams.builder().build());

    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("json", "xyz.json");
    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> ((String) (i.getArguments()[0])).replace("${json}", "xyz.json"));

    pcfPluginState.setScriptString(
        "echo '${service.manifest}/abc/test any text sfkjsdfk \n /wrong/path \\${service.manifest}/${json} some text' ");
    final ExecutionResponse executionResponse = pcfPluginState.execute(context);
    assertThat(executionResponse.isAsync()).isTrue();
    final PcfPluginStateExecutionData stateExecutionData =
        (PcfPluginStateExecutionData) (executionResponse.getStateExecutionData());
    verify(delegateService, times(1)).queueTaskV2(any(DelegateTask.class));
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_handleAsyncResponseForGitTask() {
    final GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder().build();
    GitCommandExecutionResponse gitCommandExecutionResponse = GitCommandExecutionResponse.builder()
                                                                  .gitCommandStatus(GitCommandStatus.SUCCESS)
                                                                  .gitCommandResult(gitFetchFilesFromMultipleRepoResult)
                                                                  .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("response", gitCommandExecutionResponse);
    final ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(Local).build();
    ((PcfPluginStateExecutionData) context.getStateExecutionData()).setTaskType(TaskType.GIT_FETCH_FILES_TASK);
    ((PcfPluginStateExecutionData) context.getStateExecutionData())
        .setAppManifestMap(Collections.singletonMap(K8sValuesLocation.Service, applicationManifest));
    ((PcfPluginStateExecutionData) context.getStateExecutionData()).setRepoRoot("/");

    pcfPluginState.handleAsyncInternal(context, response);

    verify(activityService, times(0)).updateStatus("activityId", APP_ID, FAILED);

    PcfPluginStateExecutionData pcfSetupStateExecutionData = context.getStateExecutionData();

    assertThat(pcfSetupStateExecutionData.getFetchFilesResult()).isEqualTo(gitFetchFilesFromMultipleRepoResult);

    verify(pcfPluginState, times(1)).executePcfPluginTask(any(), any(), any(), any(), any(), eq("/"));
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_handleAsyncResponseForPluginTask() {
    final CfCommandExecutionResponse commandExecutionResponse =
        CfCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("response", commandExecutionResponse);

    ((PcfPluginStateExecutionData) context.getStateExecutionData()).setTaskType(TaskType.PCF_COMMAND_TASK);
    ((PcfPluginStateExecutionData) context.getStateExecutionData()).setActivityId("activityId");
    pcfPluginState.handleAsyncInternal(context, response);

    verify(activityService, times(1)).updateStatus("activityId", APP_ID, SUCCESS);

    PcfPluginStateExecutionData pcfSetupStateExecutionData =
        (PcfPluginStateExecutionData) context.getStateExecutionData();
    assertThat(pcfSetupStateExecutionData.getStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);

    pcfPluginState.setTimeoutIntervalInMinutes(null);
    doReturn(PcfPluginStateExecutionData.builder().timeoutIntervalInMinutes(10).build())
        .when(mockContext)
        .getStateExecutionData();
    assertThat(pcfPluginState.getTimeoutMillis(mockContext)).isEqualTo(10 * 60 * 1000);

    pcfPluginState.setTimeoutIntervalInMinutes(1);
    assertThat(pcfPluginState.getTimeoutMillis(mockContext)).isEqualTo(60 * 1000);

    pcfPluginState.setTimeoutIntervalInMinutes(null);
    doReturn(PcfPluginStateExecutionData.builder().timeoutIntervalInMinutes(null).build())
        .when(mockContext)
        .getStateExecutionData();
    assertThat(pcfPluginState.getTimeoutMillis(mockContext)).isEqualTo(30 * 60 * 1000);
  }
}
