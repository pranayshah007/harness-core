/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.pcf;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.FeatureName.SINGLE_MANIFEST_SUPPORT;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType.UPDATE_ROUTE;
import static io.harness.pcf.model.PcfConstants.INSTANCE_PLACEHOLDER_TOKEN_DEPRECATED;
import static io.harness.pcf.model.PcfConstants.INTERIM_APP_NAME_SUFFIX;
import static io.harness.pcf.model.PcfConstants.LEGACY_NAME_PCF_MANIFEST;
import static io.harness.pcf.model.PcfConstants.MANIFEST_YML;
import static io.harness.pcf.model.PcfConstants.MULTIPLE_APPLICATION_MANIFEST_MESSAGE;
import static io.harness.pcf.model.PcfConstants.VARS_YML;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.TaskType.COMMAND;
import static software.wings.beans.TaskType.CUSTOM_MANIFEST_FETCH_TASK;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.sm.states.pcf.PcfSwitchBlueGreenRoutes.PCF_BG_SWAP_ROUTE_COMMAND;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.PCF_SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME_DECRYPTED;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.request.CfCommandRouteUpdateRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfRouteUpdateCommandResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElement;
import software.wings.api.PcfInstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseExecutionData;
import software.wings.api.PhaseExecutionData.PhaseExecutionDataBuilder;
import software.wings.api.ServiceElement;
import software.wings.api.pcf.DeploySweepingOutputPcf;
import software.wings.api.pcf.InfoVariables;
import software.wings.api.pcf.PcfDeployStateExecutionData;
import software.wings.api.pcf.PcfRouteUpdateStateExecutionData;
import software.wings.api.pcf.PcfSetupStateExecutionData;
import software.wings.api.pcf.SetupSweepingOutputPcf;
import software.wings.api.pcf.SwapRouteRollbackSweepingOutputPcf;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.command.PcfDummyCommandUnit;
import software.wings.beans.container.PcfServiceSpecification;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.Builder;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.ApplicationManifestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
@TargetModule(_870_CG_ORCHESTRATION)
public class PcfStateHelperTest extends WingsBaseTest {
  private static String SECOND_INFRA_DEFINITION_ID = INFRA_DEFINITION_ID + "2";
  public static final String REPLACE_ME = "REPLACE_ME";
  public static final ServiceElement SERVICE_ELEMENT = ServiceElement.builder().uuid(SERVICE_ID).build();
  public static final String DUMMY_ACCOUNT_ID = "accountId";
  private LogCallback logCallback = null;
  private String SERVICE_MANIFEST_YML = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : ((INSTANCES))\n"
      + "  random-route: true\n"
      + "  level: Service";

  private String TEST_APP_MANIFEST = "applications:\n"
      + "- name: " + REPLACE_ME + "\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : ((INSTANCES))\n";

  private String TEST_VAR = "  MY: order\n"
      + "  PCF_APP_NAME : prod\n"
      + "  INSTANCES : 3";

  private String TEST_VAR_1 = "  MY: login\n"
      + "  DUMMY : dummy\n"
      + "  REPLACE_ROUTE_1 : qa.io\n"
      + "  REPLACE_ROUTE_2 : prod.io";

  private String ENV_MANIFEST_YML = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : ((INSTANCES))\n"
      + "  random-route: true\n"
      + "  level: Environment";

  private String ENV_SERVICE_MANIFEST_YML = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : ((INSTANCES))\n"
      + "  random-route: true\n"
      + "  level: EnvironmentService";

  private String envServiceId = "envServiceId";

  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private DelegateService delegateService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @InjectMocks @Inject private PcfStateHelper pcfStateHelper;
  @Mock private ExecutionContext context;
  @Mock private LogService logService;
  @Mock private SettingsService settingsService;

  public static final String MANIFEST_YAML_CONTENT_With_RouteMap = "  applications:\n"
      + "  - name : ${APPLICATION_NAME}\n"
      + "    memory: 850M\n"
      + "    instances : ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    routes:\n"
      + "      - route: app.harness.io\n"
      + "      - route: qa.harness.io\n";

  public static final String INVALID_ROUTES_MANIFEST_YAML_CONTENT = "  applications:\n"
      + "  - name : ${APPLICATION_NAME}\n"
      + "    memory: 850M\n"
      + "    instances : ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    routes:\n"
      + "      -route: app.harness.io\n";

  public static final String MANIFEST_YAML_CONTENT_With_NO_ROUTE = "  applications:\n"
      + "  - name : ${APPLICATION_NAME}\n"
      + "    memory: 850M\n"
      + "    instances : ${INSTANCE_COUNT}\n"
      + "    buildpack: https://github.com/cloudfoundry/java-buildpack.git\n"
      + "    path: ${FILE_LOCATION}\n"
      + "    no-route: true\n";
  public static final String SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL = "applications:\n"
      + "- name: sample-manifest-service-level\n"
      + "  memory: 700M\n"
      + "  instances : 1\n"
      + "  random-route: true";

  public static final String SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL = "applications:\n"
      + "- name: sample-manifest-environment-level\n"
      + "  memory: 700M\n"
      + "  instances : 1\n"
      + "  random-route: true";

  public static final String SAMPLE_APPLICATION_MANIFEST_INVALID = "applications:\n"
      + "- name: invalid-manifest\n"
      + "  memory;";

  @Before
  public void setup() throws IllegalAccessException {
    ApplicationManifest manifest = ApplicationManifest.builder()
                                       .serviceId(SERVICE_ID)
                                       .kind(AppManifestKind.K8S_MANIFEST)
                                       .storeType(StoreType.Local)
                                       .build();
    manifest.setUuid("1234");

    when(context.getAppId()).thenReturn(APP_ID);

    when(context.renderExpression(anyString())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });

    when(applicationManifestService.getByServiceId(anyString(), anyString(), any())).thenReturn(manifest);
    when(applicationManifestService.getManifestFileByFileName(anyString(), anyString()))
        .thenReturn(
            ManifestFile.builder().fileName(MANIFEST_YML).fileContent(PcfSetupStateTest.MANIFEST_YAML_CONTENT).build());

    doReturn("").when(delegateService).queueTaskV2(any());
    logCallback = Mockito.mock(LogCallback.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchManifestYmlString() throws Exception {
    doReturn(PcfServiceSpecification.builder()
                 .serviceId(SERVICE_ID)
                 .manifestYaml(PcfSetupStateTest.MANIFEST_YAML_CONTENT)
                 .build())
        .when(serviceResourceService)
        .getPcfServiceSpecification(anyString(), anyString());

    String yaml = pcfStateHelper.fetchManifestYmlString(context, SERVICE_ELEMENT);
    assertThat(yaml).isNotNull();
    assertThat(yaml).isEqualTo(PcfSetupStateTest.MANIFEST_YAML_CONTENT);

    yaml = pcfStateHelper.fetchManifestYmlString(context, SERVICE_ELEMENT);
    assertThat(yaml).isNotNull();
    assertThat(yaml).isEqualTo(PcfSetupStateTest.MANIFEST_YAML_CONTENT);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetFinalManifestFilesMap() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    GitFetchFilesFromMultipleRepoResult fetchFilesResult = GitFetchFilesFromMultipleRepoResult.builder().build();

    // Local Service manifest files
    ApplicationManifest serviceApplicationManifest = generateAppManifest(StoreType.Local, SERVICE_ID);
    appManifestMap.put(K8sValuesLocation.Service, serviceApplicationManifest);

    when(applicationManifestService.getManifestFilesByAppManifestId(APP_ID, SERVICE_ID))
        .thenReturn(Arrays.asList(ManifestFile.builder().fileContent(SERVICE_MANIFEST_YML).build()));
    PcfManifestsPackage pcfManifestsPackage = pcfStateHelper.getFinalManifestFilesMap(
        appManifestMap, fetchFilesResult, null, logCallback, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage).isNotNull();
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(SERVICE_MANIFEST_YML);

    // Remote overrides in environment
    ApplicationManifest envApplicationManifest = generateAppManifest(StoreType.Remote, ENV_ID);
    appManifestMap.put(K8sValuesLocation.EnvironmentGlobal, envApplicationManifest);
    GitFetchFilesResult filesResult = GitFetchFilesResult.builder()
                                          .files(Arrays.asList(GitFile.builder().fileContent(ENV_MANIFEST_YML).build()))
                                          .build();
    filesFromMultipleRepo.put(K8sValuesLocation.EnvironmentGlobal.name(), filesResult);
    fetchFilesResult.setFilesFromMultipleRepo(filesFromMultipleRepo);
    pcfManifestsPackage = pcfStateHelper.getFinalManifestFilesMap(
        appManifestMap, fetchFilesResult, null, logCallback, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage).isNotNull();
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(ENV_MANIFEST_YML);

    // Local Environment Service manifest files
    ApplicationManifest serviceEnvApplicationManifest = generateAppManifest(StoreType.Local, envServiceId);
    appManifestMap.put(K8sValuesLocation.Environment, serviceEnvApplicationManifest);

    when(applicationManifestService.getManifestFilesByAppManifestId(APP_ID, envServiceId))
        .thenReturn(Arrays.asList(ManifestFile.builder().fileContent(ENV_SERVICE_MANIFEST_YML).build()));
    pcfManifestsPackage = pcfStateHelper.getFinalManifestFilesMap(
        appManifestMap, fetchFilesResult, null, logCallback, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage).isNotNull();
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(ENV_SERVICE_MANIFEST_YML);

    // Custom manifests in service
    appManifestMap.clear();
    ApplicationManifest customApplicationManifest = generateAppManifest(StoreType.CUSTOM, ENV_ID);
    appManifestMap.put(K8sValuesLocation.Service, customApplicationManifest);
    Map<K8sValuesLocation, Collection<String>> manifests = new HashMap<>();
    manifests.put(K8sValuesLocation.Service, Arrays.asList(SERVICE_MANIFEST_YML, TEST_VAR));
    pcfManifestsPackage = pcfStateHelper.getFinalManifestFilesMap(
        appManifestMap, null, manifests, logCallback, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage).isNotNull();
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(SERVICE_MANIFEST_YML);
    assertThat(pcfManifestsPackage.getVariableYmls()).contains(TEST_VAR);

    // Custom manifests override in env
    appManifestMap.clear();
    appManifestMap.put(K8sValuesLocation.Service, customApplicationManifest);
    appManifestMap.put(K8sValuesLocation.Environment, customApplicationManifest);
    manifests.put(K8sValuesLocation.Environment, Arrays.asList(ENV_SERVICE_MANIFEST_YML, TEST_VAR));
    pcfManifestsPackage = pcfStateHelper.getFinalManifestFilesMap(
        appManifestMap, null, manifests, logCallback, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage).isNotNull();
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(ENV_SERVICE_MANIFEST_YML);
    assertThat(pcfManifestsPackage.getVariableYmls()).contains(TEST_VAR);
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFDisabledEnvironmentLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF disabled
    doReturn(false).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: EnvService - Inline - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envServiceInlineManifestMap = new HashMap<>();
    envServiceInlineManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envServiceInlineManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(Collections.singletonList(
            ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(
        pcfStateHelper
            .generateManifestMap(context, envServiceInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 2: EnvService - Inline - Invalid Manifest
    // Expected Behaviour: Throws error
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(
            Collections.singletonList(ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found at Environment level");

    // Test case 3: EnvService - Remote - Single Manifest - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envServiceRemoteManifestMap = new HashMap<>();
    envServiceRemoteManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envServiceRemoteManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(
        pcfStateHelper
            .generateManifestMap(context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 4: EnvService - Remote - Single Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()); }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");

    // Test case 5: EnvService - Remote - Multiple Manifest - Valid Manifest
    // Expected Behaviour: Any randomly chosen manifest at env level is set as final manifest
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(
        pcfStateHelper
            .generateManifestMap(context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 6: EnvService - Remote - Multiple Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFDisabledEnvironmentGlobalLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF disabled
    doReturn(false).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: All Service manifest - Inline - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envInlineManifestMap = new HashMap<>();
    envInlineManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envInlineManifestMap.put(K8sValuesLocation.EnvironmentGlobal,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(Collections.singletonList(
            ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(pcfStateHelper
                   .generateManifestMap(context, envInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
                   .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 2: All Service manifest - Inline - Invalid Manifest
    // Expected Behaviour: Throws error
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(
            Collections.singletonList(ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found at EnvironmentGlobal level");

    // Test case 3: All Service manifest - Remote - Single Manifest - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envRemoteManifestMap = new HashMap<>();
    envRemoteManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envRemoteManifestMap.put(K8sValuesLocation.EnvironmentGlobal,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(pcfStateHelper
                   .generateManifestMap(context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
                   .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 4: All Service manifest - Remote - Single Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()); }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");

    // Test case 5: All Service manifest - Remote - Multiple Manifest - Valid
    // Expected Behaviour: Any randomly chosen manifest at env level is set as final manifest
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(pcfStateHelper
                   .generateManifestMap(context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
                   .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 6: All Service manifest - Remote - Multiple Manifest - Invalid
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFDisabledServiceLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF disabled
    doReturn(false).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: Inline manifest present only at Service
    // Expected Behaviour: The inline manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> localManifestMap = Collections.singletonMap(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());

    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());

    assertThat(
        pcfStateHelper.generateManifestMap(context, localManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL);

    // Test case 2: Remote manifest present only at Service
    // Expected Behaviour: The remote manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> remoteManifestMap = Collections.singletonMap(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.Service.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Service.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(remoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();

    assertThat(
        pcfStateHelper.generateManifestMap(context, remoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL);

    // Test case 3: Remote folder present only at Service with multiple manifests
    // Expected Behaviour: Any randomly chosen manifest is set as final manifest
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Service.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Service.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(remoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();

    assertThat(
        pcfStateHelper.generateManifestMap(context, remoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL);
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFEnabledEnvironmentLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF enabled
    doReturn(true).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: EnvService - Inline - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envServiceInlineManifestMap = new HashMap<>();
    envServiceInlineManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envServiceInlineManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(Collections.singletonList(
            ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(
        pcfStateHelper
            .generateManifestMap(context, envServiceInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 2: EnvService - Inline - Invalid Manifest
    // Expected Behaviour: Throws error
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(
            Collections.singletonList(ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found at Environment level");

    // Test case 3: EnvService - Remote - Single Manifest - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envServiceRemoteManifestMap = new HashMap<>();
    envServiceRemoteManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envServiceRemoteManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(
        pcfStateHelper
            .generateManifestMap(context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 4: EnvService - Remote - Single Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()); }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");

    // Test case 5: EnvService - Remote - Multiple Manifest - Valid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(MULTIPLE_APPLICATION_MANIFEST_MESSAGE, K8sValuesLocation.Environment.name()));

    // Test case 6: EnvService - Remote - Multiple Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Environment.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Environment.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envServiceRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envServiceRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFEnabledEnvironmentGlobalLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF enabled
    doReturn(true).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: All Service manifest - Inline - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envInlineManifestMap = new HashMap<>();
    envInlineManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envInlineManifestMap.put(K8sValuesLocation.EnvironmentGlobal,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(Collections.singletonList(
            ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(pcfStateHelper
                   .generateManifestMap(context, envInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
                   .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 2: All Service manifest - Inline - Invalid Manifest
    // Expected Behaviour: Throws error
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .doReturn(
            Collections.singletonList(ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envInlineManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found at EnvironmentGlobal level");

    // Test case 3: All Service manifest - Remote - Single Manifest - Valid Manifest
    // Expected Behaviour: The environment level manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> envRemoteManifestMap = new HashMap<>();
    envRemoteManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    envRemoteManifestMap.put(K8sValuesLocation.EnvironmentGlobal,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThat(pcfStateHelper
                   .generateManifestMap(context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
                   .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL);

    // Test case 4: All Service manifest - Remote - Single Manifest - Invalid Manifest
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build()); }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");

    // Test case 5: All Service manifest - Remote - Multiple Manifest - Valid
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_ENVIRONMENT_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(MULTIPLE_APPLICATION_MANIFEST_MESSAGE, K8sValuesLocation.EnvironmentGlobal.name()));

    // Test case 6: All Service manifest - Remote - Multiple Manifest - Invalid
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_INVALID).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.EnvironmentGlobal.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(envRemoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();
    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());
    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, envRemoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found filePath");
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testSingleManifestSupportFFEnabledServiceLevel() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    // FF enabled
    doReturn(true).when(featureFlagService).isEnabled(SINGLE_MANIFEST_SUPPORT, DUMMY_ACCOUNT_ID);

    // Test case 1: Inline manifest present only at Service
    // Expected Behaviour: The inline manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> localManifestMap = Collections.singletonMap(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());

    doReturn(Collections.singletonList(
                 ManifestFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());

    assertThat(
        pcfStateHelper.generateManifestMap(context, localManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL);

    // Test case 2: Remote manifest present only at Service
    // Expected Behaviour: The remote manifest is set as final manifest
    Map<K8sValuesLocation, ApplicationManifest> remoteManifestMap = Collections.singletonMap(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());
    List<GitFile> files = new ArrayList<GitFile>() {
      { add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build()); }
    };
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo =
        Collections.singletonMap(K8sValuesLocation.Service.name(), gitFetchFilesResult);
    final GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().gitFileConfig(GitFileConfig.builder().filePath("filePath").build()).build();
    Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Service.name(), gitFetchFilesConfig); }
    };
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder()
            .filesFromMultipleRepo(filesFromMultipleRepo)
            .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
            .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(remoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();

    assertThat(
        pcfStateHelper.generateManifestMap(context, remoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID)
            .getManifestYml())
        .isEqualTo(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL);

    // Test case 3: Remote folder present only at Service with multiple manifests
    // Expected Behaviour: Throws error
    files = new ArrayList<GitFile>() {
      {
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build());
        add(GitFile.builder().fileContent(SAMPLE_APPLICATION_MANIFEST_VALID_SERVICE_LEVEL).build());
      }
    };
    gitFetchFilesResult = GitFetchFilesResult.builder().files(files).build();
    filesFromMultipleRepo = Collections.singletonMap(K8sValuesLocation.Service.name(), gitFetchFilesResult);
    gitFetchFilesConfigMap = new HashMap<String, GitFetchFilesConfig>() {
      { put(K8sValuesLocation.Service.name(), gitFetchFilesConfig); }
    };
    gitFetchFilesFromMultipleRepoResult = GitFetchFilesFromMultipleRepoResult.builder()
                                              .filesFromMultipleRepo(filesFromMultipleRepo)
                                              .gitFetchFilesConfigMap(gitFetchFilesConfigMap)
                                              .build();

    doReturn(PcfSetupStateExecutionData.builder()
                 .appManifestMap(remoteManifestMap)
                 .fetchFilesResult(gitFetchFilesFromMultipleRepoResult)
                 .build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();

    assertThatThrownBy(()
                           -> pcfStateHelper.generateManifestMap(
                               context, remoteManifestMap, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(MULTIPLE_APPLICATION_MANIFEST_MESSAGE, K8sValuesLocation.Service.name()));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetFinalManifestFilesMapWithNullGitFetchFileResponse() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    GitFetchFilesFromMultipleRepoResult fetchFilesResult = GitFetchFilesFromMultipleRepoResult.builder().build();

    ApplicationManifest serviceApplicationManifest = generateAppManifest(StoreType.Remote, SERVICE_ID);
    appManifestMap.put(K8sValuesLocation.Service, serviceApplicationManifest);

    PcfManifestsPackage pcfManifestsPackage =
        pcfStateHelper.getFinalManifestFilesMap(appManifestMap, fetchFilesResult, null, logCallback, null);
    assertThat(pcfManifestsPackage.getManifestYml()).isNull();
    assertThat(pcfManifestsPackage.getVariableYmls()).isNull();

    filesFromMultipleRepo.put(K8sValuesLocation.Service.name(), null);
    fetchFilesResult.setFilesFromMultipleRepo(filesFromMultipleRepo);
    pcfManifestsPackage =
        pcfStateHelper.getFinalManifestFilesMap(appManifestMap, fetchFilesResult, null, logCallback, null);
    assertThat(pcfManifestsPackage.getManifestYml()).isNull();
    assertThat(pcfManifestsPackage.getVariableYmls()).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetFinalManifestFilesMapWithInvalidContent() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    GitFetchFilesFromMultipleRepoResult fetchFilesResult = GitFetchFilesFromMultipleRepoResult.builder().build();

    // Local Service manifest files
    ApplicationManifest serviceApplicationManifest = generateAppManifest(StoreType.Local, SERVICE_ID);
    appManifestMap.put(K8sValuesLocation.Service, serviceApplicationManifest);

    when(applicationManifestService.getManifestFilesByAppManifestId(APP_ID, SERVICE_ID))
        .thenReturn(Arrays.asList(ManifestFile.builder().fileContent("abc").build()));
    assertThatThrownBy(
        () -> pcfStateHelper.getFinalManifestFilesMap(appManifestMap, fetchFilesResult, null, logCallback, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No valid manifest files found at Service level");
  }

  private ApplicationManifest generateAppManifest(StoreType storeType, String id) {
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(storeType).build();
    applicationManifest.setUuid(id);
    applicationManifest.setAppId(APP_ID);

    return applicationManifest;
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetDelegateTask() throws Exception {
    String waitId = generateUuid();
    PcfDelegateTaskCreationData pcfDelegateTaskCreationData = PcfDelegateTaskCreationData.builder()
                                                                  .appId(APP_ID)
                                                                  .accountId(ACCOUNT_ID)
                                                                  .envId(ENV_ID)
                                                                  .infrastructureMappingId(INFRA_MAPPING_ID)
                                                                  .environmentType(PROD)
                                                                  .serviceId(SERVICE_ID)
                                                                  .taskType(COMMAND)
                                                                  .timeout(5l)
                                                                  .waitId(waitId)
                                                                  .parameters(new Object[] {"1"})
                                                                  .build();

    DelegateTask delegateTask = pcfStateHelper.getDelegateTask(pcfDelegateTaskCreationData);

    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD)).isEqualTo(PROD.name());
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.SERVICE_ID_FIELD)).isEqualTo(SERVICE_ID);
    assertThat(delegateTask.getWaitId()).isEqualTo(waitId);
    assertThat(delegateTask.getData().isAsync()).isTrue();

    TaskData taskData = delegateTask.getData();
    assertThat(taskData).isNotNull();
    assertThat(taskData.getTaskType()).isEqualTo(COMMAND.name());
    assertThat(taskData.getTimeout()).isEqualTo(300000L);

    Object[] parameters = taskData.getParameters();
    assertThat(parameters).isNotNull();
    assertThat(parameters).isNotEmpty();
    assertThat(parameters.length).isEqualTo(1);
    assertThat(parameters[0]).isEqualTo("1");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetActivityBuilder() throws Exception {
    final String commandName = "C1";
    final String commandType = "CType";
    final String user = "test";
    final String email = "email";
    PcfDummyCommandUnit pcfDummyCommandUnit = new PcfDummyCommandUnit("dummy");
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setCurrentUser(EmbeddedUser.builder().name(user).email(email).build());

    ExecutionContext context = mock(ExecutionContext.class);
    doReturn(workflowStandardParams).when(context).getContextElement(ContextElementType.STANDARD);

    PcfActivityBuilderCreationData creationData =
        PcfActivityBuilderCreationData.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .commandName(commandName)
            .commandType(commandType)
            .commandUnits(Arrays.asList(pcfDummyCommandUnit))
            .commandUnitType(CommandUnitType.COMMAND)
            .environment(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(PROD).build())
            .executionContext(context)
            .type(Type.Command)
            .build();

    ActivityBuilder activityBuilder = pcfStateHelper.getActivityBuilder(creationData);
    assertThat(activityBuilder).isNotNull();
    Activity activity = activityBuilder.build();
    assertThat(activity.getAppId()).isEqualTo(APP_ID);
    assertThat(activity.getApplicationName()).isEqualTo(APP_NAME);
    assertThat(activity.getEnvironmentId()).isEqualTo(ENV_ID);
    assertThat(activity.getEnvironmentName()).isEqualTo(ENV_NAME);
    assertThat(activity.getEnvironmentType()).isEqualTo(PROD);
    assertThat(activity.getCommandName()).isEqualTo(commandName);
    assertThat(activity.getCommandType()).isEqualTo(commandType);
    assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(activity.getTriggeredBy()).isNotNull();
    assertThat(activity.getTriggeredBy().getName()).isEqualTo(user);
    assertThat(activity.getTriggeredBy().getEmail()).isEqualTo(email);
    assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(activity.getCommandUnitType()).isEqualTo(CommandUnitType.COMMAND);

    List<CommandUnit> unitList = activity.getCommandUnits();
    assertThat(unitList).isNotNull();
    assertThat(unitList).containsExactly(pcfDummyCommandUnit);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testQueueDelegateTaskForRouteUpdate() {
    List renderedTags = Arrays.asList("tag1", "tag2");
    PcfRouteUpdateQueueRequestData requestData =
        PcfRouteUpdateQueueRequestData.builder()
            .timeoutIntervalInMinutes(5)
            .app(anApplication().name(APP_NAME).appId(APP_ID).uuid(APP_ID).accountId(ACCOUNT_ID).build())
            .activityId(ACTIVITY_ID)
            .environmentType(PROD)
            .pcfInfrastructureMapping(PcfInfrastructureMapping.builder()
                                          .tempRouteMap(Arrays.asList("temproute.io"))
                                          .routeMaps(Arrays.asList("route.io"))
                                          .organization("org")
                                          .space("space")
                                          .build())
            .commandName(PCF_BG_SWAP_ROUTE_COMMAND)
            .pcfConfig(
                PcfConfig.builder().endpointUrl("pcfUrl").username(USER_NAME_DECRYPTED).password(PASSWORD).build())
            .requestConfigData(CfRouteUpdateRequestConfigData.builder()
                                   .newApplicationName("newApp")
                                   .downsizeOldApplication(false)
                                   .isRollback(false)
                                   .existingApplicationNames(Arrays.asList("oldApp"))

                                   .existingApplicationDetails(Arrays.asList(CfAppSetupTimeDetails.builder()
                                                                                 .applicationGuid("AppGuid1")
                                                                                 .applicationName("pcfApp")
                                                                                 .initialInstanceCount(1)
                                                                                 .urls(Arrays.asList("url1"))
                                                                                 .build()))
                                   .build())
            .build();

    ExecutionResponse response = pcfStateHelper.queueDelegateTaskForRouteUpdate(requestData,
        SetupSweepingOutputPcf.builder()
            .pcfCommandRequest(CfCommandSetupRequest.builder().organization("org").space("space").build())
            .build(),
        null, false, renderedTags);
    assertThat(response).isNotNull();
    assertThat(response.isAsync()).isTrue();
    assertThat(response.getCorrelationIds()).containsExactly(ACTIVITY_ID);
    assertThat(response.getStateExecutionData()).isNotNull();
    assertThat(response.getStateExecutionData() instanceof PcfRouteUpdateStateExecutionData).isTrue();

    PcfRouteUpdateStateExecutionData stateExecutionData =
        (PcfRouteUpdateStateExecutionData) response.getStateExecutionData();
    assertThat(stateExecutionData.getAppId()).isNotNull();
    assertThat(stateExecutionData.getAppId()).isEqualTo(APP_ID);
    assertThat(stateExecutionData.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(stateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat("PCF BG Swap Route").isEqualTo(stateExecutionData.getCommandName());

    assertThat(stateExecutionData.getPcfCommandRequest() instanceof CfCommandRouteUpdateRequest).isTrue();
    CfCommandRouteUpdateRequest request = (CfCommandRouteUpdateRequest) stateExecutionData.getPcfCommandRequest();
    assertThat(stateExecutionData.getAppId()).isEqualTo(APP_ID);
    assertThat("PCF BG Swap Route").isEqualTo(stateExecutionData.getCommandName());
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(request.getAppId()).isEqualTo(APP_ID);
    assertThat("PCF BG Swap Route").isEqualTo(request.getCommandName());
    assertThat(request.getPcfCommandType()).isEqualTo(UPDATE_ROUTE);
    assertThat(request.getOrganization()).isEqualTo("org");
    assertThat(request.getSpace()).isEqualTo("space");

    assertThat(request.getPcfConfig().getEndpointUrl()).isEqualTo("pcfUrl");
    assertThat(request.getPcfConfig().getUsername()).isEqualTo(USER_NAME_DECRYPTED);
    assertThat(request.getPcfConfig().getPassword()).isEqualTo(PASSWORD);

    CfRouteUpdateRequestConfigData cfRouteUpdateRequestConfigData =
        stateExecutionData.getPcfRouteUpdateRequestConfigData();
    assertThat(cfRouteUpdateRequestConfigData).isNotNull();
    assertThat(cfRouteUpdateRequestConfigData.getNewApplicationName()).isEqualTo("newApp");
    assertThat(cfRouteUpdateRequestConfigData.isDownsizeOldApplication()).isFalse();
    assertThat(cfRouteUpdateRequestConfigData.isRollback()).isFalse();
    assertThat(cfRouteUpdateRequestConfigData.getExistingApplicationDetails().size()).isEqualTo(1);
    assertThat(cfRouteUpdateRequestConfigData.getExistingApplicationNames().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRouteMaps() {
    PcfInfrastructureMapping infrastructureMapping = PcfInfrastructureMapping.builder().routeMaps(null).build();

    // Test 1: Read from manifest
    List<String> routes = pcfStateHelper.getRouteMaps(MANIFEST_YAML_CONTENT_With_RouteMap, infrastructureMapping);
    assertThat(routes).isNotNull();
    assertThat(routes.size()).isEqualTo(2);
    assertThat(routes).containsExactly("app.harness.io", "qa.harness.io");

    // Test 2: Read from manifest, inframapping also contains routes, ignore them
    infrastructureMapping.setRouteMaps(Arrays.asList("stage.harness.io"));
    routes = pcfStateHelper.getRouteMaps(MANIFEST_YAML_CONTENT_With_RouteMap, infrastructureMapping);
    assertThat(routes).isNotNull();
    assertThat(routes.size()).isEqualTo(2);
    assertThat(routes).containsExactly("app.harness.io", "qa.harness.io");

    // Test 3: Routes not metinoed in manifest, read infraMaaping 1
    infrastructureMapping.setRouteMaps(Arrays.asList("stage.harness.io"));
    routes = pcfStateHelper.getRouteMaps(PcfSetupStateTest.MANIFEST_YAML_CONTENT, infrastructureMapping);
    assertThat(routes).isNotNull();
    assertThat(routes.size()).isEqualTo(1);
    assertThat(routes).containsExactly("stage.harness.io");

    // Test 4: NPE check
    infrastructureMapping.setRouteMaps(null);
    routes = pcfStateHelper.getRouteMaps(PcfSetupStateTest.MANIFEST_YAML_CONTENT, infrastructureMapping);
    assertThat(routes).isNotNull();
    assertThat(routes.size()).isEqualTo(0);

    // Test 5: no-route in manifest, routes should be empty, ignore inframap routes
    infrastructureMapping.setRouteMaps(Arrays.asList("app.harness.io"));
    routes = pcfStateHelper.getRouteMaps(MANIFEST_YAML_CONTENT_With_NO_ROUTE, infrastructureMapping);
    assertThat(routes).isNotNull();
    assertThat(routes.size()).isEqualTo(0);

    // Test 5: no-route in manifest, routes should be empty, ignore inframap routes
    infrastructureMapping.setRouteMaps(null);
    try {
      pcfStateHelper.getRouteMaps(INVALID_ROUTES_MANIFEST_YAML_CONTENT, infrastructureMapping);
      fail("Exception expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testApplyVarsYmlSubstitutionIfApplicable() {
    PcfManifestsPackage pcfManifestsPackage =
        PcfManifestsPackage.builder()
            .manifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, "TEST_((MY))__((PCF_APP_NAME))"))
            .variableYmls(Arrays.asList(TEST_VAR_1))
            .build();

    List<String> routes = pcfStateHelper.applyVarsYmlSubstitutionIfApplicable(
        Arrays.asList("((REPLACE_ROUTE_1))", "((REPLACE_ROUTE_2))", "test.io"), pcfManifestsPackage);

    assertThat(routes).containsExactly("qa.io", "prod.io", "test.io");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchMaxCountFromManifest() {
    PcfManifestsPackage pcfManifestsPackage =
        PcfManifestsPackage.builder().manifestYml(TEST_APP_MANIFEST).variableYmls(Arrays.asList(TEST_VAR)).build();

    assertThat(pcfStateHelper.fetchMaxCountFromManifest(pcfManifestsPackage, 1)).isEqualTo(3);

    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace("((INSTANCES))", "2"));
    assertThat(pcfStateHelper.fetchMaxCountFromManifest(pcfManifestsPackage, 3)).isEqualTo(2);

    pcfManifestsPackage.setManifestYml(
        TEST_APP_MANIFEST.replace("((INSTANCES))", INSTANCE_PLACEHOLDER_TOKEN_DEPRECATED));
    assertThat(pcfStateHelper.fetchMaxCountFromManifest(pcfManifestsPackage, 4)).isEqualTo(4);

    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace("((INSTANCES))", INSTANCE_PLACEHOLDER_TOKEN_DEPRECATED)
                                           .replace("instances", "INSTANCES"));
    assertThat(pcfStateHelper.fetchMaxCountFromManifest(pcfManifestsPackage, 4)).isEqualTo(4);

    pcfManifestsPackage.setVariableYmls(emptyList());
    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST);
    try {
      assertThat(pcfStateHelper.fetchMaxCountFromManifest(pcfManifestsPackage, 1)).isEqualTo(3);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("No Valid Variable file Found, please verify var file is present and has valid structure");
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFetchPcfApplicationName() {
    PcfManifestsPackage pcfManifestsPackage =
        PcfManifestsPackage.builder()
            .manifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, "TEST_((MY))__((PCF_APP_NAME))"))
            .variableYmls(Arrays.asList(TEST_VAR))
            .build();

    String appName = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, "app");
    assertThat(appName).isEqualTo("TEST_order__prod");

    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, "TEST_((PCF_APP_NAME))"));
    appName = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, "app");
    assertThat(appName).isEqualTo("TEST_prod");

    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, "TEST"));
    appName = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, "app");
    assertThat(appName).isEqualTo("TEST");

    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, "TEST_((PCF_APP_NAME))__((DUMMY))"));
    pcfManifestsPackage.setVariableYmls(Arrays.asList(TEST_VAR, TEST_VAR_1));
    appName = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, "app");
    assertThat(appName).isEqualTo("TEST_prod__dummy");

    // manifest is pcf legacy manifest, and contains ${APPLICATION_NAME}
    pcfManifestsPackage.setManifestYml(TEST_APP_MANIFEST.replace(REPLACE_ME, LEGACY_NAME_PCF_MANIFEST));
    appName = pcfStateHelper.fetchPcfApplicationName(pcfManifestsPackage, "TEST");
    assertThat(appName).isEqualTo("TEST");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetManifestFromPcfServiceSpecification() {
    String ymlContent = "yml";
    doReturn(null)
        .doReturn(PcfServiceSpecification.builder().manifestYaml(ymlContent).build())
        .when(serviceResourceService)
        .getPcfServiceSpecification(anyString(), any());

    try {
      pcfStateHelper.getManifestFromPcfServiceSpecification(context, SERVICE_ELEMENT);
      fail("Exception was expected");
    } catch (Exception e) {
      assertThat(e instanceof InvalidArgumentsException).isTrue();
    }

    assertThat(pcfStateHelper.getManifestFromPcfServiceSpecification(context, SERVICE_ELEMENT)).isEqualTo(ymlContent);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testAddToPcfManifestFilesMap() {
    PcfManifestsPackage pcfManifestsPackage = PcfManifestsPackage.builder().build();
    pcfStateHelper.addToPcfManifestFilesMap(
        TEST_APP_MANIFEST, pcfManifestsPackage, null, logCallback, null, CFManifestDataInfo.builder().build());

    // 1
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(TEST_APP_MANIFEST);

    // 2
    pcfStateHelper.addToPcfManifestFilesMap(
        TEST_VAR, pcfManifestsPackage, null, logCallback, null, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage.getVariableYmls()).isNotEmpty();
    assertThat(pcfManifestsPackage.getVariableYmls().get(0)).isEqualTo(TEST_VAR);

    // 3
    pcfStateHelper.addToPcfManifestFilesMap(
        TEST_VAR_1, pcfManifestsPackage, null, logCallback, null, CFManifestDataInfo.builder().build());
    assertThat(pcfManifestsPackage.getVariableYmls()).isNotEmpty();
    assertThat(pcfManifestsPackage.getVariableYmls().get(0)).isEqualTo(TEST_VAR);
    assertThat(pcfManifestsPackage.getVariableYmls().get(1)).isEqualTo(TEST_VAR_1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUseNoRoute() {
    assertThat(pcfStateHelper.getRouteMaps(TEST_APP_MANIFEST, PcfInfrastructureMapping.builder().build())).isEmpty();

    String route = "url1";
    List<String> routeMaps = pcfStateHelper.getRouteMaps(
        TEST_APP_MANIFEST, PcfInfrastructureMapping.builder().routeMaps(Arrays.asList(route)).build());
    assertThat(routeMaps).isNotEmpty();
    assertThat(routeMaps.get(0)).isEqualTo(route);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEvaluateExpressionsInManifestTypes() {
    String v1 = "app:app1";
    String v2 = "count:1";

    PcfManifestsPackage pcfManifestsPackage = PcfManifestsPackage.builder()
                                                  .manifestYml(TEST_APP_MANIFEST)
                                                  .variableYmls(Arrays.asList(TEST_VAR, TEST_VAR_1))
                                                  .build();
    doReturn(v1).doReturn(v2).when(context).renderExpression(anyString());

    pcfStateHelper.evaluateExpressionsInManifestTypes(context, pcfManifestsPackage);
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(TEST_APP_MANIFEST);
    assertThat(pcfManifestsPackage.getVariableYmls()).isNotEmpty();
    assertThat(pcfManifestsPackage.getVariableYmls().size()).isEqualTo(2);
    assertThat(pcfManifestsPackage.getVariableYmls().get(0)).isEqualTo(v1);
    assertThat(pcfManifestsPackage.getVariableYmls().get(1)).isEqualTo(v2);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testIsValuesInCustomSource() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    assertThat(pcfStateHelper.isValuesInCustomSource(appManifestMap)).isFalse();
    appManifestMap.put(K8sValuesLocation.Service, generateAppManifest(CUSTOM, ENV_ID));
    assertThat(pcfStateHelper.isValuesInCustomSource(appManifestMap)).isTrue();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testIsValidManifestFile() {
    assertThat(pcfStateHelper.isValidManifest(SERVICE_MANIFEST_YML, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest(TEST_APP_MANIFEST, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest(TEST_VAR, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest(TEST_VAR_1, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest(ENV_MANIFEST_YML, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest(ENV_SERVICE_MANIFEST_YML, logCallback)).isTrue();
    assertThat(pcfStateHelper.isValidManifest("", logCallback)).isFalse();
    assertThat(pcfStateHelper.isValidManifest(null, logCallback)).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateCustomFetchValuesTask() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service, generateAppManifest(CUSTOM, SERVICE_ID));

    ExecutionContextImpl context = Mockito.mock(ExecutionContextImpl.class);
    doReturn(anApplication().build()).when(context).getApp();
    doReturn(anEnvironment().build()).when(context).getEnv();
    doReturn(new PcfInfrastructureMapping()).when(infrastructureMappingService).get(any(), any());
    doReturn(CustomManifestValuesFetchParams.builder().build())
        .when(applicationManifestUtils)
        .createCustomManifestValuesFetchParams(context, appManifestMap, VARS_YML);

    DelegateTask task = pcfStateHelper.createCustomFetchValuesTask(context, appManifestMap, ACTIVITY_ID, true, 100);
    assertThat(task).isNotNull();
    assertThat(task.isSelectionLogsTrackingEnabled()).isTrue();
    assertThat(task.getData().getTaskType()).isEqualTo(CUSTOM_MANIFEST_FETCH_TASK.name());
    assertThat(task.getData().getParameters()).hasSize(1);
    assertThat(task.getData().getTimeout()).isEqualTo(100);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGenerateManifestMap() {
    Service service = Service.builder().uuid(SERVICE_ID).isPcfV2(true).build();
    doReturn(service).when(serviceResourceService).get(any());

    Map<K8sValuesLocation, ApplicationManifest> map = new HashMap<>();
    map.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .storeType(StoreType.Local)
            .serviceId(SERVICE_ID)
            .kind(AppManifestKind.PCF_OVERRIDE)
            .build());

    doReturn(PcfSetupStateExecutionData.builder().appManifestMap(map).build())
        .doReturn(null)
        .when(context)
        .getStateExecutionData();

    doReturn(Arrays.asList(ManifestFile.builder().fileContent(TEST_APP_MANIFEST).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(any(), any());

    PcfManifestsPackage pcfManifestsPackage =
        pcfStateHelper.generateManifestMap(context, map, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID);

    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(TEST_APP_MANIFEST);

    pcfManifestsPackage =
        pcfStateHelper.generateManifestMap(context, map, SERVICE_ELEMENT, ACTIVITY_ID, DUMMY_ACCOUNT_ID);
    assertThat(pcfManifestsPackage.getManifestYml()).isEqualTo(TEST_APP_MANIFEST);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsManifestInGit() throws Exception {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service, ApplicationManifest.builder().storeType(Remote).build());
    appManifestMap.put(K8sValuesLocation.Environment, ApplicationManifest.builder().storeType(Local).build());
    assertThat(pcfStateHelper.isManifestInGit(appManifestMap)).isTrue();

    appManifestMap.remove(K8sValuesLocation.Service);
    assertThat(pcfStateHelper.isManifestInGit(appManifestMap)).isFalse();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFindSetupSweepingOutputForNewExecution() throws Exception {
    final String infraDefinitionId = generateUuid();
    final String setupSweepingOutputPcfId = generateUuid();
    final String phaseName = "Phase 1";
    PhaseElement phaseElement =
        PhaseElement.builder().infraDefinitionId(infraDefinitionId).phaseName(phaseName).build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.prepareSweepingOutputInquiryBuilder())
        .thenReturn(SweepingOutputInquiry.builder()
                        .appId(APP_ID)
                        .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                        .stateExecutionId(STATE_EXECUTION_ID));
    SetupSweepingOutputPcf setupSweepingOutputPcf =
        SetupSweepingOutputPcf.builder()
            .uuid(setupSweepingOutputPcfId)
            .name(PCF_SERVICE_NAME)
            .maxInstanceCount(10)
            .desiredActualFinalCount(10)
            .pcfCommandRequest(CfCommandSetupRequest.builder().space("SPACE").organization("ORG").build())
            .newPcfApplicationDetails(CfAppSetupTimeDetails.builder()
                                          .applicationName("APP_NAME_SERVICE_NAME_ENV_NAME__1")
                                          .applicationGuid("1")
                                          .build())
            .infraMappingId(INFRA_MAPPING_ID)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .routeMaps(Arrays.asList("R1", "R2"))
            .build();

    when(sweepingOutputService.findSweepingOutput(any())).thenReturn(setupSweepingOutputPcf);
    SetupSweepingOutputPcf sweepingOutputPcf = pcfStateHelper.findSetupSweepingOutputPcf(context, false);
    assertThat(sweepingOutputPcf).isNotNull();
    assertThat(sweepingOutputPcf.getUuid()).isEqualTo(setupSweepingOutputPcfId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFindSetupSweepingOutputSecondPhase() throws Exception {
    final String infraDefinitionId = generateUuid();
    final String phaseName = "Phase 1";
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder()
                                                                    .appId(APP_ID)
                                                                    .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                    .stateExecutionId(STATE_EXECUTION_ID);
    PhaseElement phaseElement =
        PhaseElement.builder().infraDefinitionId(infraDefinitionId).phaseName(phaseName).build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);
    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + phaseName).build()))
        .thenReturn(null);
    when(stateExecutionService.fetchPreviousPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, STATE_EXECUTION_ID))
        .thenReturn(null);
    SetupSweepingOutputPcf sweepingOutputPcf = pcfStateHelper.findSetupSweepingOutputPcf(context, false);
    assertThat(sweepingOutputPcf).isNotNull();
    assertThat(sweepingOutputPcf.getUuid()).isNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testFindSetupSweepingOutputSecondPhaseServiceRepeat() throws Exception {
    final String infraDefinitionId = generateUuid();
    final String firstPhaseName = "Phase 1";
    final String secondPhaseName = "Phase 2";
    final String currentStateExecutionId = generateUuid();
    final String previousStateExecutionId = generateUuid();
    final String serviceId = generateUuid();
    final String setupSweepingOutputPcfId = generateUuid();

    SetupSweepingOutputPcf setupSweepingOutputPcf =
        SetupSweepingOutputPcf.builder()
            .uuid(setupSweepingOutputPcfId)
            .name(PCF_SERVICE_NAME)
            .maxInstanceCount(10)
            .desiredActualFinalCount(10)
            .pcfCommandRequest(CfCommandSetupRequest.builder().space("SPACE").organization("ORG").build())
            .newPcfApplicationDetails(CfAppSetupTimeDetails.builder()
                                          .applicationName("APP_NAME_SERVICE_NAME_ENV_NAME__1")
                                          .applicationGuid("1")
                                          .build())
            .infraMappingId(INFRA_MAPPING_ID)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .routeMaps(Arrays.asList("R1", "R2"))
            .build();

    PhaseExecutionData phaseExecutionData = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                .withInfraDefinitionId(INFRA_DEFINITION_ID)
                                                .withServiceId(serviceId)
                                                .build();

    PhaseExecutionData phaseExecutionData2 = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                 .withInfraDefinitionId(SECOND_INFRA_DEFINITION_ID)
                                                 .withServiceId(serviceId)
                                                 .build();

    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder1 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(currentStateExecutionId);
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder2 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(previousStateExecutionId);
    PhaseElement phaseElement =
        PhaseElement.builder().infraDefinitionId(infraDefinitionId).phaseName(secondPhaseName).build();

    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder1);
    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder1.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + secondPhaseName).build()))
        .thenReturn(null);

    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder2.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + firstPhaseName).build()))
        .thenReturn(setupSweepingOutputPcf);

    StateExecutionInstance currentStateExecutionInstance = Builder.aStateExecutionInstance()
                                                               .uuid(currentStateExecutionId)
                                                               .displayName(secondPhaseName)
                                                               .stateName(secondPhaseName)
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .stateType(StateType.PHASE.name())
                                                               .addContextElement(phaseElement)
                                                               .build();

    StateExecutionInstance previousStateExecutionInstance = Builder.aStateExecutionInstance()
                                                                .uuid(previousStateExecutionId)
                                                                .displayName(firstPhaseName)
                                                                .stateName(firstPhaseName)
                                                                .appId(APP_ID)
                                                                .executionUuid(WORKFLOW_EXECUTION_ID)
                                                                .stateType(StateType.PHASE.name())
                                                                .addContextElement(phaseElement)
                                                                .build();

    when(stateExecutionService.fetchPreviousPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(previousStateExecutionInstance);
    when(stateExecutionService.fetchCurrentPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(currentStateExecutionInstance);

    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(currentStateExecutionInstance))
        .thenReturn(phaseExecutionData);
    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(previousStateExecutionInstance))
        .thenReturn(phaseExecutionData2);

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG").space("SPACE").cloudProviderId("CP1").build())
                .uuid(INFRA_DEFINITION_ID)
                .build()));

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(SECOND_INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG").space("SPACE").cloudProviderId("CP2").build())
                .uuid(INFRA_DEFINITION_ID)
                .uuid(SECOND_INFRA_DEFINITION_ID)
                .build()));

    doReturn("ORG").when(context).renderExpression("ORG");
    doReturn("SPACE").when(context).renderExpression("SPACE");
    doReturn("ORG").when(context).renderExpression("ORG");
    doReturn("SPACE").when(context).renderExpression("SPACE");

    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL").build()).build())
        .when(settingsService)
        .get("CP1");
    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL").build()).build())
        .when(settingsService)
        .get("CP2");

    when(workflowExecutionService.checkIfOnDemand(any(), any())).thenReturn(false);

    SetupSweepingOutputPcf sweepingOutputPcf = pcfStateHelper.findSetupSweepingOutputPcf(context, false);
    assertThat(sweepingOutputPcf).isNotNull();
    assertThat(sweepingOutputPcf.getUuid()).isEqualTo(setupSweepingOutputPcf.getUuid());
    assertThat(sweepingOutputPcf.getName()).isEqualTo(setupSweepingOutputPcf.getName());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFindSetupSweepingOutputSecondPhaseServiceFailsIfDifferentOrgOrSpace() throws Exception {
    final String infraDefinitionId = generateUuid();
    final String firstPhaseName = "Phase 1";
    final String secondPhaseName = "Phase 2";
    final String currentStateExecutionId = generateUuid();
    final String previousStateExecutionId = generateUuid();
    final String serviceId = generateUuid();
    final String setupSweepingOutputPcfId = generateUuid();

    SetupSweepingOutputPcf setupSweepingOutputPcf =
        SetupSweepingOutputPcf.builder()
            .uuid(setupSweepingOutputPcfId)
            .name(PCF_SERVICE_NAME)
            .maxInstanceCount(10)
            .desiredActualFinalCount(10)
            .pcfCommandRequest(CfCommandSetupRequest.builder().space("SPACE").organization("ORG").build())
            .newPcfApplicationDetails(CfAppSetupTimeDetails.builder()
                                          .applicationName("APP_NAME_SERVICE_NAME_ENV_NAME__1")
                                          .applicationGuid("1")
                                          .build())
            .infraMappingId(INFRA_MAPPING_ID)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .routeMaps(Arrays.asList("R1", "R2"))
            .build();

    PhaseExecutionData phaseExecutionData = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                .withInfraDefinitionId(INFRA_DEFINITION_ID)
                                                .withServiceId(serviceId)
                                                .build();

    PhaseExecutionData phaseExecutionData2 = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                 .withInfraDefinitionId(SECOND_INFRA_DEFINITION_ID)
                                                 .withServiceId(serviceId)
                                                 .build();

    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder1 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(currentStateExecutionId);
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder2 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(previousStateExecutionId);
    PhaseElement phaseElement =
        PhaseElement.builder().infraDefinitionId(infraDefinitionId).phaseName(secondPhaseName).build();

    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder1);
    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder1.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + secondPhaseName).build()))
        .thenReturn(null);

    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder2.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + firstPhaseName).build()))
        .thenReturn(setupSweepingOutputPcf);

    StateExecutionInstance currentStateExecutionInstance = Builder.aStateExecutionInstance()
                                                               .uuid(currentStateExecutionId)
                                                               .displayName(secondPhaseName)
                                                               .stateName(secondPhaseName)
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .stateType(StateType.PHASE.name())
                                                               .addContextElement(phaseElement)
                                                               .build();

    StateExecutionInstance previousStateExecutionInstance = Builder.aStateExecutionInstance()
                                                                .uuid(previousStateExecutionId)
                                                                .displayName(firstPhaseName)
                                                                .stateName(firstPhaseName)
                                                                .appId(APP_ID)
                                                                .executionUuid(WORKFLOW_EXECUTION_ID)
                                                                .stateType(StateType.PHASE.name())
                                                                .addContextElement(phaseElement)
                                                                .build();

    when(stateExecutionService.fetchPreviousPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(previousStateExecutionInstance);
    when(stateExecutionService.fetchCurrentPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(currentStateExecutionInstance);

    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(currentStateExecutionInstance))
        .thenReturn(phaseExecutionData);
    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(previousStateExecutionInstance))
        .thenReturn(phaseExecutionData2);

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG").space("SPACE").cloudProviderId("CP1").build())
                .uuid(INFRA_DEFINITION_ID)
                .build()));

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(SECOND_INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG2").space("SPACE2").cloudProviderId("CP2").build())
                .uuid(SECOND_INFRA_DEFINITION_ID)
                .build()));

    doReturn("ORG").when(context).renderExpression("ORG");
    doReturn("SPACE").when(context).renderExpression("SPACE");
    doReturn("ORG2").when(context).renderExpression("ORG2");
    doReturn("SPACE2").when(context).renderExpression("SPACE2");

    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL").build()).build())
        .when(settingsService)
        .get("CP1");
    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL").build()).build())
        .when(settingsService)
        .get("CP2");

    when(workflowExecutionService.checkIfOnDemand(anyString(), anyString())).thenReturn(false);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> pcfStateHelper.findSetupSweepingOutputPcf(context, false))
        .withMessageContaining("Different Infrastructure or Service on worklflow phases");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFindSetupSweepingOutputSecondPhaseServiceFailsIfDifferentEndpointUrl() throws Exception {
    final String infraDefinitionId = generateUuid();
    final String firstPhaseName = "Phase 1";
    final String secondPhaseName = "Phase 2";
    final String currentStateExecutionId = generateUuid();
    final String previousStateExecutionId = generateUuid();
    final String serviceId = generateUuid();
    final String setupSweepingOutputPcfId = generateUuid();

    SetupSweepingOutputPcf setupSweepingOutputPcf =
        SetupSweepingOutputPcf.builder()
            .uuid(setupSweepingOutputPcfId)
            .name(PCF_SERVICE_NAME)
            .maxInstanceCount(10)
            .desiredActualFinalCount(10)
            .pcfCommandRequest(CfCommandSetupRequest.builder().space("SPACE").organization("ORG").build())
            .newPcfApplicationDetails(CfAppSetupTimeDetails.builder()
                                          .applicationName("APP_NAME_SERVICE_NAME_ENV_NAME__1")
                                          .applicationGuid("1")
                                          .build())
            .infraMappingId(INFRA_MAPPING_ID)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .routeMaps(Arrays.asList("R1", "R2"))
            .build();

    PhaseExecutionData phaseExecutionData = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                .withInfraDefinitionId(INFRA_DEFINITION_ID)
                                                .withServiceId(serviceId)
                                                .build();

    PhaseExecutionData phaseExecutionData2 = PhaseExecutionDataBuilder.aPhaseExecutionData()
                                                 .withInfraDefinitionId(SECOND_INFRA_DEFINITION_ID)
                                                 .withServiceId(serviceId)
                                                 .build();

    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder1 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(currentStateExecutionId);
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder2 = SweepingOutputInquiry.builder()
                                                                     .appId(APP_ID)
                                                                     .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                                                     .stateExecutionId(previousStateExecutionId);
    PhaseElement phaseElement =
        PhaseElement.builder().infraDefinitionId(infraDefinitionId).phaseName(secondPhaseName).build();

    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder1);
    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder1.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + secondPhaseName).build()))
        .thenReturn(null);

    when(sweepingOutputService.findSweepingOutput(
             sweepingOutputInquiryBuilder2.name(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + firstPhaseName).build()))
        .thenReturn(setupSweepingOutputPcf);

    StateExecutionInstance currentStateExecutionInstance = Builder.aStateExecutionInstance()
                                                               .uuid(currentStateExecutionId)
                                                               .displayName(secondPhaseName)
                                                               .stateName(secondPhaseName)
                                                               .appId(APP_ID)
                                                               .executionUuid(WORKFLOW_EXECUTION_ID)
                                                               .stateType(StateType.PHASE.name())
                                                               .addContextElement(phaseElement)
                                                               .build();

    StateExecutionInstance previousStateExecutionInstance = Builder.aStateExecutionInstance()
                                                                .uuid(previousStateExecutionId)
                                                                .displayName(firstPhaseName)
                                                                .stateName(firstPhaseName)
                                                                .appId(APP_ID)
                                                                .executionUuid(WORKFLOW_EXECUTION_ID)
                                                                .stateType(StateType.PHASE.name())
                                                                .addContextElement(phaseElement)
                                                                .build();

    when(stateExecutionService.fetchPreviousPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(previousStateExecutionInstance);
    when(stateExecutionService.fetchCurrentPhaseStateExecutionInstance(
             APP_ID, WORKFLOW_EXECUTION_ID, currentStateExecutionId))
        .thenReturn(currentStateExecutionInstance);

    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(currentStateExecutionInstance))
        .thenReturn(phaseExecutionData);
    when(stateExecutionService.fetchPhaseExecutionDataSweepingOutput(previousStateExecutionInstance))
        .thenReturn(phaseExecutionData2);

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG").space("SPACE").cloudProviderId("CP1").build())
                .uuid(INFRA_DEFINITION_ID)
                .build()));

    when(infrastructureDefinitionService.getInfraStructureDefinitionByUuids(
             eq(APP_ID), eq(Collections.singletonList(SECOND_INFRA_DEFINITION_ID))))
        .thenReturn(Collections.singletonList(
            InfrastructureDefinition.builder()
                .appId(HARNESS_APPLICATION_ID)
                .infrastructure(
                    PcfInfraStructure.builder().organization("ORG2").space("SPACE2").cloudProviderId("CP2").build())
                .uuid(SECOND_INFRA_DEFINITION_ID)
                .build()));

    doReturn("ORG").when(context).renderExpression("ORG");
    doReturn("SPACE").when(context).renderExpression("SPACE");
    doReturn("ORG").when(context).renderExpression("ORG");
    doReturn("SPACE").when(context).renderExpression("SPACE");

    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL1").build()).build())
        .when(settingsService)
        .get("CP1");
    doReturn(
        SettingAttribute.Builder.aSettingAttribute().withValue(PcfConfig.builder().endpointUrl("URL2").build()).build())
        .when(settingsService)
        .get("CP2");

    when(workflowExecutionService.checkIfOnDemand(anyString(), anyString())).thenReturn(false);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> pcfStateHelper.findSetupSweepingOutputPcf(context, false))
        .withMessageContaining("Different Infrastructure or Service on worklflow phases");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainDeploySweepingOutputName() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName("Phase 1").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainDeploySweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(DeploySweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainDeploySweepingOutputNameWithWhiteSpaces() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName(" Phase 1 ").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainDeploySweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(DeploySweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainSetupSweepingOutputName() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName("Phase 1").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainSetupSweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainSetupSweepingOutputNameWithWhiteSpaces() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName(" Phase 1 ").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainSetupSweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(SetupSweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainSwapRouteRollbackSweepingOutputName() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName("Phase 1").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainSwapRouteSweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(SwapRouteRollbackSweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testObtainSwapRouteRollbackSweepingOutputNameWithWhiteSpaces() {
    PhaseElement phaseElement =
        PhaseElement.builder().phaseNameForRollback("Rollback Phase 1").phaseName(" Phase 1 ").build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    String outputName = pcfStateHelper.obtainSwapRouteSweepingOutputName(context, false);
    assertThat(outputName).isNotNull();
    assertThat(outputName).isEqualTo(SwapRouteRollbackSweepingOutputPcf.SWEEPING_OUTPUT_NAME + "Phase 1");
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testGetPhaseNameForQuery() {
    final String phaseName = "Phase 1";
    when(workflowExecutionService.checkIfOnDemand(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(false);
    String queryPhaseName = pcfStateHelper.getPhaseNameForQuery(APP_ID, WORKFLOW_EXECUTION_ID, phaseName);
    assertThat(queryPhaseName).isNotNull();
    assertThat(queryPhaseName).isEqualTo(phaseName);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testGetPhaseNameForQueryRollback() {
    final String phaseName = "Staging Execution Phase 1";
    when(workflowExecutionService.checkIfOnDemand(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(true);
    String queryPhaseName = pcfStateHelper.getPhaseNameForQuery(APP_ID, WORKFLOW_EXECUTION_ID, phaseName);
    assertThat(queryPhaseName).isNotNull();
    assertThat(queryPhaseName).isEqualTo("Phase 1");
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testIsRollBackNotNeeded() {
    SetupSweepingOutputPcf setupSweepingOutputPcf = null;
    SetupSweepingOutputPcf setupSweepingOutputPcf1 = SetupSweepingOutputPcf.builder().isSuccess(false).build();
    SetupSweepingOutputPcf setupSweepingOutputPcf2 = SetupSweepingOutputPcf.builder().isSuccess(true).build();

    assertThat(pcfStateHelper.isRollBackNotNeeded(setupSweepingOutputPcf)).isTrue();
    assertThat(pcfStateHelper.isRollBackNotNeeded(setupSweepingOutputPcf1)).isTrue();
    assertThat(pcfStateHelper.isRollBackNotNeeded(setupSweepingOutputPcf2)).isFalse();
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testHandleRollbackSkipped() {
    PcfDeployStateExecutionData pcfDeployStateExecutionData =
        PcfDeployStateExecutionData.builder()
            .activityId(ACTIVITY_ID)
            .commandName(COMMAND_NAME)
            .updateDetails(new StringBuilder().append("test message").toString())
            .build();

    doReturn(true).when(logService).batchedSaveCommandUnitLogs(any(), any(), any());

    ExecutionResponse executionResponse =
        pcfStateHelper.handleRollbackSkipped(APP_ID, ACTIVITY_ID, COMMAND_NAME, "test message");

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(pcfDeployStateExecutionData);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetStateTimeoutMillis() {
    PcfStateHelper pcfStateHelper = spy(PcfStateHelper.class);
    doReturn(SetupSweepingOutputPcf.builder().timeoutIntervalInMinutes(10).build())
        .when(pcfStateHelper)
        .findSetupSweepingOutputPcf(context, false);
    assertThat(pcfStateHelper.getStateTimeoutMillis(context, 5, false)).isEqualTo(10 * 60 * 1000);

    doReturn(SetupSweepingOutputPcf.builder().timeoutIntervalInMinutes(null).build())
        .when(pcfStateHelper)
        .findSetupSweepingOutputPcf(context, false);
    assertThat(pcfStateHelper.getStateTimeoutMillis(context, 5, false)).isEqualTo(5 * 60 * 1000);

    doThrow(new InvalidArgumentsException("test")).when(pcfStateHelper).findSetupSweepingOutputPcf(context, false);
    assertThat(pcfStateHelper.getStateTimeoutMillis(context, 5, false)).isEqualTo(5 * 60 * 1000);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateInstanceElement() {
    PcfStateHelper pcfStateHelper = spy(PcfStateHelper.class);
    PcfInstanceElement app1 = PcfInstanceElement.builder()
                                  .uuid("1")
                                  .applicationId("1")
                                  .displayName("app1")
                                  .isUpsize(true)
                                  .instanceIndex("4")
                                  .build();
    PcfInstanceElement app0 = PcfInstanceElement.builder()
                                  .uuid("0")
                                  .isUpsize(false)
                                  .displayName("app0")
                                  .applicationId("0")
                                  .instanceIndex("2")
                                  .build();
    List<PcfInstanceElement> pcfInstanceElements = Arrays.asList(app1, app0);

    List<InstanceElement> instanceElements = pcfStateHelper.generateInstanceElement(pcfInstanceElements);

    assertThat(instanceElements.size()).isEqualTo(2);
    assertThat(instanceElements.get(0).getUuid()).isEqualTo("1");
    assertThat(instanceElements.get(0).getDisplayName()).isEqualTo("app1");
    assertThat(instanceElements.get(0).getHostName()).isEqualTo("app1:4");
    assertThat(instanceElements.get(0).getHost().getPcfElement()).isEqualTo(app1);

    assertThat(instanceElements.get(1).getUuid()).isEqualTo("0");
    assertThat(instanceElements.get(1).getDisplayName()).isEqualTo("app0");
    assertThat(instanceElements.get(1).getHostName()).isEqualTo("app0:2");
    assertThat(instanceElements.get(1).getHost().getPcfElement()).isEqualTo(app0);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRemoveCommentedLinesFromScript() {
    String scriptWithCommentedLines = "#line 1\n#line 2 \n line 3";
    String scriptWithoutCommentedLines = pcfStateHelper.removeCommentedLineFromScript(scriptWithCommentedLines);

    assertThat(scriptWithoutCommentedLines).doesNotContain("line 1");
    assertThat(scriptWithoutCommentedLines).doesNotContain("line 2");
    assertThat(scriptWithoutCommentedLines).contains("line 3");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testUpdateInfoVariables() {
    PcfRouteUpdateStateExecutionData stateExecutionData =
        PcfRouteUpdateStateExecutionData.builder()
            .pcfRouteUpdateRequestConfigData(
                CfRouteUpdateRequestConfigData.builder().finalRoutes(Collections.singletonList("NEW_ROUTE")).build())
            .build();
    InfoVariables infoVariables = InfoVariables.builder().newAppRoutes(Collections.singletonList("TEMP_ROUTE")).build();
    doReturn(SweepingOutputInstance.builder().uuid("1").value(infoVariables).build())
        .when(sweepingOutputService)
        .find(any());
    doReturn(APP_ID).when(context).getAppId();
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);

    pcfStateHelper.updateInfoVariables(
        context, stateExecutionData, CfCommandExecutionResponse.builder().build(), false);

    verify(sweepingOutputService, times(1)).deleteById(APP_ID, "1");
    verify(sweepingOutputService, times(1)).ensure(captor.capture());
    SweepingOutputInstance sweepingOutputInstance = captor.getValue();
    InfoVariables savedInfoVariables = (InfoVariables) sweepingOutputInstance.getValue();
    assertThat(savedInfoVariables.getNewAppRoutes().get(0)).isEqualTo("NEW_ROUTE");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliVersionOrDefaultWithDefaultV6Version() {
    Service pcfService = Service.builder().deploymentType(DeploymentType.PCF).cfCliVersion(null).build();
    doReturn(pcfService).when(serviceResourceService).get("app-id", "service-id");

    CfCliVersion cfCliVersionOrDefault = pcfStateHelper.getCfCliVersionOrDefault("app-id", "service-id");

    assertThat(cfCliVersionOrDefault).isNotNull();
    assertThat(cfCliVersionOrDefault).isEqualTo(CfCliVersion.V6);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliVersionWithV7Version() {
    Service pcfService = Service.builder().deploymentType(DeploymentType.PCF).cfCliVersion(CfCliVersion.V7).build();
    doReturn(pcfService).when(serviceResourceService).get("app-id", "service-id");

    CfCliVersion cfCliVersionOrDefault = pcfStateHelper.getCfCliVersionOrDefault("app-id", "service-id");

    assertThat(cfCliVersionOrDefault).isNotNull();
    assertThat(cfCliVersionOrDefault).isEqualTo(CfCliVersion.V7);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetCfCliVersionWithV6Version() {
    Service pcfService = Service.builder().deploymentType(DeploymentType.PCF).cfCliVersion(CfCliVersion.V6).build();
    doReturn(pcfService).when(serviceResourceService).get("app-id", "service-id");

    CfCliVersion cfCliVersionOrDefault = pcfStateHelper.getCfCliVersionOrDefault("app-id", "service-id");

    assertThat(cfCliVersionOrDefault).isNotNull();
    assertThat(cfCliVersionOrDefault).isEqualTo(CfCliVersion.V6);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesVersionToVersion() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp__2";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + "__3";
    String prevInActiveAppOldName = "PaymentApp__1";

    // app should not have been renamed during version to version deployment
    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder().build();
    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    // this should have been saved with this content after app setup step
    InfoVariables existingInfoVariables = InfoVariables.builder()
                                              .activeAppName(oldAppName)
                                              .inActiveAppName(newAppName)
                                              .oldAppName(oldAppName)
                                              .oldAppGuid(oldAppId)
                                              .newAppName(newAppName)
                                              .newAppGuid(newAppId)
                                              .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                              .build();

    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(existingInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, false);

    ArgumentCaptor<SweepingOutputInstance> outputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(outputInstanceArgumentCaptor.capture());

    SweepingOutputInstance captorValue = outputInstanceArgumentCaptor.getValue();
    assertThat(captorValue).isNotNull();
    InfoVariables updatedInfoVariables = (InfoVariables) captorValue.getValue();

    assertThat(updatedInfoVariables.getNewAppName()).isEqualTo(newAppName);
    assertThat(updatedInfoVariables.getOldAppName()).isEqualTo(oldAppName);

    assertThat(updatedInfoVariables.getActiveAppName()).isEqualTo(newAppName);
    assertThat(updatedInfoVariables.getInActiveAppName()).isEqualTo(oldAppName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesVersionToVersionRollback() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp__2";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + "__3";
    String prevInActiveAppOldName = "PaymentApp__1";
    // app should not have been renamed during version to version rollback deployment
    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder().build();
    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    InfoVariables afterSwapInfoVariables = InfoVariables.builder()
                                               .activeAppName(newAppName)
                                               .inActiveAppName(oldAppName)
                                               .oldAppName(oldAppName)
                                               .oldAppGuid(oldAppId)
                                               .newAppName(newAppName)
                                               .newAppGuid(newAppId)
                                               .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                               .build();
    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(afterSwapInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, true);
    ArgumentCaptor<SweepingOutputInstance> rollbackSweepingCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(rollbackSweepingCaptor.capture());
    SweepingOutputInstance rollbackValue = rollbackSweepingCaptor.getValue();
    assertThat(rollbackSweepingCaptor).isNotNull();
    InfoVariables rollbackInfoVariables = (InfoVariables) rollbackValue.getValue();
    assertThat(rollbackInfoVariables.getNewAppName()).isEqualTo(newAppName);
    assertThat(rollbackInfoVariables.getOldAppName()).isEqualTo(oldAppName);

    assertThat(rollbackInfoVariables.getActiveAppName()).isEqualTo(oldAppName);
    assertThat(rollbackInfoVariables.getInActiveAppName()).isEqualTo(prevInActiveAppOldName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesVersionToNonVersion() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp__2";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = "PaymentApp__INACTIVE";
    String prevInActiveAppOldName = "PaymentApp__1";

    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder()
                                                       .oldAppGuid(oldAppId)
                                                       .oldAppName(newAppName)
                                                       .newAppGuid(newAppId)
                                                       .newAppName(appPrefix)
                                                       .build();
    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    // this should have been saved with this content after app setup step
    InfoVariables existingInfoVariables = InfoVariables.builder()
                                              .activeAppName(oldAppName)
                                              .inActiveAppName(newAppName)
                                              .oldAppName(oldAppName)
                                              .oldAppGuid(oldAppId)
                                              .newAppName(newAppName)
                                              .newAppGuid(newAppId)
                                              .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                              .build();

    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(existingInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, false);

    ArgumentCaptor<SweepingOutputInstance> outputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(outputInstanceArgumentCaptor.capture());

    SweepingOutputInstance captorValue = outputInstanceArgumentCaptor.getValue();
    assertThat(captorValue).isNotNull();
    InfoVariables updatedInfoVariables = (InfoVariables) captorValue.getValue();

    assertThat(updatedInfoVariables.getNewAppName()).isEqualTo(updatedValues.getNewAppName());
    assertThat(updatedInfoVariables.getOldAppName()).isEqualTo(updatedValues.getOldAppName());

    assertThat(updatedInfoVariables.getActiveAppName()).isEqualTo(appPrefix);
    assertThat(updatedInfoVariables.getInActiveAppName()).isEqualTo(newAppName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesVersionToNonVersionRollback() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp__2";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + INTERIM_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__1";

    // app names would be renamed back to what it was before the deployment started during rollback
    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder()
                                                       .oldAppGuid(oldAppId)
                                                       .oldAppName(oldAppName)
                                                       .newAppGuid(newAppId)
                                                       .newAppName(newAppName)
                                                       .build();

    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    InfoVariables afterSwapInfoVariables = InfoVariables.builder()
                                               .activeAppName(newAppName)
                                               .inActiveAppName(oldAppName)
                                               .oldAppName(oldAppName)
                                               .oldAppGuid(oldAppId)
                                               .newAppName(newAppName)
                                               .newAppGuid(newAppId)
                                               .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                               .build();
    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(afterSwapInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, true);
    ArgumentCaptor<SweepingOutputInstance> rollbackSweepingCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(rollbackSweepingCaptor.capture());
    SweepingOutputInstance rollbackValue = rollbackSweepingCaptor.getValue();
    assertThat(rollbackSweepingCaptor).isNotNull();
    InfoVariables rollbackInfoVariables = (InfoVariables) rollbackValue.getValue();
    assertThat(rollbackInfoVariables.getNewAppName()).isEqualTo(newAppName);
    assertThat(rollbackInfoVariables.getOldAppName()).isEqualTo(oldAppName);

    assertThat(rollbackInfoVariables.getActiveAppName()).isEqualTo(oldAppName);
    assertThat(rollbackInfoVariables.getInActiveAppName()).isEqualTo(prevInActiveAppOldName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesNonVersionToNonVersion() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__INACTIVE";

    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder()
                                                       .oldAppGuid(oldAppId)
                                                       .oldAppName(newAppName)
                                                       .newAppGuid(newAppId)
                                                       .newAppName(appPrefix)
                                                       .build();
    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    // this should have been saved with this content after app setup step
    InfoVariables existingInfoVariables = InfoVariables.builder()
                                              .activeAppName(oldAppName)
                                              .inActiveAppName(newAppName)
                                              .oldAppName(oldAppName)
                                              .oldAppGuid(oldAppId)
                                              .newAppName(newAppName)
                                              .newAppGuid(newAppId)
                                              .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                              .build();

    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(existingInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, false);

    ArgumentCaptor<SweepingOutputInstance> outputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(outputInstanceArgumentCaptor.capture());

    SweepingOutputInstance captorValue = outputInstanceArgumentCaptor.getValue();
    assertThat(captorValue).isNotNull();
    InfoVariables updatedInfoVariables = (InfoVariables) captorValue.getValue();

    assertThat(updatedInfoVariables.getNewAppName()).isEqualTo(updatedValues.getNewAppName());
    assertThat(updatedInfoVariables.getOldAppName()).isEqualTo(updatedValues.getOldAppName());

    assertThat(updatedInfoVariables.getActiveAppName()).isEqualTo(appPrefix);
    assertThat(updatedInfoVariables.getInActiveAppName()).isEqualTo(newAppName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesNonVersionToNonVersionRollback() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + INTERIM_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__INACTIVE";

    // app names would be renamed back to what it was before the deployment started during rollback
    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder()
                                                       .oldAppGuid(oldAppId)
                                                       .oldAppName(oldAppName)
                                                       .newAppGuid(newAppId)
                                                       .newAppName(newAppName)
                                                       .build();

    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    InfoVariables afterSwapInfoVariables = InfoVariables.builder()
                                               .activeAppName(newAppName)
                                               .inActiveAppName(oldAppName)
                                               .oldAppName(oldAppName)
                                               .oldAppGuid(oldAppId)
                                               .newAppName(newAppName)
                                               .newAppGuid(newAppId)
                                               .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                               .build();
    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(afterSwapInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, true);
    ArgumentCaptor<SweepingOutputInstance> rollbackSweepingCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(rollbackSweepingCaptor.capture());
    SweepingOutputInstance rollbackValue = rollbackSweepingCaptor.getValue();
    assertThat(rollbackSweepingCaptor).isNotNull();
    InfoVariables rollbackInfoVariables = (InfoVariables) rollbackValue.getValue();
    assertThat(rollbackInfoVariables.getNewAppName()).isEqualTo(newAppName);
    assertThat(rollbackInfoVariables.getOldAppName()).isEqualTo(oldAppName);

    assertThat(rollbackInfoVariables.getActiveAppName()).isEqualTo(oldAppName);
    assertThat(rollbackInfoVariables.getInActiveAppName()).isEqualTo(prevInActiveAppOldName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesNonVersionToVersion() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__INACTIVE";

    CfInBuiltVariablesUpdateValues updatedValues =
        CfInBuiltVariablesUpdateValues.builder()
            .oldAppGuid(oldAppId)
            .oldAppName(appPrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX + "1")
            .newAppGuid(newAppId)
            .newAppName(appPrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX + "2")
            .build();
    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    // this should have been saved with this content after app setup step
    InfoVariables existingInfoVariables = InfoVariables.builder()
                                              .activeAppName(oldAppName)
                                              .inActiveAppName(newAppName)
                                              .oldAppName(oldAppName)
                                              .oldAppGuid(oldAppId)
                                              .newAppName(newAppName)
                                              .newAppGuid(newAppId)
                                              .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                              .build();

    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(existingInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, false);

    ArgumentCaptor<SweepingOutputInstance> outputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(outputInstanceArgumentCaptor.capture());

    SweepingOutputInstance captorValue = outputInstanceArgumentCaptor.getValue();
    assertThat(captorValue).isNotNull();
    InfoVariables updatedInfoVariables = (InfoVariables) captorValue.getValue();

    assertThat(updatedInfoVariables.getNewAppName()).isEqualTo(updatedValues.getNewAppName());
    assertThat(updatedInfoVariables.getOldAppName()).isEqualTo(updatedValues.getOldAppName());

    assertThat(updatedInfoVariables.getActiveAppName()).isEqualTo(updatedValues.getNewAppName());
    assertThat(updatedInfoVariables.getInActiveAppName()).isEqualTo(updatedValues.getOldAppName());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testActiveInActiveInBuiltVariablesNonVersionToVersionRollback() {
    String appPrefix = "PaymentApp";
    String oldAppName = "PaymentApp";
    String oldAppId = "oldAppId";
    String newAppId = "newAppId";
    String newAppName = appPrefix + INTERIM_APP_NAME_SUFFIX;
    String prevInActiveAppOldName = "PaymentApp__INACTIVE";

    // app names would be renamed back to what it was before the deployment started during rollback
    CfInBuiltVariablesUpdateValues updatedValues = CfInBuiltVariablesUpdateValues.builder()
                                                       .oldAppGuid(oldAppId)
                                                       .oldAppName(oldAppName)
                                                       .newAppGuid(newAppId)
                                                       .newAppName(newAppName)
                                                       .build();

    CfCommandExecutionResponse response =
        CfCommandExecutionResponse.builder()
            .pcfCommandResponse(CfRouteUpdateCommandResponse.builder().updateValues(updatedValues).build())
            .build();

    InfoVariables afterSwapInfoVariables = InfoVariables.builder()
                                               .activeAppName(newAppName)
                                               .inActiveAppName(oldAppName)
                                               .oldAppName(oldAppName)
                                               .oldAppGuid(oldAppId)
                                               .newAppName(newAppName)
                                               .newAppGuid(newAppId)
                                               .mostRecentInactiveAppVersionOldName(prevInActiveAppOldName)
                                               .build();
    PcfRouteUpdateStateExecutionData executionData = PcfRouteUpdateStateExecutionData.builder().build();
    SweepingOutputInstance outputInstance = SweepingOutputInstance.builder().value(afterSwapInfoVariables).build();
    doReturn(outputInstance).when(sweepingOutputService).find(any());
    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(context).prepareSweepingOutputBuilder(any());

    pcfStateHelper.updateInfoVariables(context, executionData, response, true);
    ArgumentCaptor<SweepingOutputInstance> rollbackSweepingCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);

    verify(sweepingOutputService, times(1)).deleteById(any(), any());
    verify(sweepingOutputService, times(1)).ensure(rollbackSweepingCaptor.capture());
    SweepingOutputInstance rollbackValue = rollbackSweepingCaptor.getValue();
    assertThat(rollbackSweepingCaptor).isNotNull();
    InfoVariables rollbackInfoVariables = (InfoVariables) rollbackValue.getValue();
    assertThat(rollbackInfoVariables.getNewAppName()).isEqualTo(newAppName);
    assertThat(rollbackInfoVariables.getOldAppName()).isEqualTo(oldAppName);

    assertThat(rollbackInfoVariables.getActiveAppName()).isEqualTo(oldAppName);
    assertThat(rollbackInfoVariables.getInActiveAppName()).isEqualTo(prevInActiveAppOldName);
  }
}
