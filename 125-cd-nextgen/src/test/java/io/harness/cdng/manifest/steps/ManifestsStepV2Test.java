package io.harness.cdng.manifest.steps;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
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
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ManifestsStepV2Test {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private ConnectorService connectorService;
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
    ManifestConfigWrapper file1 = sampleManifestFile("file1");
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    doReturn(getServiceSweepingOutput(List.of(file1, file2, file3)))
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    ManifestsOutcome outcome = (ManifestsOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file2").getOrder()).isEqualTo(2);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncConnectorNotFound() {
    doReturn(Optional.empty()).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
    ManifestConfigWrapper file1 = sampleManifestFile("file1");
    ManifestConfigWrapper file2 = sampleValuesYamlFile("file2");
    ManifestConfigWrapper file3 = sampleValuesYamlFile("file3");

    doReturn(getServiceSweepingOutput(List.of(file1, file2, file3)))
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getFailureDataCount()).isEqualTo(1);
  }

  private ServiceSweepingOutput getServiceSweepingOutput(List<ManifestConfigWrapper> manifestConfigWrappers) {
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
    String serviceYaml = YamlUtils.write(NGServiceConfig.builder().ngServiceV2InfoConfig(config).build());
    return ServiceSweepingOutput.builder().finalServiceYaml(serviceYaml).build();
  }

  private ManifestConfigWrapper sampleManifestFile(String identifier) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier(identifier)
                      .type(ManifestConfigType.K8_MANIFEST)
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