package io.harness.cdng.service.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.environment.EnvironmentOutcome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CDExpressionResolver expressionResolver;
  private AutoCloseable mocks;
  @InjectMocks private ServiceStepV3 step = new ServiceStepV3();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // default behaviours
    doReturn(Optional.empty())
        .when(serviceOverrideService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());
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
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.<String>builder()
                                                .expression(true)
                                                .expressionValue("<+randomExpression>")
                                                .build())
                                .build(),
                            null));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSync() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();

    mockService(serviceEntity);

    mockEnv(environment);

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(1);

    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(4))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));

    ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) outputsMap.get(ServiceStepOutcome.class);
    EnvironmentOutcome envOutcome = (EnvironmentOutcome) outputsMap.get(EnvironmentOutcome.class);
    VariablesSweepingOutput variablesSweepingOutput =
        (VariablesSweepingOutput) outputsMap.get(VariablesSweepingOutput.class);

    assertThat(serviceStepOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(serviceStepOutcome.getName()).isEqualTo(serviceEntity.getName());

    assertThat(envOutcome.getIdentifier()).isEqualTo(environment.getIdentifier());
    assertThat(envOutcome.getName()).isEqualTo(environment.getName());
    assertThat(variablesSweepingOutput.keySet()).containsExactly("numbervar1", "secretvar", "numbervar", "stringvar");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testVariableOverrides() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();
    final NGServiceOverridesEntity serviceOverrides =
        testServiceOverrides(environment.getIdentifier(), serviceEntity.getIdentifier());

    mockService(serviceEntity);
    mockEnv(environment);
    mockOverrides(serviceOverrides);

    step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);

    verify(sweepingOutputService, times(4)).consume(any(Ambiance.class), anyString(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));

    VariablesSweepingOutput variablesSweepingOutput =
        (VariablesSweepingOutput) outputsMap.get(VariablesSweepingOutput.class);

    assertThat(variablesSweepingOutput.keySet()).containsExactly("numbervar1", "secretvar", "numbervar", "stringvar");

    assertThat(((ParameterField) variablesSweepingOutput.get("numbervar")).getValue()).isEqualTo(9.0);
    assertThat(variablesSweepingOutput.get("secretvar")).isEqualTo("<+secrets.getValue(\"org.secret\")>");
    assertThat(((ParameterField) variablesSweepingOutput.get("numbervar1")).getValue()).isEqualTo(3.0);
    assertThat(((ParameterField) variablesSweepingOutput.get("stringvar")).getValue()).isEqualTo("envvalue");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncWithServiceInputs() throws IOException {
    final ServiceEntity serviceEntity = testServiceEntityWithInputs();
    final Environment environment = testEnvEntity();
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

    mockService(serviceEntity);
    mockEnv(environment);

    step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .inputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
            .build(),
        null);

    verify(serviceStepsHelper).validateResources(any(Ambiance.class), any(NGServiceConfig.class));

    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(sweepingOutputService, times(4))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));

    ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) outputsMap.get(ServiceStepOutcome.class);

    assertThat(serviceStepOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(serviceStepOutcome.getName()).isEqualTo(serviceEntity.getName());
    ServiceSweepingOutput serviceSweepingOutput = (ServiceSweepingOutput) outputsMap.get(ServiceSweepingOutput.class);
    NGServiceV2InfoConfig serviceConfig =
        YamlUtils.read(serviceSweepingOutput.getFinalServiceYaml(), NGServiceConfig.class).getNgServiceV2InfoConfig();

    assertThat(((K8sManifest) serviceConfig.getServiceDefinition()
                       .getServiceSpec()
                       .getManifests()
                       .get(0)
                       .getManifest()
                       .getSpec())
                   .getValuesPaths()
                   .getValue())
        .containsExactly("v1.yaml", "v2.yaml");
    assertThat(serviceSweepingOutput.getFinalServiceYaml().contains("<+input>")).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponse_0() {
    ServiceEntity service = testServiceEntity();
    Environment environment = testEnvEntity();
    doReturn(ServiceSweepingOutput.builder().finalServiceYaml(service.getYaml()).build())
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.handleChildrenResponse(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(service.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        Map.of("taskid1", StepResponseNotifyData.builder().nodeUuid("nodeuuid").status(Status.SUCCEEDED).build()));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    ServiceStepOutcome outcome = (ServiceStepOutcome) stepResponse.getStepOutcomes().iterator().next().getOutcome();
    assertThat(outcome.getName()).isEqualTo("service-name");
    assertThat(outcome.getIdentifier()).isEqualTo("service-id");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponse_1() {
    ServiceEntity service = testServiceEntity();
    Environment environment = testEnvEntity();
    doReturn(ServiceSweepingOutput.builder().finalServiceYaml(service.getYaml()).build())
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    // outputs from children steps
    doReturn(OptionalSweepingOutput.builder().found(true).output(new ManifestsOutcome()).build())
        .when(sweepingOutputService)
        .resolveOptional(
            any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.MANIFESTS)));

    doReturn(OptionalSweepingOutput.builder().found(true).output(ArtifactsOutcome.builder().build()).build())
        .when(sweepingOutputService)
        .resolveOptional(
            any(Ambiance.class), eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ARTIFACTS)));

    doReturn(OptionalSweepingOutput.builder().found(true).output(new ConfigFilesOutcome()).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class),
            eq(RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.CONFIG_FILES)));

    StepResponse stepResponse = step.handleChildrenResponse(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(service.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        Map.of("taskid1", StepResponseNotifyData.builder().nodeUuid("nodeuuid").status(Status.SUCCEEDED).build()));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(4);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get("manifests")).isInstanceOf(ManifestsOutcome.class);
    assertThat(outcomeMap.get("artifacts")).isInstanceOf(ArtifactsOutcome.class);
    assertThat(outcomeMap.get("configFiles")).isInstanceOf(ConfigFilesOutcome.class);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponse_failure() {
    ServiceEntity service = testServiceEntity();
    Environment environment = testEnvEntity();
    doReturn(ServiceSweepingOutput.builder().finalServiceYaml(service.getYaml()).build())
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class), eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.handleChildrenResponse(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(service.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        Map.of("taskid1", StepResponseNotifyData.builder().nodeUuid("nodeuuid").status(Status.FAILED).build()));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  private ServiceEntity testServiceEntity() {
    final String serviceYaml = "service:\n"
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      variables:\n"
        + "        - name: stringvar\n"
        + "          type: String\n"
        + "          value: stringvalue\n"
        + "        - name: numbervar\n"
        + "          type: Number\n"
        + "          value: 1\n"
        + "        - name: numbervar1\n"
        + "          type: Number\n"
        + "          value: 3\n"
        + "        - name: secretvar\n"
        + "          type: Secret\n"
        + "          value: org.secret\n"
        + "    type: Ssh\n";
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

  private Environment testEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 5";
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("envId")
        .name("developmentEnv")
        .yaml(yaml)
        .build();
  }

  private NGServiceOverridesEntity testServiceOverrides(String envId, String serviceId) {
    String yaml = "serviceOverrides:\n"
        + "  environmentRef: " + envId + "\n"
        + "  serviceRef: " + serviceId + "\n"
        + "  variables:\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 9";
    return NGServiceOverridesEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .environmentRef(envId)
        .serviceRef(serviceId)
        .yaml(yaml)
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

  private void mockService(ServiceEntity serviceEntity) {
    doReturn(Optional.ofNullable(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));
  }

  private void mockEnv(Environment environment) {
    doReturn(Optional.ofNullable(environment))
        .when(environmentService)
        .get(anyString(), anyString(), anyString(), anyString(), eq(false));
  }

  private void mockOverrides(NGServiceOverridesEntity serviceOverrides) {
    doReturn(Optional.ofNullable(serviceOverrides))
        .when(serviceOverrideService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());
  }
}