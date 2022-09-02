package io.harness.cdng.manifest.steps;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ManifestsStepV2Test {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ConnectorService connectorService;
  @Mock private CDStepHelper cdStepHelper;
  @InjectMocks private ManifestsStepV2 step = new ManifestsStepV2();
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    doReturn(Optional.of(ConnectorResponseDTO.builder()
                             .entityValidityDetails(EntityValidityDetails.builder().valid(true).build())
                             .build()))
        .when(connectorService)
        .get(anyString(), anyString(), anyString(), anyString());
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

    doReturn(getServiceConfig(List.of(file1, file2, file3)))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    ArgumentCaptor<ManifestsOutcome> captor = ArgumentCaptor.forClass(ManifestsOutcome.class);
    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), eq("manifests"), captor.capture(), eq("STAGE"));
    ManifestsOutcome outcome = captor.getValue();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file2").getOrder()).isEqualTo(1);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncFailWithInvalidManifestList_0() {
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    // 2 k8s manifests are not allowed
    ManifestConfigWrapper file2 = sampleManifestFile("file2", ManifestConfigType.K8_MANIFEST);
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    doReturn(getServiceConfig(List.of(file1, file2, file3)))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

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
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.HELM_CHART);
    // 2 k8s manifests are not allowed
    ManifestConfigWrapper file2 = sampleManifestFile("file2", ManifestConfigType.HELM_CHART);
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    doReturn(getServiceConfig(List.of(file1, file2, file3)))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

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
  public void executeSyncConnectorNotFound() {
    doReturn(Optional.empty()).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
    ManifestConfigWrapper file1 = sampleManifestFile("file1", ManifestConfigType.K8_MANIFEST);
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    doReturn(getServiceConfig(List.of(file1, file2, file3)))
        .when(cdStepHelper)
        .fetchServiceConfigFromSweepingOutput(any(Ambiance.class));

    try {
      step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage()).contains("gitconnector");
      assertThat(ex.getMessage()).contains("not found");
      return;
    }

    fail("expected to raise an exception");
  }

  private Optional<NGServiceV2InfoConfig> getServiceConfig(List<ManifestConfigWrapper> manifestConfigWrappers) {
    NGServiceV2InfoConfig config =
        NGServiceV2InfoConfig.builder()
            .identifier("service-id")
            .name("service-name")
            .serviceDefinition(
                ServiceDefinition.builder()
                    .type(ServiceDefinitionType.KUBERNETES)
                    .serviceSpec(KubernetesServiceSpec.builder().manifests(manifestConfigWrappers).build())
                    .build())
            .build();
    return Optional.of(config);
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