/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class MultiDeploymentSpawnerStepTest extends CategoryTest {
  @InjectMocks private final MultiDeploymentSpawnerStep multiDeploymentSpawnerStep = new MultiDeploymentSpawnerStep();

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleChildrenResponseInternal() {
    StepResponseNotifyData stepResponseNotifyData = StepResponseNotifyData.builder().status(Status.SUCCEEDED).build();
    assertThat(multiDeploymentSpawnerStep
                   .handleChildrenResponseInternal(null, null, Maps.newHashMap("a", stepResponseNotifyData))
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

    assertThat(multiDeploymentSpawnerStep.obtainChildrenAfterRbac(null, multiDeploymentStepParameters, null))
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
    assertThat(multiDeploymentSpawnerStep.obtainChildrenAfterRbac(null, multiDeploymentStepParameters, null))
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
}
