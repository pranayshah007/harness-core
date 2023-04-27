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
import static io.harness.beans.FeatureName.CF_ALLOW_SPECIAL_CHARACTERS;
import static io.harness.beans.FeatureName.CF_CUSTOM_EXTRACTION;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.pcf.CfCommandUnitConstants.CheckExistingApps;
import static io.harness.pcf.CfCommandUnitConstants.FetchCustomFiles;
import static io.harness.pcf.CfCommandUnitConstants.FetchGitFiles;
import static io.harness.pcf.CfCommandUnitConstants.PcfSetup;
import static io.harness.pcf.CfCommandUnitConstants.VerifyManifests;
import static io.harness.pcf.CfCommandUnitConstants.Wrapup;
import static io.harness.pcf.model.PcfConstants.INFRA_ROUTE;
import static io.harness.pcf.model.PcfConstants.PCF_INFRA_ROUTE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.CUSTOM_MANIFEST_FETCH_TASK;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.beans.TaskType.PCF_COMMAND_TASK;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.MASKED;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.states.pcf.PcfSetupState.PCF_SETUP_COMMAND;
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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
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
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfSetupCommandResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.pcf.InfoVariables;
import software.wings.api.pcf.PcfSetupStateExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitFetchFilesTaskParams;
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
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.common.InfrastructureConstants;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.ServiceHelper;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
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
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.ApplicationManifestUtils;

import dev.morphia.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PcfSetupStateTest extends WingsBaseTest {
  private static final String BASE_URL = "https://env.harness.io/";
  public static final String MANIFEST_YAML_CONTENT = "  applications:\n"
      + "  - name : appName\n"
      + "    memory: 850M\n"
      + "    instances : 3\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    routes:\n"
      + "      - route: ${ROUTE_MAP}\n";

  public static final String MANIFEST_YAML_LEGACY = "  applications:\n"
      + "  - name: ${APPLICATION_NAME}\n"
      + "    instances: ${INSTANCE_COUNT}\n";

  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
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
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks @Spy private PcfStateHelper pcfStateHelper;

  private PcfStateTestHelper pcfStateTestHelper = new PcfStateTestHelper();

  @Spy @InjectMocks private PcfSetupState pcfSetupState = new PcfSetupState("name");

  @Mock private MainConfiguration configuration;

  private ExecutionContext context;

  private WorkflowStandardParams workflowStandardParams = pcfStateTestHelper.getWorkflowStandardParams();

  private ServiceElement serviceElement = pcfStateTestHelper.getServiceElement();

  @InjectMocks private PhaseElement phaseElement = pcfStateTestHelper.getPhaseElement(serviceElement);

  private StateExecutionInstance stateExecutionInstance =
      pcfStateTestHelper.getStateExecutionInstanceForSetupState(workflowStandardParams, phaseElement, serviceElement);

  private Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();
  private Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
  private Service service = Service.builder()
                                .appId(APP_ID)
                                .uuid(SERVICE_ID)
                                .name(SERVICE_NAME)
                                .artifactStreamIds(singletonList(ARTIFACT_STREAM_ID))
                                .build();
  private Artifact artifact = anArtifact()
                                  .withArtifactSourceName("source")
                                  .withMetadata(new ArtifactMetadata(new HashMap<String, String>() {
                                    { put(ArtifactMetadataKeys.buildNo, "bn"); }
                                  }))
                                  .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                  .build();
  private ArtifactStream artifactStream =
      JenkinsArtifactStream.builder().appId(APP_ID).sourceName("").jobname("").artifactPaths(null).build();

  private SettingAttribute pcfConfig = aSettingAttribute()
                                           .withValue(PcfConfig.builder()
                                                          .endpointUrl(URL)
                                                          .password(PASSWORD)
                                                          .username(USER_NAME_DECRYPTED)
                                                          .accountId(ACCOUNT_ID)
                                                          .build())
                                           .build();

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
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.SETUP).withName("Setup Service Cluster").build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "Setup Service Cluster"))
        .thenReturn(serviceCommand);

    EmbeddedUser currentUser = EmbeddedUser.builder().name("test").email("test@harness.io").build();
    workflowStandardParams.setCurrentUser(currentUser);

    when(artifactService.get(any())).thenReturn(artifact);
    when(artifactStreamService.get(any())).thenReturn(artifactStream);

    InfrastructureMapping infrastructureMapping = PcfInfrastructureMapping.builder()
                                                      .organization(ORG)
                                                      .space(SPACE)
                                                      .routeMaps(Arrays.asList("R1"))
                                                      .tempRouteMap(Arrays.asList("R2"))
                                                      .computeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                      .build();
    infrastructureMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);
    InfrastructureDefinition pcfInfraDef = InfrastructureDefinition.builder()
                                               .infrastructure(PcfInfraStructure.builder()
                                                                   .organization(ORG)
                                                                   .space(SPACE)
                                                                   .routeMaps(Arrays.asList("R1"))
                                                                   .tempRouteMap(Arrays.asList("R2"))
                                                                   .cloudProviderId(COMPUTE_PROVIDER_ID)
                                                                   .build())
                                               .build();
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(pcfInfraDef);

    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);
    when(activityService.get(any(), any())).thenReturn(activity);

    when(settingsService.get(any())).thenReturn(pcfConfig);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(serviceVariableList);
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, MASKED))
        .thenReturn(safeDisplayServiceVariableList);
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    FieldUtils.writeField(pcfSetupState, "secretManager", secretManager, true);
    FieldUtils.writeField(pcfSetupState, "olderActiveVersionCountToKeep", 3, true);
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("settingsService", settingsService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);

    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(serviceResourceService.getPcfServiceSpecification(anyString(), anyString()))
        .thenReturn(
            PcfServiceSpecification.builder().manifestYaml(MANIFEST_YAML_CONTENT).serviceId(service.getUuid()).build());
    doNothing().when(serviceHelper).addPlaceholderTexts(any());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");

    doReturn("artifact-name").when(pcfSetupState).artifactFileNameForSource(any(), any());
    doReturn("artifact-path").when(pcfSetupState).artifactPathForSource(any(), any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        spy(new WorkflowStandardParamsExtensionService(appService, null, artifactService, environmentService,
            artifactStreamServiceBindingService, null, featureFlagService));

    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(pcfSetupState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory =
        new ContextElementParamMapperFactory(subdomainUrlHelper, workflowExecutionService, artifactService,
            artifactStreamService, null, featureFlagService, null, workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testArtifactPathForSource() {
    when(pcfSetupState.artifactPathForSource(any(), any())).thenCallRealMethod();

    // Case1: when jobName occurs as a substring inside artifactPath
    String path = "harness-internal/harness-internal/20211208.2.zip";
    String jobName1 = "harness-internal";
    String url1 = "https://harness.jfrog.io/artifactory/harness-internal/harness-internal/20211208.2.zip";
    Map<String, String> metadata = new HashMap<String, String>() {
      {
        put("artifactPath", path);
        put("url", url1);
      }
    };

    Artifact artifact = new Artifact();
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(SettingVariableTypes.ARTIFACTORY.name())
                                                            .jobName(jobName1)
                                                            .metadata(metadata)
                                                            .build();
    String result = pcfSetupState.artifactPathForSource(artifact, artifactStreamAttributes);
    assertThat(result).isEqualTo(path);

    // Case2: when there is no overlap between jobName and artifactPath
    String jobName2 = "test";
    String url2 = "https://harness.jfrog.io/artifactory/test/harness-internal/20211208.2.zip";
    artifactStreamAttributes.setJobName(jobName2);
    metadata.put("url", url2);
    result = pcfSetupState.artifactPathForSource(artifact, artifactStreamAttributes);
    assertThat(result).isEqualTo(path);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testArtifactPathForSourceNoURL() {
    when(pcfSetupState.artifactPathForSource(any(), any())).thenCallRealMethod();

    String path = "harness-internal/harness-internal/20211208.2.zip";
    String jobName = "harness-internal";
    Map<String, String> metadata = new HashMap<String, String>() {
      { put("artifactPath", path); }
    };

    Artifact artifact = new Artifact();
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(SettingVariableTypes.NEXUS.name())
                                                            .jobName(jobName)
                                                            .metadata(metadata)
                                                            .build();
    String result = pcfSetupState.artifactPathForSource(artifact, artifactStreamAttributes);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testArtifactPathForSourceNoMetadata() {
    when(pcfSetupState.artifactPathForSource(any(), any())).thenCallRealMethod();

    String jobName = "harness-internal";

    Artifact artifact = new Artifact();
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .artifactStreamType(SettingVariableTypes.NEXUS.name())
                                                            .jobName(jobName)
                                                            .build();
    String result = pcfSetupState.artifactPathForSource(artifact, artifactStreamAttributes);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testExecute() {
    on(context).set("serviceTemplateService", serviceTemplateService);
    pcfSetupState.setUseCurrentRunningCount(false);
    pcfSetupState.setMaxInstances(2);
    pcfSetupState.setTags(singletonList("tag1"));

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    // With workflowV2 flag = true
    doReturn(PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_CONTENT).build())
        .when(pcfStateHelper)
        .generateManifestMap(any(), anyMap(), any(), anyString(), anyString());

    ExecutionResponse executionResponse = pcfSetupState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    PcfSetupStateExecutionData stateExecutionData =
        (PcfSetupStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.getCommandName()).isEqualTo("PCF Setup");
    CfCommandSetupRequest cfCommandSetupRequest = (CfCommandSetupRequest) stateExecutionData.getPcfCommandRequest();
    assertThat(cfCommandSetupRequest.getReleaseNamePrefix()).isEqualTo("appName");
    assertThat(cfCommandSetupRequest.getPcfCommandType()).isEqualTo(PcfCommandType.SETUP);
    assertThat(cfCommandSetupRequest.getPcfConfig().getEndpointUrl()).isEqualTo(URL);
    assertThat(cfCommandSetupRequest.getPcfConfig().getUsername()).isEqualTo(USER_NAME_DECRYPTED);
    assertThat(cfCommandSetupRequest.getOrganization()).isEqualTo(ORG);
    assertThat(cfCommandSetupRequest.getSpace()).isEqualTo(SPACE);
    assertThat(cfCommandSetupRequest.getMaxCount()).isEqualTo(3);
    assertThat(stateExecutionData.getTags()).isEqualTo(singletonList("tag1"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExceptionThrownWhenArtifactProcessingScriptIsSetForDockerDeployment() {
    on(context).set("serviceTemplateService", serviceTemplateService);
    stateExecutionInstance.setStateExecutionMap(new HashMap<>());
    pcfSetupState.setUseCurrentRunningCount(false);
    pcfSetupState.setMaxInstances(2);
    pcfSetupState.setUseArtifactProcessingScript(true);
    pcfSetupState.setArtifactProcessingScript("test");
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_CUSTOM_EXTRACTION), any());

    doReturn(PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_CONTENT).build())
        .when(pcfStateHelper)
        .generateManifestMap(any(), anyMap(), any(), anyString(), anyString());

    doReturn(DockerArtifactStream.builder().build()).when(artifactStreamService).get(any());

    assertThatThrownBy(() -> pcfSetupState.execute(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Docker based deployment shouldn't contain an artifact processing script");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWithCustomExtractionEnabled() {
    on(context).set("serviceTemplateService", serviceTemplateService);
    stateExecutionInstance.setStateExecutionMap(new HashMap<>());
    pcfSetupState.setUseCurrentRunningCount(false);
    pcfSetupState.setMaxInstances(2);
    pcfSetupState.setUseArtifactProcessingScript(true);
    pcfSetupState.setArtifactProcessingScript("script");
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_CUSTOM_EXTRACTION), any());

    doReturn(PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_CONTENT).build())
        .when(pcfStateHelper)
        .generateManifestMap(any(), anyMap(), any(), anyString(), anyString());

    ExecutionResponse executionResponse = pcfSetupState.execute(context);

    verify(activityService, times(1)).save(any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    PcfSetupStateExecutionData stateExecutionData =
        (PcfSetupStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.getCommandName()).isEqualTo("PCF Setup");
    CfCommandSetupRequest cfCommandSetupRequest = (CfCommandSetupRequest) stateExecutionData.getPcfCommandRequest();
    assertThat(cfCommandSetupRequest.getArtifactProcessingScript()).isEqualTo("script");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCFAppNamesWithSpecialCharacters() {
    String manifest = "applications:\n"
        + "- name: test-gsup-1.0.45\n"
        + "  memory: 500M\n"
        + "  instances : 2\n"
        + "  random-route: true\n"
        + "  docker:\n"
        + "   image: registry.hub.docker.com/harness/todolist_ga:latest\n";

    PcfManifestsPackage manifestsPackage = PcfManifestsPackage.builder().manifestYml(manifest).build();
    CfInternalConfig config = CfInternalConfig.builder().build();
    doReturn(false).when(featureFlagService).isEnabled(eq(CF_ALLOW_SPECIAL_CHARACTERS), any());
    String appNamePrefix =
        pcfSetupState.generateAppNamePrefix(context, app, serviceElement, env, manifestsPackage, config);
    assertThat(appNamePrefix).isNotNull();
    assertThat(appNamePrefix).isEqualTo("test__gsup__1__0__45");

    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ALLOW_SPECIAL_CHARACTERS), any());
    appNamePrefix = pcfSetupState.generateAppNamePrefix(context, app, serviceElement, env, manifestsPackage, config);
    assertThat(appNamePrefix).isNotNull();
    assertThat(appNamePrefix).isEqualTo("test-gsup-1.0.45");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteForFetchFiles() {
    on(context).set("serviceTemplateService", serviceTemplateService);

    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, ApplicationManifest.builder().storeType(Remote).build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.PCF_OVERRIDE))
        .thenReturn(applicationManifestMap);
    when(applicationManifestUtils.createGitFetchFilesTaskParams(any(), any(), any()))
        .thenReturn(GitFetchFilesTaskParams.builder().build());

    ExecutionResponse executionResponse = pcfSetupState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    PcfSetupStateExecutionData stateExecutionData =
        (PcfSetupStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.getCommandName()).isEqualTo("PCF Setup");
    assertThat(stateExecutionData.getTaskType()).isEqualTo(GIT_FETCH_FILES_TASK);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteCustomFetchValuesTask() {
    on(context).set("serviceTemplateService", serviceTemplateService);

    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, ApplicationManifest.builder().storeType(CUSTOM).build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.PCF_OVERRIDE))
        .thenReturn(applicationManifestMap);

    DelegateTask task = DelegateTask.builder()
                            .data(TaskData.builder()
                                      .async(true)
                                      .taskType(TaskType.CUSTOM_MANIFEST_FETCH_TASK.name())
                                      .parameters(new Object[] {CustomManifestValuesFetchParams.builder().build()})
                                      .build())
                            .build();
    doReturn(task)
        .when(pcfStateHelper)
        .createCustomFetchValuesTask(any(), eq(applicationManifestMap), anyString(), anyBoolean(), anyInt());

    ExecutionResponse executionResponse = pcfSetupState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    PcfSetupStateExecutionData stateExecutionData =
        (PcfSetupStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.getCommandName()).isEqualTo("PCF Setup");
    assertThat(stateExecutionData.getTaskType()).isEqualTo(CUSTOM_MANIFEST_FETCH_TASK);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitTask() {
    doReturn(InfrastructureDefinition.builder().name(INFRA_DEFINITION_ID).build())
        .when(infrastructureDefinitionService)
        .get(anyString(), anyString());

    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.SUCCESS).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", gitCommandExecutionResponse);

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service, ApplicationManifest.builder().storeType(Local).build());
    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(GIT_FETCH_FILES_TASK);
    pcfSetupStateExecutionData.setAppManifestMap(appManifestMap);
    pcfSetupStateExecutionData.setActivityId("activityId");

    doReturn(MANIFEST_YAML_CONTENT).when(pcfStateHelper).fetchManifestYmlString(any(), any());
    on(context).set("serviceTemplateService", serviceTemplateService);

    doReturn(PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_CONTENT).build())
        .when(pcfStateHelper)
        .generateManifestMap(any(), any(), any(), anyString(), anyString());

    pcfSetupState.handleAsyncInternal(context, response);
    verify(activityService, times(0)).updateStatus("activityId", APP_ID, FAILED);
    ArgumentCaptor<HashMap> appManifestMapCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(pcfSetupState, times(1)).executePcfTask(any(), any(), appManifestMapCaptor.capture());
    Map<K8sValuesLocation, ApplicationManifest> capturedValue =
        (Map<K8sValuesLocation, ApplicationManifest>) appManifestMapCaptor.getValue();
    assertThat(capturedValue).isNotEmpty();
    assertThat(capturedValue).containsKey(K8sValuesLocation.Service);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForCustomFetchValuesTaskWrapper() {
    doReturn(InfrastructureDefinition.builder().name(INFRA_DEFINITION_ID).build())
        .when(infrastructureDefinitionService)
        .get(anyString(), anyString());

    CustomManifestValuesFetchResponse fetchResponse =
        CustomManifestValuesFetchResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", fetchResponse);

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service, ApplicationManifest.builder().storeType(CUSTOM).build());
    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(CUSTOM_MANIFEST_FETCH_TASK);
    pcfSetupStateExecutionData.setAppManifestMap(appManifestMap);
    pcfSetupStateExecutionData.setActivityId("activityId");

    doReturn(MANIFEST_YAML_CONTENT).when(pcfStateHelper).fetchManifestYmlString(any(), any());
    on(context).set("serviceTemplateService", serviceTemplateService);

    doReturn(PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_CONTENT).build())
        .when(pcfStateHelper)
        .generateManifestMap(any(), any(), any(), anyString(), anyString());

    Map<K8sValuesLocation, Collection<String>> valuesFiles = new HashMap<>();
    valuesFiles.put(K8sValuesLocation.Service, Arrays.asList("Content"));
    doReturn(valuesFiles)
        .when(applicationManifestUtils)
        .getValuesFilesFromCustomFetchValuesResponse(any(), any(), any(), anyString());

    pcfSetupState.handleAsyncInternal(context, response);
    verify(activityService, times(0)).updateStatus("activityId", APP_ID, FAILED);
    ArgumentCaptor<HashMap> appManifestMapCaptor = ArgumentCaptor.forClass(HashMap.class);
    verify(pcfSetupState, times(1)).executePcfTask(any(), any(), appManifestMapCaptor.capture());
    Map<K8sValuesLocation, ApplicationManifest> capturedValue =
        (Map<K8sValuesLocation, ApplicationManifest>) appManifestMapCaptor.getValue();
    assertThat(capturedValue).isNotEmpty();
    assertThat(capturedValue).containsKey(K8sValuesLocation.Service);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForCustomFetchValuesTaskWrapperInErrorCase() {
    CustomManifestValuesFetchResponse fetchResponse =
        CustomManifestValuesFetchResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", fetchResponse);

    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(CUSTOM_MANIFEST_FETCH_TASK);
    pcfSetupStateExecutionData.setActivityId("activityId");

    pcfSetupState.handleAsyncInternal(context, response);
    verify(activityService, times(1)).updateStatus("activityId", APP_ID, FAILED);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitTaskInErrorCase() {
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.FAILURE).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", gitCommandExecutionResponse);

    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(GIT_FETCH_FILES_TASK);
    pcfSetupStateExecutionData.setActivityId("activityId");

    pcfSetupState.handleAsyncInternal(context, response);
    verify(activityService, times(1)).updateStatus("activityId", APP_ID, FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesAfterAppSetup() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__INACTIVE";

    CfAppSetupTimeDetails mostRecentInactiveAppVersion =
        CfAppSetupTimeDetails.builder().oldName(prevInActiveAppOldName).applicationName(appPrefix + "__0").build();

    CfAppSetupTimeDetails activeApp =
        CfAppSetupTimeDetails.builder().applicationName(oldAppName).applicationGuid(oldAppId).build();

    CfAppSetupTimeDetails newApp =
        CfAppSetupTimeDetails.builder().applicationName(newAppName).applicationGuid(newAppId).build();

    CfSetupCommandResponse pcfCommandResponse = CfSetupCommandResponse.builder()
                                                    .mostRecentInactiveAppVersion(mostRecentInactiveAppVersion)
                                                    .downsizeDetails(Collections.singletonList(activeApp))
                                                    .newApplicationDetails(newApp)
                                                    .build();

    CfCommandExecutionResponse cfCommandExecutionResponse = CfCommandExecutionResponse.builder()
                                                                .pcfCommandResponse(pcfCommandResponse)
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", cfCommandExecutionResponse);

    PcfSetupStateExecutionData pcfSetupStateExecutionData = context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(PCF_COMMAND_TASK);
    pcfSetupStateExecutionData.setActivityId("activityId");
    pcfSetupStateExecutionData.setStandardBlueGreen(true);
    pcfSetupStateExecutionData.setAppId(APP_ID);
    pcfSetupStateExecutionData.setInfraMappingId(INFRA_MAPPING_ID);

    ExecutionResponse executionResponse = pcfSetupState.handleAsyncInternal(context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    ArgumentCaptor<SweepingOutputInstance> outputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(2)).save(outputInstanceArgumentCaptor.capture());
    List<SweepingOutputInstance> allValues = outputInstanceArgumentCaptor.getAllValues();
    assertThat(allValues.size()).isEqualTo(2);
    InfoVariables infoVariables = (InfoVariables) allValues.get(1).getValue();
    assertThat(infoVariables.getActiveAppName()).isEqualTo(oldAppName);
    assertThat(infoVariables.getInActiveAppName()).isEqualTo(newAppName);
    assertThat(infoVariables.getMostRecentInactiveAppVersionOldName()).isEqualTo(prevInActiveAppOldName);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForPcfTaskInErrorCase() {
    CfCommandExecutionResponse cfCommandExecutionResponse =
        CfCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", cfCommandExecutionResponse);

    PcfSetupStateExecutionData pcfSetupStateExecutionData =
        (PcfSetupStateExecutionData) context.getStateExecutionData();
    pcfSetupStateExecutionData.setTaskType(PCF_COMMAND_TASK);
    pcfSetupStateExecutionData.setActivityId("activityId");

    ExecutionResponse executionResponse = pcfSetupState.handleAsyncInternal(context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCommandUnitList() {
    List<CommandUnit> commandUnits = pcfSetupState.getCommandUnitList(true, false);
    List<String> commandUnitNames = commandUnits.stream().map(CommandUnit::getName).collect(toList());
    assertThat(commandUnits).isNotNull();
    assertThat(commandUnits.size()).isEqualTo(5);
    assertThat(commandUnitNames).containsExactly(FetchGitFiles, VerifyManifests, CheckExistingApps, PcfSetup, Wrapup);

    commandUnits = pcfSetupState.getCommandUnitList(false, false);
    commandUnitNames = commandUnits.stream().map(CommandUnit::getName).collect(toList());
    assertThat(commandUnits).isNotNull();
    assertThat(commandUnits.size()).isEqualTo(4);
    assertThat(commandUnitNames).containsExactly(VerifyManifests, CheckExistingApps, PcfSetup, Wrapup);

    commandUnits = pcfSetupState.getCommandUnitList(false, true);
    commandUnitNames = commandUnits.stream().map(CommandUnit::getName).collect(toList());
    assertThat(commandUnitNames)
        .containsExactly(FetchCustomFiles, VerifyManifests, CheckExistingApps, PcfSetup, Wrapup);

    commandUnits = pcfSetupState.getCommandUnitList(true, true);
    commandUnitNames = commandUnits.stream().map(CommandUnit::getName).collect(toList());
    assertThat(commandUnitNames)
        .containsExactly(FetchGitFiles, FetchCustomFiles, VerifyManifests, CheckExistingApps, PcfSetup, Wrapup);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateCurrentRunningCount() {
    pcfSetupState.setMaxInstances(2);
    assertThat(pcfSetupState.generateCurrentRunningCount(
                   CfSetupCommandResponse.builder().instanceCountForMostRecentVersion(3).build()))
        .isEqualTo(3);

    assertThat(pcfSetupState.generateCurrentRunningCount(
                   CfSetupCommandResponse.builder().instanceCountForMostRecentVersion(0).build()))
        .isEqualTo(0);

    assertThat(pcfSetupState.generateCurrentRunningCount(
                   CfSetupCommandResponse.builder().instanceCountForMostRecentVersion(null).build()))
        .isEqualTo(0);

    assertThat(pcfSetupState.generateCurrentRunningCount(null)).isEqualTo(0);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetActualDesiredCount() {
    CfSetupCommandResponse cfSetupCommandResponse =
        CfSetupCommandResponse.builder().instanceCountForMostRecentVersion(null).build();

    PcfSetupStateExecutionData stateExecutionData =
        PcfSetupStateExecutionData.builder().useCurrentRunningInstanceCount(true).maxInstanceCount(2).build();
    assertThat(pcfSetupState.getActualDesiredCount(stateExecutionData, cfSetupCommandResponse)).isEqualTo(2);

    cfSetupCommandResponse.setInstanceCountForMostRecentVersion(0);
    assertThat(pcfSetupState.getActualDesiredCount(stateExecutionData, cfSetupCommandResponse)).isEqualTo(2);

    cfSetupCommandResponse.setInstanceCountForMostRecentVersion(3);
    assertThat(pcfSetupState.getActualDesiredCount(stateExecutionData, cfSetupCommandResponse)).isEqualTo(3);

    cfSetupCommandResponse.setInstanceCountForMostRecentVersion(3);
    stateExecutionData.setUseCurrentRunningInstanceCount(false);
    assertThat(pcfSetupState.getActualDesiredCount(stateExecutionData, cfSetupCommandResponse)).isEqualTo(2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCurrentRunningCountForSetupRequest() {
    PcfSetupState setupState = new PcfSetupState("PCF", PCF_SETUP_COMMAND);
    assertThat(setupState.getCurrentRunningCountForSetupRequest()).isNull();

    setupState.setUseCurrentRunningCount(true);
    setupState.setCurrentRunningCount(null);
    assertThat(setupState.getCurrentRunningCountForSetupRequest()).isNotNull();
    assertThat(setupState.getCurrentRunningCountForSetupRequest().intValue()).isEqualTo(2);

    setupState.setCurrentRunningCount(4);
    assertThat(setupState.getCurrentRunningCountForSetupRequest()).isNotNull();
    assertThat(setupState.getCurrentRunningCountForSetupRequest().intValue()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateAppNamePrefix() {
    PcfManifestsPackage pcfManifestsPackage = PcfManifestsPackage.builder().manifestYml(MANIFEST_YAML_LEGACY).build();
    CfInternalConfig config = CfInternalConfig.builder().build();
    String appName = "applicationName";
    pcfSetupState.setPcfAppName(appName);
    String appPrefix =
        pcfSetupState.generateAppNamePrefix(context, app, serviceElement, env, pcfManifestsPackage, config);
    assertThat(appPrefix).isNotNull();
    assertThat(appPrefix).isEqualTo(appName);

    pcfSetupState.setPcfAppName(null);
    appPrefix = pcfSetupState.generateAppNamePrefix(context, app, serviceElement, env, pcfManifestsPackage, config);
    assertThat(appPrefix).isNotNull();
    assertThat(appPrefix).isEqualTo(APP_NAME + "__" + SERVICE_NAME + "__" + ENV_NAME);

    pcfSetupState.setPcfAppName(appName);
    appName = "appName";
    doReturn(appName).when(pcfStateHelper).fetchPcfApplicationName(any(), anyString());
    pcfManifestsPackage.setManifestYml(MANIFEST_YAML_CONTENT);
    appPrefix = pcfSetupState.generateAppNamePrefix(context, app, serviceElement, env, pcfManifestsPackage, config);
    assertThat(appPrefix).isNotNull();
    assertThat(appPrefix).isEqualTo("appName");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testShouldUseOriginalRoute() {
    PcfSetupState state = new PcfSetupState("");
    assertThat(state.shouldUseOriginalRoute()).isTrue();

    state.setRoute(INFRA_ROUTE);
    assertThat(state.shouldUseOriginalRoute()).isTrue();

    state.setRoute(PCF_INFRA_ROUTE);
    assertThat(state.shouldUseOriginalRoute()).isTrue();

    state.setRoute(INFRA_ROUTE);
    state.setBlueGreen(true);
    assertThat(state.shouldUseOriginalRoute()).isFalse();

    state.setRoute(PCF_INFRA_ROUTE);
    state.setBlueGreen(true);
    assertThat(state.shouldUseOriginalRoute()).isFalse();

    state.setRoute(WorkflowServiceHelper.INFRA_TEMP_ROUTE_PCF);
    state.setBlueGreen(false);
    assertThat(state.shouldUseOriginalRoute()).isFalse();

    state.setRoute(WorkflowServiceHelper.INFRA_TEMP_ROUTE_PCF);
    state.setBlueGreen(true);
    assertThat(state.shouldUseOriginalRoute()).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchMaxCount() {
    String manifestYamlContent;
    manifestYamlContent = "applications:\n"
        + "- name: \"AppName\"\n"
        + "  memory: 1\n"
        + "  INSTANCES : 0\n"
        + "  path: /vars/foo\n"
        + "  ROUTES:\n"
        + "  - route: /hello/my-route";
    assertThat(pcfSetupState.fetchMaxCount(PcfManifestsPackage.builder().manifestYml(manifestYamlContent).build()))
        .isEqualTo(0);

    manifestYamlContent = "applications:\n"
        + "- name: \"AppName\"\n"
        + "  memory: 1\n"
        + "  INSTANCES : 5\n"
        + "  path: /vars/foo\n"
        + "  ROUTES:\n"
        + "  - route: /hello/my-route";
    assertThat(pcfSetupState.fetchMaxCount(PcfManifestsPackage.builder().manifestYml(manifestYamlContent).build()))
        .isEqualTo(5);

    manifestYamlContent = "applications:\n"
        + "- name: \"AppName\"\n"
        + "  memory: 1\n"
        + "  INSTANCES : 2\n"
        + "  path: /vars/foo\n"
        + "  ROUTES:\n"
        + "  - route: /hello/my-route";
    doReturn(2).when(pcfStateHelper).fetchMaxCountFromManifest(any(), any());
    assertThat(pcfSetupState.fetchMaxCount(PcfManifestsPackage.builder().manifestYml(manifestYamlContent).build()))
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchTempRoutes() {
    PcfSetupState state = new PcfSetupState("");
    assertThat(state.fetchTempRoutes(context, PcfInfrastructureMapping.builder().tempRouteMap(null).build())).isEmpty();

    String r1 = "myRoute1";
    String r2 = "myRoute2";
    String r3 = "myRoute3";
    state.setTempRouteMap(new String[] {r1, r2});
    assertThat(state.fetchTempRoutes(context, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .contains(r1, r2);

    state.setTempRouteMap(null);
    assertThat(
        state.fetchTempRoutes(context, PcfInfrastructureMapping.builder().tempRouteMap(Arrays.asList(r1, r3)).build()))
        .contains(r1, r3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRestoreStateDataAfterGitFetchIfNeeded() {
    PcfSetupState state = new PcfSetupState("");
    String[] tempRoutes = {"r1"};
    String[] finalRoutes = {"r2"};
    PcfSetupStateExecutionData pcfSetupStateExecutionData = PcfSetupStateExecutionData.builder()
                                                                .useAppAutoscalar(true)
                                                                .useCurrentRunningInstanceCount(true)
                                                                .maxInstanceCount(2)
                                                                .enforceSslValidation(true)
                                                                .activeVersionsToKeep(4)
                                                                .pcfAppNameFromLegacyWorkflow(APP_NAME)
                                                                .resizeStrategy(RESIZE_NEW_FIRST)
                                                                .tempRoutesOnSetupState(tempRoutes)
                                                                .finalRoutesOnSetupState(finalRoutes)
                                                                .useArtifactProcessingScript(true)
                                                                .artifactProcessingScript("test script")
                                                                .timeout(6)
                                                                .build();

    assertThat(state.getMaxInstances()).isNull();
    assertThat(state.isUseAppAutoscalar()).isFalse();
    assertThat(state.isEnforceSslValidation()).isFalse();
    assertThat(state.getOlderActiveVersionCountToKeep()).isNull();
    assertThat(state.isUseCurrentRunningCount()).isFalse();
    assertThat(state.getResizeStrategy()).isNull();
    assertThat(state.getTimeoutIntervalInMinutes()).isEqualTo(5);
    assertThat(state.getPcfAppName()).isNull();
    assertThat(state.getTempRouteMap()).isNull();
    assertThat(state.getFinalRouteMap()).isNull();
    assertThat(state.isUseArtifactProcessingScript()).isFalse();
    assertThat(state.getArtifactProcessingScript()).isNull();

    state.restoreStateDataAfterGitFetchIfNeeded(pcfSetupStateExecutionData);

    assertThat(state.getMaxInstances()).isEqualTo(2);
    assertThat(state.isUseAppAutoscalar()).isTrue();
    assertThat(state.isEnforceSslValidation()).isTrue();
    assertThat(state.getOlderActiveVersionCountToKeep()).isEqualTo(4);
    assertThat(state.isUseCurrentRunningCount()).isTrue();
    assertThat(state.getResizeStrategy()).isEqualTo(RESIZE_NEW_FIRST);
    assertThat(state.getTimeoutIntervalInMinutes()).isEqualTo(6);
    assertThat(state.getPcfAppName()).isEqualTo(APP_NAME);
    assertThat(state.getTempRouteMap()).isEqualTo(tempRoutes);
    assertThat(state.getFinalRouteMap()).isEqualTo(finalRoutes);
    assertThat(state.isUseArtifactProcessingScript()).isTrue();
    assertThat(state.getArtifactProcessingScript().equalsIgnoreCase("test script")).isTrue();

    // test for NPE
    state.restoreStateDataAfterGitFetchIfNeeded(null);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchRouteMas() {
    PcfManifestsPackage pcfManifestsPackage = PcfManifestsPackage.builder().build();
    String r1 = "myRoute4";
    String r2 = "myRoute01";
    doReturn(Arrays.asList(r1)).when(pcfStateHelper).getRouteMaps(any(), any());
    pcfSetupState.setBlueGreen(false);
    assertThat(pcfSetupState.fetchRouteMaps(
                   context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .containsExactly(r1);

    pcfSetupState.setBlueGreen(true);
    doReturn(Arrays.asList(r1)).when(pcfStateHelper).applyVarsYmlSubstitutionIfApplicable(anyList(), any());
    assertThat(pcfSetupState.fetchRouteMaps(
                   context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .containsExactly(r1);

    pcfSetupState.setBlueGreen(true);
    pcfSetupState.setFinalRouteMap(new String[] {r2});
    doReturn(Arrays.asList(r1)).when(pcfStateHelper).applyVarsYmlSubstitutionIfApplicable(anyList(), any());
    assertThat(pcfSetupState.fetchRouteMaps(
                   context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .containsExactly(r1, r2);

    pcfSetupState.setBlueGreen(true);
    doReturn(emptyList()).when(pcfStateHelper).getRouteMaps(anyString(), any());
    doReturn(emptyList()).when(pcfStateHelper).applyVarsYmlSubstitutionIfApplicable(anyList(), any());
    assertThat(pcfSetupState.fetchRouteMaps(
                   context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .containsExactly(r2);

    pcfSetupState.setBlueGreen(true);
    pcfSetupState.setFinalRouteMap(null);
    doReturn(emptyList()).when(pcfStateHelper).getRouteMaps(any(), any());
    doReturn(emptyList()).when(pcfStateHelper).applyVarsYmlSubstitutionIfApplicable(anyList(), any());
    try {
      pcfSetupState.fetchRouteMaps(
          context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build());
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }

    pcfSetupState.setBlueGreen(false);
    doReturn(emptyList()).when(pcfStateHelper).applyVarsYmlSubstitutionIfApplicable(anyList(), any());
    assertThat(pcfSetupState.fetchRouteMaps(
                   context, pcfManifestsPackage, PcfInfrastructureMapping.builder().tempRouteMap(null).build()))
        .isEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    PcfSetupState state = new PcfSetupState("name");
    state.setTimeoutIntervalInMinutes(null);
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);

    state.setTimeoutIntervalInMinutes(10);
    assertThat(state.getTimeoutMillis()).isEqualTo(600000);
  }
}
