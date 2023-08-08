/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.gitops.steps.GitOpsEnvOutCome;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.helpers.ServiceOverrideUtilityFacade;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.expression.EngineExpressionService;
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
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
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

public class ServiceStepV3Test extends CategoryTest {
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceStepsHelper serviceStepsHelper;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private CDExpressionResolver expressionResolver;
  @Mock private ServiceStepOverrideHelper serviceStepOverrideHelper;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private NgExpressionHelper ngExpressionHelper;
  @Mock private ServiceCustomSweepingOutputHelper serviceCustomSweepingOutputHelper;

  @Mock private ServiceEntityYamlSchemaHelper serviceEntityYamlSchemaHelper;
  @Mock private EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;

  @Mock private ServiceOverrideUtilityFacade serviceOverrideUtilityFacade;

  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;

  private AutoCloseable mocks;
  @InjectMocks private ServiceStepV3 step = new ServiceStepV3();

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    // default behaviours
    doReturn(Optional.empty())
        .when(serviceOverrideService)
        .get(anyString(), anyString(), anyString(), anyString(), anyString());
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(any(Ambiance.class), any());

    doReturn(AccessCheckResponseDTO.builder().accessControlList(List.of()).build())
        .when(accessControlClient)
        .checkForAccess(any(Principal.class), anyList());

    doNothing()
        .when(serviceCustomSweepingOutputHelper)
        .saveAdditionalServiceFieldsToSweepingOutput(any(NGServiceConfig.class), any(Ambiance.class));
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
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.<String>builder()
                                                .expression(true)
                                                .expressionValue("<+randomExpression>")
                                                .build())
                                .envRef(ParameterField.createValueField("envRef"))
                                .build(),
                            null))
        .withMessageContaining("Expression (<+randomExpression>) given for service ref could not be resolved. ");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeSyncServiceTypeMismatch() {
    // SSH Service
    final ServiceEntity serviceEntity = testServiceEntity();
    mockService(serviceEntity);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.createValueField("svcid"))
                                .deploymentType(ServiceDefinitionType.ECS)
                                .build(),
                            null));
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteSyncServiceWithNoServiceDef() {
    final ServiceEntity serviceEntity = testServiceEntityWithNoServiceDef();
    final Environment environment = testEnvEntity();
    mockService(serviceEntity);
    mockEnv(environment);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                                .envRef(ParameterField.createValueField(environment.getIdentifier()))
                                .childrenNodeIds(new ArrayList<>())
                                .build(),
                            null))
        .withMessageContaining(String.format("Unable to read yaml for service [Name: %s, Identifier: %s]",
            serviceEntity.getName(), serviceEntity.getIdentifier()));
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateParametersForEnvRef() {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.obtainChildren(buildAmbiance(),
                    ServiceStepV3Parameters.builder()
                        .serviceRef(ParameterField.createExpressionField(true, "<+env.variables.vara>", null, true))
                        .envRef(ParameterField.createValueField("envId"))
                        .childrenNodeIds(new ArrayList<>())
                        .build(),
                    null))
        .withMessageContaining(
            "Expression (<+env.variables.vara>) given for service ref could not be resolved. [Hint]: environment variables expression should not be used as service ref.");

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.obtainChildren(buildAmbiance(),
                    ServiceStepV3Parameters.builder()
                        .serviceRef(ParameterField.createValueField("serviceRef"))
                        .envRef(ParameterField.createExpressionField(true, "<+serviceVariables.vara>", null, true))
                        .childrenNodeIds(new ArrayList<>())
                        .build(),
                    null))
        .withMessageContaining(
            "Expression (<+serviceVariables.vara>) given for environment ref could not be resolved. [Hint]: service variables expression should not be used as environment ref.");

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(
            ()
                -> step.obtainChildren(buildAmbiance(),
                    ServiceStepV3Parameters.builder()
                        .serviceRef(ParameterField.createValueField("serviceRef"))
                        .envRef(ParameterField.createValueField("envId"))
                        .envGroupRef(ParameterField.createExpressionField(true, "<+serviceVariables.vara>", null, true))
                        .childrenNodeIds(new ArrayList<>())
                        .build(),
                    null))
        .withMessageContaining(
            "Expression (<+serviceVariables.vara>) given for environment group ref could not be resolved. [Hint]: service variables expression should not be used as environment group ref.");

    assertThatExceptionOfType(UnresolvedExpressionsException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.createValueField("serviceRef"))
                                .envRefs(Arrays.asList(
                                    ParameterField.createExpressionField(true, "<+serviceVariables.vara>", null, true),
                                    ParameterField.createExpressionField(true, "<+serviceVariables.varb>", null, true)))
                                .childrenNodeIds(new ArrayList<>())
                                .build(),
                            null))
        .withMessageContaining(
            "Unresolved expressions: <+serviceVariables.vara>, <+serviceVariables.varb>. Expression (<+serviceVariables.vara>, <+serviceVariables.varb>) given for environment refs could not be resolved. [Hint]: service variables expression should not be used as environment refs.");
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

    assertThat(response.getLogKeysCount()).isEqualTo(2);

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
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeProject() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(false).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());
    mockService(serviceEntity);
    mockEnv(environment);

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("envId"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeOrg() {
    final ServiceEntity serviceEntity = testOrgServiceEntity();
    final Environment environment = testOrgEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(false).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());

    mockService(serviceEntity);
    mockEnv(environment);

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("org.envId"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("org.service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }

  @Test
  @Owner(developers = OwnerRule.RISHABH)
  @Category(UnitTests.class)
  public void executeSyncWithFreezeAccount() {
    final ServiceEntity serviceEntity = testAccountServiceEntity();
    final Environment environment = testAccountEnvEntity();

    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(false).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());
    mockService(serviceEntity);
    mockEnv(environment);

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(freezeEvaluateService, times(1)).getActiveManualFreezeEntities(any(), any(), any(), captor.capture());

    Map<FreezeEntityType, List<String>> entityMap = captor.getValue();
    assertThat(entityMap.size()).isEqualTo(6);
    assertThat(entityMap.get(FreezeEntityType.ENVIRONMENT)).isEqualTo(Lists.newArrayList("account.envId"));
    assertThat(entityMap.get(FreezeEntityType.SERVICE)).isEqualTo(Lists.newArrayList("account.service-id"));
    assertThat(entityMap.get(FreezeEntityType.ORG)).isEqualTo(Lists.newArrayList("ORG_ID"));
    assertThat(entityMap.get(FreezeEntityType.PROJECT)).isEqualTo(Lists.newArrayList("PROJECT_ID"));
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeWithOldEnv() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();

    mockService(serviceEntity);

    // old env without yaml
    environment.setYaml(null);
    mockEnv(environment);

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);
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
        + "      artifacts:\n"
        + "        primary:\n"
        + "          spec:\n"
        + "            tag: develop-1\n"
        + "          type: DockerRegistry\n"
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

    verify(serviceStepsHelper)
        .checkForVariablesAccessOrThrow(any(Ambiance.class), any(NGServiceConfig.class), anyString());

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

    KubernetesServiceSpec spec = (KubernetesServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec();
    assertThat(((K8sManifest) spec.getManifests().get(0).getManifest().getSpec()).getValuesPaths().getValue())
        .containsExactly("v1.yaml", "v2.yaml");
    assertThat(((DockerHubArtifactConfig) spec.getArtifacts().getPrimary().getSpec()).getTag().getValue())
        .isEqualTo("develop-1");
    assertThat(serviceSweepingOutput.getFinalServiceYaml().contains("<+input>")).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testMergeServiceInputsInputValidation() {
    final ServiceEntity serviceEntity = testServiceEntityWithInputs();
    final Environment environment = testEnvEntity();
    String inputYaml = "  serviceDefinition:\n"
        + "    type: \"Kubernetes\"\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          spec:\n"
        + "            tag: xyz-1\n"
        + "          type: DockerRegistry\n"
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

    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                                .envRef(ParameterField.createValueField(environment.getIdentifier()))
                                .inputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
                                .childrenNodeIds(new ArrayList<>())
                                .build(),
                            null))
        .withMessageContaining(
            "The value provided for [service.serviceDefinition.spec.artifacts.primary.spec.tag: xyz-1] does not match the required regex pattern");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testMergeEnvInputsValidation() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntityWithInputs();

    String inputYaml = " identifier: envId\n"
        + " type: Production\n"
        + " variables:\n"
        + "   - name: numbervar\n"
        + "     type: Number\n"
        + "     value: 7";

    mockService(serviceEntity);
    mockEnv(environment);

    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> step.obtainChildren(buildAmbiance(),
                            ServiceStepV3Parameters.builder()
                                .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                                .envRef(ParameterField.createValueField(environment.getIdentifier()))
                                .envInputs(ParameterField.createValueField(YamlUtils.read(inputYaml, Map.class)))
                                .childrenNodeIds(new ArrayList<>())
                                .build(),
                            null))
        .withMessageContaining(
            "The values provided for environment.variables.numbervar: [\\'7\\'] do not match any of the allowed values [\\'5\\', \\'6\\']");
  }
  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  public void executeWithEnvironments() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();
    final Environment environment2 = testEnvEntity2();

    mockService(serviceEntity);

    List<ParameterField<String>> envRefs = Arrays.asList(ParameterField.createValueField(environment.getIdentifier()),
        ParameterField.createValueField(environment2.getIdentifier()));
    doReturn(Arrays.asList(environment, environment2))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfRefs(anyString(), anyString(), anyString(), anyList());

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRefs(envRefs)
            .gitOpsMultiSvcEnvEnabled(ParameterField.createValueField(true))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(4))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));

    ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) outputsMap.get(ServiceStepOutcome.class);
    VariablesSweepingOutput variablesSweepingOutput =
        (VariablesSweepingOutput) outputsMap.get(VariablesSweepingOutput.class);

    GitOpsEnvOutCome gitOpsEnvOutCome = (GitOpsEnvOutCome) outputsMap.get(GitOpsEnvOutCome.class);
    assertThat(serviceStepOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(serviceStepOutcome.getName()).isEqualTo(serviceEntity.getName());

    assertThat(gitOpsEnvOutCome).isNotNull();

    assertThat(variablesSweepingOutput.keySet()).containsExactly("numbervar1", "secretvar", "numbervar", "stringvar");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteWithEnvironmentV2_SecretVariables() throws IOException {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();
    mockService(serviceEntity);
    mockEnv(environment);
    doReturn(true).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overridesConfigsTestData =
        getOverridesConfigsV2MapTestData(serviceEntity, environment);
    doReturn(overridesConfigsTestData)
        .when(serviceOverrideUtilityFacade)
        .getMergedServiceOverrideConfigs(
            anyString(), anyString(), anyString(), any(ServiceStepV3Parameters.class), any(Environment.class), any());

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .infraId(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);
    ArgumentCaptor<List> secretVariablesCaptor = ArgumentCaptor.forClass(List.class);
    verify(serviceStepsHelper).checkForAccessOrThrow(any(Ambiance.class), secretVariablesCaptor.capture());
    List<NGVariable> variables = secretVariablesCaptor.getValue();
    assertThat(variables).hasSize(2);
    assertThat(variables.stream().map(NGVariable::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("envSecretVar", "envSecretVar");
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteWithEnvironmentV2_EnvironmentOutcome() throws IOException {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();
    mockService(serviceEntity);
    mockEnv(environment);
    doReturn(true).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overridesConfigsTestData =
        getOverridesConfigsV2MapTestData(serviceEntity, environment);
    doReturn(overridesConfigsTestData)
        .when(serviceOverrideUtilityFacade)
        .getMergedServiceOverrideConfigs(
            anyString(), anyString(), anyString(), any(ServiceStepV3Parameters.class), any(Environment.class), any());

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .infraId(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);
    ArgumentCaptor<EnvironmentOutcome> envOutcomeCaptor = ArgumentCaptor.forClass(EnvironmentOutcome.class);
    verify(sweepingOutputService).consume(any(Ambiance.class), anyString(), envOutcomeCaptor.capture(), anyString());
    EnvironmentOutcome envOutcome = envOutcomeCaptor.getValue();
    Map<String, Object> variables = envOutcome.getVariables();
    assertThat(variables.keySet())
        .containsExactlyInAnyOrder(
            "envServiceStringVar", "envStringVar", "infraStringVar", "envSecretVar", "numbervar1", "stringvar");
    assertThat(variables).isNotEmpty();
    assertThat(variables.values()
                   .stream()
                   .filter(value -> value instanceof ParameterField)
                   .map(value -> (ParameterField) value)
                   .map(ParameterField::getValue)
                   .filter(fieldValue -> fieldValue instanceof String)
                   .map(fieldValue -> (String) fieldValue)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            "envServiceStringVal", "infraOverrideStringVal", "infraStringVal", "stringVarFromEnv");
    assertThat(variables.get("envSecretVar")).isEqualTo("<+secrets.getValue(\"secretFromInfraService\")>");
    assertThat(((ParameterField) variables.get("numbervar1")).getValue()).isEqualTo(123D);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteWithEnvironmentV2_ServiceVariables() throws IOException {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();
    mockService(serviceEntity);
    mockEnv(environment);
    doReturn(true).when(overrideV2ValidationHelper).isOverridesV2Enabled(any(), any(), any());
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(ngSettingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overridesConfigsTestData =
        getOverridesConfigsV2MapTestData(serviceEntity, environment);
    doReturn(overridesConfigsTestData)
        .when(serviceOverrideUtilityFacade)
        .getMergedServiceOverrideConfigs(
            anyString(), anyString(), anyString(), any(ServiceStepV3Parameters.class), any(Environment.class), any());

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .infraId(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<VariablesSweepingOutput> varOutputCaptor = ArgumentCaptor.forClass(VariablesSweepingOutput.class);
    verify(sweepingOutputService).consume(any(Ambiance.class), anyString(), varOutputCaptor.capture(), anyString());
    VariablesSweepingOutput varOutput = varOutputCaptor.getValue();

    assertThat(varOutput.keySet())
        .containsExactlyInAnyOrder("envServiceStringVar", "numbervar", "envStringVar", "infraStringVar", "envSecretVar",
            "numbervar1", "secretvar", "stringvar");

    assertThat(varOutput.values()
                   .stream()
                   .filter(value -> value instanceof ParameterField)
                   .map(value -> (ParameterField) value)
                   .map(ParameterField::getValue)
                   .filter(fieldValue -> fieldValue instanceof String)
                   .map(fieldValue -> (String) fieldValue)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            "envServiceStringVal", "infraOverrideStringVal", "infraStringVal", "stringVarFromEnv");

    assertThat(varOutput.get("envSecretVar")).isEqualTo("<+secrets.getValue(\"secretFromInfraService\")>");
    assertThat(((ParameterField) varOutput.get("numbervar1")).getValue()).isEqualTo(123D);
    assertThat(varOutput.get("secretvar")).isEqualTo("<+secrets.getValue(\"org.secret\")>");
    assertThat(((ParameterField) varOutput.get("numbervar")).getValue()).isEqualTo(1D);
  }

  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  public void executeWithEnvironmentsWithEnvVariables() {
    final ServiceEntity serviceEntity = testServiceEntity();
    final Environment environment = testEnvEntity();

    mockService(serviceEntity);

    List<ParameterField<String>> envRefs = Arrays.asList(ParameterField.createValueField(environment.getIdentifier()));
    doReturn(Arrays.asList(environment))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfRefs(anyString(), anyString(), anyString(), anyList());

    Map<String, ParameterField<Map<String, Object>>> mergedEnvironmentInputs = new HashMap<>();
    mergedEnvironmentInputs.put("envId", ParameterField.createValueField(Map.of("h1", "k1")));

    ChildrenExecutableResponse response = step.obtainChildren(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
            .envRefs(envRefs)
            .envToEnvInputs(mergedEnvironmentInputs)
            .gitOpsMultiSvcEnvEnabled(ParameterField.createValueField(true))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        null);

    assertThat(response.getLogKeysCount()).isEqualTo(2);

    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(4))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));

    ServiceStepOutcome serviceStepOutcome = (ServiceStepOutcome) outputsMap.get(ServiceStepOutcome.class);
    VariablesSweepingOutput variablesSweepingOutput =
        (VariablesSweepingOutput) outputsMap.get(VariablesSweepingOutput.class);

    GitOpsEnvOutCome gitOpsEnvOutCome = (GitOpsEnvOutCome) outputsMap.get(GitOpsEnvOutCome.class);
    assertThat(serviceStepOutcome.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(serviceStepOutcome.getName()).isEqualTo(serviceEntity.getName());

    assertThat(gitOpsEnvOutCome).isNotNull();
    assertThat(gitOpsEnvOutCome.getEnvToEnvVariables()).isNotEmpty();
    assertThat(gitOpsEnvOutCome.getEnvToEnvVariables().get(environment.getIdentifier()).size()).isEqualTo(2);

    assertThat(variablesSweepingOutput.keySet()).containsExactly("numbervar1", "secretvar", "numbervar", "stringvar");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testHandleResponse_0() {
    ServiceEntity service = testServiceEntity();
    Environment environment = testEnvEntity();
    doReturn(ServiceSweepingOutput.builder().finalServiceYaml(service.getYaml()).build())
        .when(sweepingOutputService)
        .resolve(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT)));

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
        .resolve(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT)));

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
        .resolve(any(Ambiance.class),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_SWEEPING_OUTPUT)));

    StepResponse stepResponse = step.handleChildrenResponse(buildAmbiance(),
        ServiceStepV3Parameters.builder()
            .serviceRef(ParameterField.createValueField(service.getIdentifier()))
            .envRef(ParameterField.createValueField(environment.getIdentifier()))
            .childrenNodeIds(new ArrayList<>())
            .build(),
        Map.of("taskid1", StepResponseNotifyData.builder().nodeUuid("nodeuuid").status(Status.FAILED).build()));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePart() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(false);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse = step.executeFreezePart(buildAmbiance(), entityMap);
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(1))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());

    List<ExecutionSweepingOutput> allValues = captor.getAllValues();

    Map<Class, ExecutionSweepingOutput> outputsMap =
        allValues.stream().collect(Collectors.toMap(ExecutionSweepingOutput::getClass, a -> a));
    FreezeOutcome freezeOutcome = (FreezeOutcome) outputsMap.get(FreezeOutcome.class);
    assertThat(freezeOutcome.isFrozen()).isEqualTo(true);
    assertThat(freezeOutcome.getGlobalFreezeConfigs()).isEqualTo(freezeSummaryResponseDTOList);
    assertThat(childrenExecutableResponse.getChildrenCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePartWithEmptyFreezeList() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Collections.emptyList();
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(false);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse = step.executeFreezePart(buildAmbiance(), entityMap);
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(0))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());
    assertThat(childrenExecutableResponse).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testExecuteFreezePartIfOverrideFreeze() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(true);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    ChildrenExecutableResponse childrenExecutableResponse = step.executeFreezePart(buildAmbiance(), entityMap);
    assertThat(childrenExecutableResponse).isEqualTo(null);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testExecuteFreezePartIfFreezeNotActive() {
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(new LinkedList<>())
        .when(freezeEvaluateService)
        .anyGlobalFreezeActive(anyString(), anyString(), anyString());
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();

    step.executeFreezePart(buildAmbiance(), entityMap);
    ArgumentCaptor<ExecutionSweepingOutput> captor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    verify(sweepingOutputService, times(0))
        .consume(any(Ambiance.class), stringCaptor.capture(), captor.capture(), anyString());
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

  private ServiceEntity testOrgServiceEntity() {
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
        + "    type: Ssh\n";
    return ServiceEntity.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private ServiceEntity testAccountServiceEntity() {
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
        + "    type: Ssh\n";
    return ServiceEntity.builder()
        .accountId("accountId")
        .identifier("service-id")
        .name("service-name")
        .type(ServiceDefinitionType.KUBERNETES)
        .yaml(serviceYaml)
        .build();
  }

  private ServiceEntity testServiceEntityWithNoServiceDef() {
    final String serviceYaml = "service:\n"
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec: {}\n";
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
        + "  name: service-name\n"
        + "  identifier: service-id\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          spec:\n"
        + "            connectorRef: account.docker\n"
        + "            imagePath: library/nginx\n"
        + "            tag: <+input>.regex(develop.*)\n"
        + "          type: DockerRegistry\n"
        + "      manifests:\n"
        + "      - manifest:\n"
        + "          identifier: m1\n"
        + "          type: K8sManifest\n"
        + "          spec:\n"
        + "            store: {}\n"
        + "            valuesPaths: <+input>";
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
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testOrgEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  orgIdentifier: orgId\n"
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
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testAccountEnvEntity() {
    String yaml = "environment:\n"
        + "  name: developmentEnv\n"
        + "  identifier: envId\n"
        + "  type: Production\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n"
        + "    - name: numbervar\n"
        + "      type: Number\n"
        + "      value: 5";
    return Environment.builder()
        .accountId("accountId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testEnvEntityWithInputs() {
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
        + "      value: <+input>.allowedValues(5,6)";
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("envId")
        .name("developmentEnv")
        .type(EnvironmentType.Production)
        .yaml(yaml)
        .build();
  }

  private Environment testEnvEntity2() {
    String yaml = "environment:\n"
        + "  name: developmentEnv2\n"
        + "  identifier: envId2\n"
        + "  type: Production\n"
        + "  orgIdentifier: orgId\n"
        + "  projectIdentifier: projectId\n"
        + "  variables:\n"
        + "    - name: stringvar\n"
        + "      type: String\n"
        + "      value: envvalue\n";
    return Environment.builder()
        .accountId("accountId")
        .orgIdentifier("orgId")
        .projectIdentifier("projectId")
        .identifier("envId2")
        .name("developmentEnv2")
        .type(EnvironmentType.Production)
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
                   .setStepType(ServiceStepV3Constants.STEP_TYPE)
                   .build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(UUIDGenerator.generateUuid())
        .putAllSetupAbstractions(Map.of("accountId", "ACCOUNT_ID", "projectIdentifier", "PROJECT_ID", "orgIdentifier",
            "ORG_ID", "pipelineId", "PIPELINE_ID"))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234L)
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("pipelineId")
                         .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                               .setPrincipal("prinicipal")
                                               .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                               .build())
                         .build())
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

  private FreezeSummaryResponseDTO createGlobalFreezeResponse() {
    FreezeConfig freezeConfig = FreezeConfig.builder()
                                    .freezeInfoConfig(FreezeInfoConfig.builder()
                                                          .identifier("_GLOBAL_")
                                                          .name("Global Freeze")
                                                          .status(FreezeStatus.DISABLED)
                                                          .build())
                                    .build();
    String yaml = NGFreezeDtoMapper.toYaml(freezeConfig);
    FreezeConfigEntity freezeConfigEntity = NGFreezeDtoMapper.toFreezeConfigEntity(
        "accountId", "orgIdentifier", "projectIdentifier", yaml, FreezeType.GLOBAL);
    return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
  }

  private static EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> getOverridesConfigsV2MapTestData(
      ServiceEntity serviceEntity, Environment environment) {
    EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> mergedOverrideV2Configs =
        new EnumMap<>(ServiceOverridesType.class);
    mergedOverrideV2Configs.put(ServiceOverridesType.ENV_GLOBAL_OVERRIDE,
        NGServiceOverrideConfigV2.builder()
            .identifier("id0")
            .environmentRef(environment.getIdentifier())
            .serviceRef(serviceEntity.getIdentifier())
            .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
            .spec(ServiceOverridesSpec.builder()
                      .variables(List.of(StringNGVariable.builder()
                                             .name("stringvar")
                                             .value(ParameterField.createValueField("stringVarFromEnv"))
                                             .build(),
                          SecretNGVariable.builder()
                              .name("envSecretVar")
                              .value(ParameterField.createValueField(
                                  SecretRefData.builder()
                                      .scope(Scope.PROJECT)
                                      .identifier("secretFromEnv")
                                      .decryptedValue("secretValueFromEnv".toCharArray())
                                      .build()))
                              .build(),
                          StringNGVariable.builder()
                              .name("envStringVar")
                              .value(ParameterField.createValueField("envStringVal"))
                              .build()))
                      .build())
            .build());

    mergedOverrideV2Configs.put(ServiceOverridesType.ENV_SERVICE_OVERRIDE,
        NGServiceOverrideConfigV2.builder()
            .identifier("id1")
            .environmentRef(environment.getIdentifier())
            .serviceRef(serviceEntity.getIdentifier())
            .type(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
            .spec(ServiceOverridesSpec.builder()
                      .variables(List.of(NumberNGVariable.builder()
                                             .name("numbervar1")
                                             .value(ParameterField.createValueField(123D))
                                             .build(),
                          StringNGVariable.builder()
                              .name("envServiceStringVar")
                              .value(ParameterField.createValueField("envServiceStringVal"))
                              .build()))
                      .build())
            .build());

    mergedOverrideV2Configs.put(ServiceOverridesType.INFRA_SERVICE_OVERRIDE,
        NGServiceOverrideConfigV2.builder()
            .identifier("id2")
            .environmentRef(environment.getIdentifier())
            .serviceRef(serviceEntity.getIdentifier())
            .type(ServiceOverridesType.INFRA_SERVICE_OVERRIDE)
            .spec(ServiceOverridesSpec.builder()
                      .variables(List.of(SecretNGVariable.builder()
                                             .name("envSecretVar")
                                             .value(ParameterField.createValueField(
                                                 SecretRefData.builder()
                                                     .scope(Scope.PROJECT)
                                                     .identifier("secretFromInfraService")
                                                     .decryptedValue("secretValueFromInfra".toCharArray())
                                                     .build()))
                                             .build(),
                          StringNGVariable.builder()
                              .name("envStringVar")
                              .value(ParameterField.createValueField("infraOverrideStringVal"))
                              .build(),
                          StringNGVariable.builder()
                              .name("infraStringVar")
                              .value(ParameterField.createValueField("infraStringVal"))
                              .build()))
                      .build())
            .build());

    return mergedOverrideV2Configs;
  }
}
