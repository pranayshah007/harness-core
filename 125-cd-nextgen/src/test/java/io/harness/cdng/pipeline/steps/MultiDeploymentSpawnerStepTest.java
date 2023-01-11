/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Maps;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MultiDeploymentSpawnerStepTest extends CategoryTest {
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @Mock private EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Mock private AccessControlClient accessControlClient;
  @InjectMocks private final MultiDeploymentSpawnerStep multiDeploymentSpawnerStep = new MultiDeploymentSpawnerStep();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponseInternal() {
    StepResponseNotifyData stepResponseNotifyData = StepResponseNotifyData.builder().status(Status.SUCCEEDED).build();
    assertThat(
        multiDeploymentSpawnerStep
            .handleChildrenResponseInternal(prepareAmbience(), null, Maps.newHashMap("a", stepResponseNotifyData))
            .getStatus())
        .isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(multiDeploymentSpawnerStep.getStepParametersClass()).isEqualTo(MultiDeploymentStepParameters.class);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidateResourcesWithEnvironmentGroup() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account1");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions)
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder()
                                                                   .setPrincipalType(PrincipalType.USER)
                                                                   .setPrincipal("Principal")
                                                                   .build())
                                             .build())
                            .build();

    MultiDeploymentStepParameters multiDeploymentStepParameters =
        MultiDeploymentStepParameters.builder()
            .childNodeId("test")
            .environmentGroup(
                EnvironmentGroupYaml.builder().envGroupRef(ParameterField.createValueField("org.envGroup1")).build())
            .build();
    multiDeploymentSpawnerStep.validateResources(ambiance, multiDeploymentStepParameters);

    ArgumentCaptor<ResourceScope> resourceScopeCaptor = ArgumentCaptor.forClass(ResourceScope.class);
    ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(
            any(Principal.class), resourceScopeCaptor.capture(), resourceCaptor.capture(), anyString(), anyString());

    ResourceScope resourceScope = resourceScopeCaptor.getValue();
    assertThat(resourceScope).isNotNull();
    assertThat(resourceScope.getAccountIdentifier()).isEqualTo("account1");
    assertThat(resourceScope.getOrgIdentifier()).isEqualTo("org1");
    assertThat(resourceScope.getProjectIdentifier()).isBlank();

    Resource resource = resourceCaptor.getValue();
    assertThat(resource).isNotNull();
    assertThat(resource.getResourceIdentifier()).isEqualTo("envGroup1");
    assertThat(resource.getResourceType()).isEqualTo(NGResourceType.ENVIRONMENT_GROUP);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbacWithOnlyServices() {
    ServiceYamlV2 serviceYamlV2 = ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("svc1")).build();
    List<ServiceYamlV2> serviceYamlV2s = new ArrayList<>();
    serviceYamlV2s.add(serviceYamlV2);
    MultiDeploymentStepParameters multiDeploymentStepParameters =
        MultiDeploymentStepParameters.builder()
            .childNodeId("test")
            .services(ServicesYaml.builder().values(ParameterField.createValueField(serviceYamlV2s)).build())
            .build();

    assertThat(
        multiDeploymentSpawnerStep.obtainChildrenAfterRbac(prepareAmbience(), multiDeploymentStepParameters, null))
        .isEqualTo(
            ChildrenExecutableResponse.newBuilder()
                .addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId("test").setStrategyMetadata(
                    StrategyMetadata.newBuilder()
                        .setTotalIterations(1)
                        .setMatrixMetadata(MatrixMetadata.newBuilder()
                                               .setSubType(MultiDeploymentSpawnerUtils.MULTI_SERVICE_DEPLOYMENT)
                                               .putAllMatrixValues(Maps.newHashMap("serviceRef", "svc1"))
                                               .build())
                        .build()))
                .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainChildrenAfterRbacWithOnlyEnvironments() {
    EnvironmentYamlV2 environmentYamlV2 = EnvironmentYamlV2.builder()
                                              .environmentRef(ParameterField.createValueField("env1"))
                                              .infrastructureDefinition(ParameterField.createValueField(
                                                  InfraStructureDefinitionYaml.builder()
                                                      .identifier(ParameterField.createValueField("identifier"))
                                                      .build()))
                                              .build();
    List<EnvironmentYamlV2> environmentYamlV2s = new ArrayList<>();
    environmentYamlV2s.add(environmentYamlV2);
    MultiDeploymentStepParameters multiDeploymentStepParameters =
        MultiDeploymentStepParameters.builder()
            .childNodeId("test")
            .environments(
                EnvironmentsYaml.builder().values(ParameterField.createValueField(environmentYamlV2s)).build())
            .build();

    Map<String, String> map = new HashMap<>();
    map.put("environmentRef", "env1");
    map.put("identifier", "identifier");
    assertThat(
        multiDeploymentSpawnerStep.obtainChildrenAfterRbac(prepareAmbience(), multiDeploymentStepParameters, null))
        .isEqualTo(ChildrenExecutableResponse.newBuilder()
                       .addChildren(ChildrenExecutableResponse.Child.newBuilder()
                                        .setChildNodeId("test")
                                        .setStrategyMetadata(
                                            StrategyMetadata.newBuilder()
                                                .setTotalIterations(1)
                                                .setMatrixMetadata(
                                                    MatrixMetadata.newBuilder()
                                                        .setSubType(MultiDeploymentSpawnerUtils.MULTI_ENV_DEPLOYMENT)
                                                        .putAllMatrixValues(map))
                                                .build())
                                        .build())
                       .build());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testForEmptyInfrastructure() {
    EnvironmentYamlV2 environmentYamlV2 =
        EnvironmentYamlV2.builder()
            .environmentRef(ParameterField.createValueField("env1"))
            .infrastructureDefinitions(ParameterField.createExpressionField(true, null, null, true))
            .build();

    List<EnvironmentYamlV2> environmentYamlV2s = new ArrayList<>();

    environmentYamlV2s.add(environmentYamlV2);

    MultiDeploymentStepParameters multiDeploymentStepParameters =
        MultiDeploymentStepParameters.builder()
            .childNodeId("test")
            .environments(
                EnvironmentsYaml.builder().values(ParameterField.createValueField(environmentYamlV2s)).build())
            .build();

    assertThatThrownBy(()
                           -> multiDeploymentSpawnerStep.obtainChildrenAfterRbac(
                               prepareAmbience(), multiDeploymentStepParameters, null))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("No infrastructure definition provided. Please provide atleast one value");
  }

  private Ambiance prepareAmbience() {
    return Ambiance.newBuilder().build();
  }
}
