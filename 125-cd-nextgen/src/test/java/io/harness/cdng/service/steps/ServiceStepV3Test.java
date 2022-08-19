package io.harness.cdng.service.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.io.IOException;
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

public class ServiceStepV3Test {
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  private AutoCloseable mocks;
  @InjectMocks private ServiceStepV3 step = new ServiceStepV3();

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
  public void executeSyncServiceRefNotResolved() {
    assertThatExceptionOfType(UnresolvedExpressionsException.class)
        .isThrownBy(()
                        -> step.executeSync(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.<String>builder()
                                                .expression(true)
                                                .expressionValue("<+randomExpression>")
                                                .build())
                                .build(),
                            null, null));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync() {
    final ServiceEntity serviceEntity = testServiceEntity();

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));

    StepResponse stepResponse = step.executeSync(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .build(),
        null, null);

    ArgumentCaptor<ServiceSweepingOutput> captor = ArgumentCaptor.forClass(ServiceSweepingOutput.class);

    verify(sweepingOutputService)
        .consume(any(Ambiance.class), eq(ServiceStepV3.SERVICE_SWEEPING_OUTPUT), captor.capture(), eq("STAGE"));

    ServiceStepOutcome serviceOutcome =
        (ServiceStepOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();
    ServiceSweepingOutput output = captor.getValue();

    assertThat(serviceOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(output.getFinalServiceYaml()).isEqualTo(serviceEntity.getYaml());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncWithServiceInputs() throws IOException {
    final ServiceEntity serviceEntity = testServiceEntityWithInputs();
    String inputYaml = "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "      - manifest:\n"
        + "          identifier: \"m1\"\n"
        + "          type: \"K8sManifest\"\n"
        + "          spec:\n"
        + "            valuesPaths:\n"
        + "               - v1.yaml\n"
        + "               - v2.yaml";

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));

    StepResponse stepResponse = step.executeSync(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .inputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
            .build(),
        null, null);

    ArgumentCaptor<ServiceSweepingOutput> captor = ArgumentCaptor.forClass(ServiceSweepingOutput.class);

    verify(sweepingOutputService)
        .consume(any(Ambiance.class), eq(ServiceStepV3.SERVICE_SWEEPING_OUTPUT), captor.capture(), eq("STAGE"));
    verify(serviceStepsHelper).validateResources(any(Ambiance.class), any(NGServiceConfig.class));

    ServiceStepOutcome serviceOutcome =
        (ServiceStepOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();
    ServiceSweepingOutput output = captor.getValue();
    NGServiceV2InfoConfig serviceConfig =
        YamlUtils.read(output.getFinalServiceYaml(), NGServiceConfig.class).getNgServiceV2InfoConfig();

    assertThat(serviceOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(((K8sManifest) serviceConfig.getServiceDefinition()
                       .getServiceSpec()
                       .getManifests()
                       .get(0)
                       .getManifest()
                       .getSpec())
                   .getValuesPaths()
                   .getValue())
        .containsExactly("v1.yaml", "v2.yaml");
    assertThat(output.getFinalServiceYaml().contains("<+input>")).isFalse();
  }

  private ServiceEntity testServiceEntity() {
    NGServiceV2InfoConfig config = NGServiceV2InfoConfig.builder()
                                       .identifier("service-id")
                                       .name("service-name")
                                       .serviceDefinition(ServiceDefinition.builder()
                                                              .type(ServiceDefinitionType.KUBERNETES)
                                                              .serviceSpec(KubernetesServiceSpec.builder().build())
                                                              .build())
                                       .build();
    String serviceYaml = YamlUtils.write(NGServiceConfig.builder().ngServiceV2InfoConfig(config).build());
    return ServiceEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier(config.getIdentifier())
        .name(config.getName())
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private ServiceEntity testServiceEntityWithInputs() {
    String serviceYaml = "service:\n"
        + "  name: \"service-name\"\n"
        + "  identifier: \"service-id\"\n"
        + "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "      manifests:\n"
        + "      - manifest:\n"
        + "          identifier: \"m1\"\n"
        + "          type: \"K8sManifest\"\n"
        + "          spec:\n"
        + "            store: {}\n"
        + "            valuesPaths: \"<+input>\"";
    return ServiceEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private Ambiance buildAmbiance() {
    List<Level> levels = new ArrayList();
    levels.add(Level.newBuilder()
                   .setRuntimeId(UUIDGenerator.generateUuid())
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(ServiceStepV3.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(UUIDGenerator.generateUuid())
        .putAllSetupAbstractions(
            Map.of("accountId", "ACCOUNT_ID", "projectIdentifier", "PROJECT_ID", "orgIdentifier", "ORG_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .build();
  }
}