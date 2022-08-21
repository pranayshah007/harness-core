package io.harness.cdng.configfile.steps;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceStepsHelper;
import io.harness.cdng.service.steps.ServiceSweepingOutput;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.logstreaming.NGLogCallback;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConfigFilesStepV2Test {
  @Mock private NGLogCallback mockNgLogCallback;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private ConfigFileStepUtils configFileStepUtils;
  @Mock private ExecutionSweepingOutputService mockSweepingOutputService;

  @InjectMocks private final ConfigFilesStepV2 step = new ConfigFilesStepV2();
  private AutoCloseable mocks;
  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
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
    ConfigFileWrapper file1 = sampleConfigFile("file1");
    ConfigFileWrapper file2 = sampleConfigFile("file2");
    ConfigFileWrapper file3 = sampleConfigFile("file3");

    doReturn(getServiceSweepingOutput(List.of(file1, file2, file3)))
        .when(mockSweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.executeSync(buildAmbiance(), new EmptyStepParameters(), null, null);

    ConfigFilesOutcome outcome = (ConfigFilesOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(outcome.keySet()).containsExactlyInAnyOrder("file1", "file2", "file3");
    assertThat(outcome.get("file1").getOrder()).isEqualTo(1);
    assertThat(outcome.get("file2").getOrder()).isEqualTo(2);
    assertThat(outcome.get("file3").getOrder()).isEqualTo(3);
  }

  private ServiceSweepingOutput getServiceSweepingOutput(List<ConfigFileWrapper> configFileWrapperList) {
    NGServiceV2InfoConfig config =
        NGServiceV2InfoConfig.builder()
            .identifier("service-id")
            .name("service-name")
            .serviceDefinition(
                ServiceDefinition.builder()
                    .type(ServiceDefinitionType.KUBERNETES)
                    .serviceSpec(KubernetesServiceSpec.builder().configFiles(configFileWrapperList).build())
                    .build())
            .build();
    String serviceYaml = YamlUtils.write(NGServiceConfig.builder().ngServiceV2InfoConfig(config).build());
    return ServiceSweepingOutput.builder().finalServiceYaml(serviceYaml).build();
  }

  private ConfigFileWrapper sampleConfigFile(String identifier) {
    return ConfigFileWrapper.builder()
        .configFile(ConfigFile.builder()
                        .identifier(identifier)
                        .spec(ConfigFileAttributes.builder()
                                  .store(ParameterField.createValueField(
                                      StoreConfigWrapper.builder()
                                          .spec(GitStore.builder()
                                                    .connectorRef(ParameterField.createValueField("account.connector"))
                                                    .build())
                                          .type(StoreConfigType.GIT)
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
                   .setStepType(ConfigFilesStepV2.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .build();
  }
}