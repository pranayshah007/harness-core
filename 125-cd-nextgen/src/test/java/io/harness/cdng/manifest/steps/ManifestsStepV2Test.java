/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_OVERRIDES;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.steps.output.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmRepoOverrideManifest;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.EntityReferenceExtractorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync() {
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

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    verify(expressionResolver, times(1)).updateExpressions(any(Ambiance.class), any());

    ManifestsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file2").getOrder()).isEqualTo(1);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(2);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(any(), any(List.class), any(Boolean.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestList_0() {
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
      step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).contains("Kubernetes deployment support only one manifest of one of types");
      return;
    }

    fail("expected to raise an exception");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestList_1() {
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
      step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);
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
  public void executeSyncConnectorNotFound() {
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
      step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);
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
  public void envLevelGlobalOverride() {
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
    step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue())
        .isEqualTo("overriddenconnector");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void svcLevelOverride() {
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
    step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue()).isEqualTo("svcoverride");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV2)
  @Category(UnitTests.class)
  public void svcAndEnvLevelOverrides() {
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
    step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    verify(expressionResolver).updateExpressions(any(), listArgumentCaptor.capture());

    List<ManifestAttributes> manifestAttributes = listArgumentCaptor.getValue();
    assertThat(manifestAttributes.size()).isEqualTo(1);
    assertThat(manifestAttributes.get(0).getStoreConfig().getConnectorReference().getValue()).isEqualTo("svcoverride");
  }

  private ManifestConfigWrapper sampleManifestFile(String identifier, ManifestConfigType type) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(type)
                      .spec(K8sManifest.builder()
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
