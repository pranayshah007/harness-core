/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_OVERRIDES;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ServiceExecutionSummaryDetails;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.steps.output.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.steps.output.UnresolvedManifestsOutput;
import io.harness.cdng.manifest.steps.task.ManifestTaskService;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.summary.HelmChartManifestSummary;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.k8s.model.HelmVersion;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.tasks.ResponseData;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class ManifestsStepV2Test extends CategoryTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ConnectorService connectorService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Mock private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private NGLogCallback logCallback;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private ManifestTaskService manifestTaskService;
  @Mock private StrategyHelper strategyHelper;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @InjectMocks private ManifestsStepV2 step = new ManifestsStepV2();

  private AutoCloseable mocks;

  private static final String SVC_ID = "SVC_ID";
  private static final String ENV_ID = "ENV_ID";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), anyString());
    doReturn(logCallback).when(serviceStepsHelper).getServiceLogCallback(any());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void executeSyncHelm() {
    StepResponse stepResponse =
        testExecuteHelmManifest(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync() {
    StepResponse stepResponse =
        testExecute(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeAsync() {
    AsyncExecutableResponse asyncResponse =
        testExecute(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
    assertThat(asyncResponse.getCallbackIdsList().asByteStringList()).isEmpty();
  }
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeAsyncWithTasks() {
    final TaskParameters taskParameters = mock(TaskParameters.class);
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Collections.singletonList(file1));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.KUBERNETES)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("ManifestSecret1").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));
    doReturn(true).when(manifestTaskService).isSupported(any(Ambiance.class), any(ManifestOutcome.class));
    doReturn(Optional.of(TaskData.builder().parameters(new Object[] {taskParameters}).taskType("TEST_TASK").build()))
        .when(manifestTaskService)
        .createTaskData(any(Ambiance.class), any(ManifestOutcome.class));
    doReturn("taskId")
        .when(delegateGrpcClientWrapper)
        .submitAsyncTaskV2(nullable(DelegateTaskRequest.class), any(Duration.class));

    AsyncExecutableResponse asyncResponse = step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null);
    assertThat(asyncResponse.getCallbackIdsList().asByteStringList())
        .containsExactlyInAnyOrder(ByteString.copyFromUtf8("taskId"));

    ArgumentCaptor<UnresolvedManifestsOutput> captor = ArgumentCaptor.forClass(UnresolvedManifestsOutput.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq(OutcomeExpressionConstants.UNRESOLVED_MANIFESTS), captor.capture(), eq(""));
    UnresolvedManifestsOutput unresolvedManifestsOutput = captor.getValue();
    assertThat(unresolvedManifestsOutput.getTaskIdMapping()).isEqualTo(ImmutableMap.of("taskId", "file1"));
    assertThat(unresolvedManifestsOutput.getManifestsOutcome().keySet()).containsExactlyInAnyOrder("file1");
  }

  private <T> T testExecute(Supplier<T> executeMethod) {
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Collections.singletonList(file1));
    finalManifests.put(ENVIRONMENT_GLOBAL_OVERRIDES, Collections.singletonList(file2));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(file3));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.KUBERNETES)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));
    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("ManifestSecret1").build());
    listEntityDetail.add(EntityDetail.builder().name("ManifestSecret2").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));
    doReturn(false)
        .when(featureFlagHelperService)
        .isEnabled(anyString(), eq(FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC));
    verify(stageExecutionInfoService, times(0)).updateStageExecutionInfo(any(), any());

    T response = executeMethod.get();

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ManifestsOutcome outcome = captor.getValue();

    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file2").getOrder()).isEqualTo(1);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(2);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(), any(List.class), any(Boolean.class));

    return response;
  }

  private <T> T testExecuteHelmManifest(Supplier<T> executeMethod) {
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Arrays.asList(file1, file2));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.KUBERNETES)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));
    List<EntityDetail> listEntityDetail = new ArrayList<>();

    listEntityDetail.add(EntityDetail.builder().name("ManifestSecret1").build());
    listEntityDetail.add(EntityDetail.builder().name("ManifestSecret2").build());

    Set<EntityDetailProtoDTO> setEntityDetail = new HashSet<>();

    doReturn(setEntityDetail).when(entityReferenceExtractorUtils).extractReferredEntities(any(), any());

    doReturn(listEntityDetail)
        .when(entityDetailProtoToRestMapper)
        .createEntityDetailsDTO(new ArrayList<>(emptyIfNull(setEntityDetail)));
    doReturn(true)
        .when(featureFlagHelperService)
        .isEnabled(anyString(), eq(FeatureName.CDS_CUSTOM_STAGE_EXECUTION_DATA_SYNC));
    T response = executeMethod.get();

    ArgumentCaptor<StageExecutionInfoUpdateDTO> captor = ArgumentCaptor.forClass(StageExecutionInfoUpdateDTO.class);
    verify(stageExecutionInfoService, times(1)).updateStageExecutionInfo(any(), captor.capture());
    StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO = captor.getValue();
    ServiceExecutionSummaryDetails.ManifestsSummary manifestsSummary =
        stageExecutionInfoUpdateDTO.getManifestsSummary();
    assertThat(manifestsSummary.getManifestSummaries()).hasSize(1);
    HelmChartManifestSummary helmChartManifestSummary =
        (HelmChartManifestSummary) manifestsSummary.getManifestSummaries().get(0);
    assertThat(helmChartManifestSummary.getIdentifier()).isEqualTo("file1");
    assertThat(helmChartManifestSummary.getType()).isEqualTo(HelmChart);
    assertThat(helmChartManifestSummary.getChartVersion()).isEqualTo("0.1.0");

    ArgumentCaptor<ManifestsOutcome> manifestsOutcomeArgumentCaptor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), manifestsOutcomeArgumentCaptor.capture(), eq("STAGE"));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ManifestsOutcome outcome = manifestsOutcomeArgumentCaptor.getValue();

    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2");
    assertThat(outcome.get("file2").getOrder()).isEqualTo(1);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(), any(List.class), any(Boolean.class));

    return response;
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestListSync_0() {
    executeSyncFailWithInvalidManifestList_0(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestList_0() {
    executeSyncFailWithInvalidManifestList_0(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void executeSyncFailWithInvalidManifestList_0(Supplier<T> executeMethod) {
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    // 2 k8s manifests are not allowed
    ManifestConfigWrapper file2 = sampleManifestFile("file2", ManifestConfigType.K8_MANIFEST);
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Arrays.asList(file1, file2));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(file3));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.KUBERNETES)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    try {
      executeMethod.get();
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).contains("Kubernetes deployment support only one manifest of one of types");
      return;
    }

    fail("expected to raise an exception");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestListSync_1() {
    executeSyncFailWithInvalidManifestList_1(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestList_1() {
    executeSyncFailWithInvalidManifestList_1(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void executeSyncFailWithInvalidManifestList_1(Supplier<T> executeMethod) {
    ManifestConfigWrapper file1 = sampleHelmChartManifestFile("file1", ManifestConfigType.HELM_CHART);
    // 2 k8s manifests are not allowed
    ManifestConfigWrapper file2 = sampleHelmChartManifestFile("file2", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Arrays.asList(file1, file2));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(file3));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    try {
      executeMethod.get();
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage())
          .contains(
              "Multiple manifests found [file2 : HelmChart, file1 : HelmChart]. NativeHelm deployment support only one manifest of one of types: HelmChart. Remove all unused manifests");
      return;
    }

    fail("expected to raise an exception");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncConnectorNotFoundSync() {
    executeSyncConnectorNotFound(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void executeSyncConnectorNotFound() {
    executeSyncConnectorNotFound(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void executeSyncConnectorNotFound(Supplier<T> executeMethod) {
    doReturn(Optional.empty()).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Arrays.asList(file1, file2));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(file3));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    try {
      executeMethod.get();
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).contains("gitconnector");
      assertThat(ex.getMessage()).contains("not found");
      return;
    }

    fail("expected to raise an exception");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void envLevelGlobalOverrideSync() {
    envLevelGlobalOverride(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void envLevelGlobalOverride() {
    envLevelGlobalOverride(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void envLevelGlobalOverride(Supplier<T> executeMethod) {
    ManifestConfigWrapper helmchart = sampleManifestHttpHelm("helm1", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper envLevelOverride = sampleHelmRepoOverride("helmoverride1", "overriddenconnector");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Collections.singletonList(helmchart));
    finalManifests.put(ENVIRONMENT_GLOBAL_OVERRIDES, Collections.singletonList(envLevelOverride));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    executeMethod.get();

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue())
        .isEqualTo("overriddenconnector");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void svcLevelOverrideSync() {
    svcLevelOverride(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void svcLevelOverride() {
    svcLevelOverride(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcLevelOverride(Supplier<T> executeMethod) {
    ManifestConfigWrapper helmchart = sampleManifestHttpHelm("helm1", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper svcOverride = sampleHelmRepoOverride("helmoverride1", "svcoverride");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Collections.singletonList(helmchart));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(svcOverride));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    executeMethod.get();

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue()).isEqualTo("svcoverride");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesSync() {
    svcAndEnvLevelOverrides(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverrides() {
    svcAndEnvLevelOverrides(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverrides(Supplier<T> executeMethod) {
    ManifestConfigWrapper helmchart = sampleManifestHttpHelm("helm1", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper svcOverride = sampleHelmRepoOverride("helmoverride1", "svcoverride");
    ManifestConfigWrapper envOverride = sampleHelmRepoOverride("helmoverride2", "envoverride");

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, Collections.singletonList(helmchart));
    finalManifests.put(SERVICE_OVERRIDES, Collections.singletonList(svcOverride));
    finalManifests.put(ENVIRONMENT_GLOBAL_OVERRIDES, Collections.singletonList(envOverride));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .finalSvcManifestsMap(finalManifests)
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    executeMethod.get();

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue()).isEqualTo("svcoverride");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2Sync() throws IOException {
    svcAndEnvLevelOverridesV2(() -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2() throws IOException {
    svcAndEnvLevelOverridesV2(() -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverridesV2(Supplier<T> executeMethod) throws IOException {
    ManifestConfigWrapper infraOverride = sampleManifestFile("id1", ManifestConfigType.VALUES);
    ManifestConfigWrapper svcManifest = sampleManifestFile("id2", ManifestConfigType.VALUES);
    ManifestConfigWrapper envOverride = sampleManifestFile("id3", ManifestConfigType.VALUES);

    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    manifestsFromOverride.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, List.of(envOverride));
    manifestsFromOverride.put(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE, List.of(infraOverride));

    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .svcManifests(List.of(svcManifest))
                             .manifestsFromOverride(manifestsFromOverride)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    executeMethod.get();

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    ManifestsOutcome manifestsOutcome = captor.getValue();
    assertThat(manifestsOutcome).isNotNull();
    assertThat(manifestsOutcome.values().stream().map(ManifestOutcome::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("id1", "id2", "id3");

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());
    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2HelmRepoOverrideSync() throws IOException {
    svcAndEnvLevelOverridesV2HelmRepoOverride(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2HelmRepoOverride() throws IOException {
    svcAndEnvLevelOverridesV2HelmRepoOverride(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverridesV2HelmRepoOverride(Supplier<T> executeMethod) throws IOException {
    ManifestConfigWrapper svcHelmChart = sampleManifestHttpHelm("helm1", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper envOverride = sampleHelmRepoOverride("helmoverride1", "svcoverride");
    ManifestConfigWrapper infraOverride = sampleHelmRepoOverride("helmoverride2", "envoverride");

    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    manifestsFromOverride.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, List.of(envOverride));
    manifestsFromOverride.put(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE, List.of(infraOverride));

    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .svcManifests(List.of(svcHelmChart))
                             .manifestsFromOverride(manifestsFromOverride)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    executeMethod.get();

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    ManifestsOutcome manifestsOutcome = captor.getValue();
    assertThat(manifestsOutcome).isNotNull();
    assertThat(manifestsOutcome.values().stream().map(ManifestOutcome::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("helm1");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2OnlySvcManifestSync() throws IOException {
    svcAndEnvLevelOverridesV2OnlySvcManifest(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2OnlySvcManifest() throws IOException {
    svcAndEnvLevelOverridesV2OnlySvcManifest(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverridesV2OnlySvcManifest(Supplier<T> executeMethod) throws IOException {
    ManifestConfigWrapper svcManifest = sampleManifestFile("id1", ManifestConfigType.VALUES);

    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .svcManifests(List.of(svcManifest))
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    executeMethod.get();

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    ManifestsOutcome manifestsOutcome = captor.getValue();
    assertThat(manifestsOutcome).isNotNull();
    assertThat(manifestsOutcome.values().stream().map(ManifestOutcome::getIdentifier).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("id1");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2NotValidManifestTypeSync() throws IOException {
    svcAndEnvLevelOverridesV2NotValidManifestType(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2NotValidManifestType() throws IOException {
    svcAndEnvLevelOverridesV2NotValidManifestType(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverridesV2NotValidManifestType(Supplier<T> executeMethod) throws IOException {
    ManifestConfigWrapper infraOverride = sampleManifestFile("id1", ManifestConfigType.VALUES);
    ManifestConfigWrapper svcManifest = sampleManifestFile("id2", ManifestConfigType.VALUES);
    ManifestConfigWrapper envOverride = sampleManifestFile("id3", ManifestConfigType.AWS_LAMBDA);

    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    manifestsFromOverride.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, List.of(envOverride));
    manifestsFromOverride.put(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE, List.of(infraOverride));

    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .svcManifests(List.of(svcManifest))
                             .manifestsFromOverride(manifestsFromOverride)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    assertThatThrownBy(executeMethod::get)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Unsupported Manifest Types: [AwsLambdaFunctionDefinition] found for ENV_SERVICE_OVERRIDE");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2NoManifestSync() throws IOException {
    StepResponse stepResponse = svcAndEnvLevelOverridesV2NoManifest(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2NoManifest() throws IOException {
    svcAndEnvLevelOverridesV2NoManifest(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
    verify(sweepingOutputService, never())
        .consume(any(Ambiance.class), eq(OutcomeExpressionConstants.MANIFESTS), any(ExecutionSweepingOutput.class),
            anyString());
  }

  private <T> T svcAndEnvLevelOverridesV2NoManifest(Supplier<T> executeMethod) throws IOException {
    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    return executeMethod.get();
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2DuplicateIdentifierSync() throws IOException {
    svcAndEnvLevelOverridesV2DuplicateIdentifier(
        () -> step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverridesV2DuplicateIdentifier() throws IOException {
    svcAndEnvLevelOverridesV2DuplicateIdentifier(
        () -> step.executeAsync(buildAmbiance(), new EmptyStepParameters(), null, null));
  }

  private <T> void svcAndEnvLevelOverridesV2DuplicateIdentifier(Supplier<T> executeMethod) throws IOException {
    ManifestConfigWrapper infraOverride = sampleManifestFile("id1", ManifestConfigType.VALUES);
    ManifestConfigWrapper svcManifest = sampleManifestFile("id1", ManifestConfigType.VALUES);
    ManifestConfigWrapper envOverride = sampleManifestFile("id3", ManifestConfigType.VALUES);

    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    manifestsFromOverride.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE, List.of(envOverride));
    manifestsFromOverride.put(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE, List.of(infraOverride));

    doReturn(true).when(featureFlagHelperService).isEnabled(anyString(), eq(FeatureName.CDS_SERVICE_OVERRIDES_2_0));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgManifestsMetadataSweepingOutput.builder()
                             .serviceIdentifier(SVC_ID)
                             .environmentIdentifier(ENV_ID)
                             .svcManifests(List.of(svcManifest))
                             .manifestsFromOverride(manifestsFromOverride)
                             .serviceDefinitionType(ServiceDefinitionType.NATIVE_HELM)
                             .build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_MANIFESTS_SWEEPING_OUTPUT)));

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();

    assertThatThrownBy(executeMethod::get)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("found duplicate identifiers [id1] in INFRA_GLOBAL_OVERRIDE");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleAsyncResponse() {
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(sweepingOutputService)
        .resolveOptional(
            any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.MANIFESTS)));

    StepResponse stepResponse = step.handleAsyncResponse(buildAmbiance(), new EmptyStepParameters(), new HashMap<>());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleAsyncResponseSkipped() {
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(
            any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.MANIFESTS)));

    StepResponse stepResponse = step.handleAsyncResponse(buildAmbiance(), new EmptyStepParameters(), new HashMap<>());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleAsyncResponseWithResponse() {
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    final Map<String, String> taskIdMapping = ImmutableMap.of("task1", "manifest1");
    final UnresolvedManifestsOutput output =
        UnresolvedManifestsOutput.builder().taskIdMapping(taskIdMapping).manifestsOutcome(manifestsOutcome).build();
    final OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(output).build();
    final Map<String, ResponseData> responses = ImmutableMap.of("task1", mock(ResponseData.class));

    doReturn(optionalSweepingOutput)
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.UNRESOLVED_MANIFESTS)));

    StepResponse stepResponse = step.handleAsyncResponse(buildAmbiance(), new EmptyStepParameters(), responses);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    verify(manifestTaskService).handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleAsyncResponseWithErrorResponse() {
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    final Map<String, String> taskIdMapping = ImmutableMap.of("task1", "manifest1");
    final UnresolvedManifestsOutput output =
        UnresolvedManifestsOutput.builder().taskIdMapping(taskIdMapping).manifestsOutcome(manifestsOutcome).build();
    final OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(output).build();
    final Map<String, ResponseData> responses = ImmutableMap.of("task1", mock(ResponseData.class));
    final StepResponse failedResponse = StepResponse.builder().status(Status.FAILED).build();
    final Exception exception = new InvalidRequestException("Something went wrong");

    doReturn(optionalSweepingOutput)
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.UNRESOLVED_MANIFESTS)));
    doThrow(exception).when(manifestTaskService).handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
    doReturn(failedResponse).when(strategyHelper).handleException(exception);

    StepResponse stepResponse = step.handleAsyncResponse(buildAmbiance(), new EmptyStepParameters(), responses);
    assertThat(stepResponse).isSameAs(failedResponse);
    verify(manifestTaskService).handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
  }

  private ManifestConfigWrapper sampleManifestFile(String identifier, ManifestConfigType type) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder().identifier(identifier).type(type).spec(getSpec(identifier, type)).build())
        .build();
  }

  private ManifestAttributes getSpec(String identifier, ManifestConfigType type) {
    if (ManifestConfigType.HELM_CHART.equals(type)) {
      return HelmChartManifest.builder()
          .identifier(identifier)
          .helmVersion(HelmVersion.V3)
          .chartVersion(ParameterField.createValueField("0.1.0"))
          .store(getStoreConfig())
          .build();
    }
    return K8sManifest.builder().identifier(identifier).store(getStoreConfig()).build();
  }

  @NotNull
  private ParameterField<StoreConfigWrapper> getStoreConfig() {
    return ParameterField.createValueField(
        StoreConfigWrapper.builder()
            .type(StoreConfigType.GIT)
            .spec(GitStore.builder()
                      .folderPath(ParameterField.createValueField("manifests/"))
                      .connectorRef(ParameterField.createValueField("gitconnector"))
                      .branch(ParameterField.createValueField("main"))
                      .paths(ParameterField.createValueField(List.of("path1", "path2")))
                      .build())
            .build());
  }

  private ManifestConfigWrapper sampleManifestHttpHelm(String identifier, ManifestConfigType type) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(type)
                      .spec(HelmChartManifest.builder()
                                .identifier(identifier)
                                .store(ParameterField.createValueField(
                                    StoreConfigWrapper.builder()
                                        .type(StoreConfigType.HTTP)
                                        .spec(HttpStoreConfig.builder()
                                                  .connectorRef(ParameterField.createValueField("helmconnector"))
                                                  .build())
                                        .build()))
                                .build())
                      .build())
        .build();
  }

  private ManifestConfigWrapper sampleHelmChartManifestFile(String identifier, ManifestConfigType type) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(type)
                      .spec(HelmChartManifest.builder()
                                .identifier(identifier)
                                .store(ParameterField.createValueField(
                                    StoreConfigWrapper.builder()
                                        .type(StoreConfigType.GIT)
                                        .spec(GitStore.builder()
                                                  .folderPath(ParameterField.createValueField("manifests/"))
                                                  .connectorRef(ParameterField.createValueField("gitconnector"))
                                                  .branch(ParameterField.createValueField("main"))
                                                  .paths(ParameterField.createValueField(List.of("path1", "path2")))
                                                  .build())
                                        .build()))
                                .build())
                      .build())
        .build();
  }

  private ManifestConfigWrapper sampleHelmRepoOverride(String identifier, String connectorRef) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(ManifestConfigType.HELM_REPO_OVERRIDE)
                      .spec(HelmRepoOverrideManifest.builder()
                                .identifier(identifier)
                                .connectorRef(ParameterField.createValueField(connectorRef))
                                .type("Http")
                                .build())
                      .build())
        .build();
  }

  private ManifestConfigWrapper sampleValuesYamlFile(String identifier) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(ManifestConfigType.VALUES)
                      .spec(ValuesManifest.builder()
                                .identifier(identifier)
                                .store(ParameterField.createValueField(
                                    StoreConfigWrapper.builder()
                                        .type(StoreConfigType.GIT)
                                        .spec(GitStore.builder()
                                                  .folderPath(ParameterField.createValueField("values/"))
                                                  .connectorRef(ParameterField.createValueField("gitconnector"))
                                                  .branch(ParameterField.createValueField("main"))
                                                  .paths(ParameterField.createValueField(
                                                      List.of("values" + identifier + ".yaml")))
                                                  .build())
                                        .build()))
                                .build())
                      .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder()
                   .setRuntimeId(generateUuid())
                   .setSetupId(generateUuid())
                   .setStepType(ManifestsStepV2.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", "ACCOUNT_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}
