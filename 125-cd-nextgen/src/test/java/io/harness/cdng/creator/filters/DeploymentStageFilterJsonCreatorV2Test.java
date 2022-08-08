/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnitParamsRunner.class)
public class DeploymentStageFilterJsonCreatorV2Test extends CategoryTest {
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentService environmentService;
  @InjectMocks private DeploymentStageFilterJsonCreatorV2 filterCreator;

  private final ServiceEntity serviceEntity = ServiceEntity.builder()
                                                  .accountId("accountId")
                                                  .identifier("service-id")
                                                  .orgIdentifier("orgId")
                                                  .projectIdentifier("projectId")
                                                  .name("my-service")
                                                  .build();
  private final Environment envEntity = Environment.builder()
                                            .accountId("accountId")
                                            .identifier("env-id")
                                            .orgIdentifier("orgId")
                                            .projectIdentifier("projectId")
                                            .name("my-env")
                                            .build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get("accountId", "orgId", "projectId", "service-id", false);
    doReturn(Optional.of(envEntity)).when(environmentService).get("accountId", "orgId", "projectId", "env-id", false);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfig")
  public void getFilters(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[],\"environmentNames\":[\"my-env\"],\"serviceNames\":[\"my-service\"],\"infrastructureTypes\":[\"KubernetesDirect\"]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getFiltersWhenUseFromStage() {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage-1").build()).build())
            .infrastructure(PipelineInfrastructure.builder()
                                .environment(EnvironmentYaml.builder()
                                                 .identifier(envEntity.getIdentifier())
                                                 .name(envEntity.getName())
                                                 .build())
                                .useFromStage(InfraUseFromStage.builder().stage("stage-1").build())
                                .build())
            .build());
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[],\"environmentNames\":[\"my-env\"],\"serviceNames\":[],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getInvalidDeploymentStageConfig")
  public void testValidation(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(new YamlField(new YamlNode("stage", new ObjectNode(null))))
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class).isThrownBy(() -> filterCreator.getFilter(ctx, node));
  }

  private Object[][] getDeploymentStageConfig() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .build())
            .infrastructure(
                PipelineInfrastructure.builder()
                    .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                    .infrastructureDefinition(
                        InfrastructureDef.builder().type(InfrastructureType.KUBERNETES_DIRECT).build())
                    .build())
            .build());

    final DeploymentStageNode node2 = new DeploymentStageNode();
    node2.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(ServiceConfig.builder()
                               .service(ServiceYaml.builder()
                                            .identifier(serviceEntity.getIdentifier())
                                            .name(serviceEntity.getName())
                                            .build())
                               .build())
            .infrastructure(PipelineInfrastructure.builder()
                                .environment(EnvironmentYaml.builder()
                                                 .identifier(envEntity.getIdentifier())
                                                 .name(envEntity.getName())
                                                 .build())
                                .infrastructureDefinition(
                                    InfrastructureDef.builder().type(InfrastructureType.KUBERNETES_DIRECT).build())
                                .build())
            .build());

    return new Object[][] {{node1}, {node2}};
  }

  private Object[][] getInvalidDeploymentStageConfig() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value("env").build())
                             .build())
            .build());

    final DeploymentStageNode node2 = new DeploymentStageNode();
    node2.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .build())
            .environmentGroup(EnvironmentGroupYaml.builder()
                                  .envGroupRef(ParameterField.<String>builder().value("envg").build())
                                  .build())
            .build());

    final DeploymentStageNode node3 = new DeploymentStageNode();
    node3.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().serviceRef(ParameterField.<String>builder().value("svc").build()).build())
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .infrastructure(PipelineInfrastructure.builder()
                                .environment(EnvironmentYaml.builder()
                                                 .identifier(envEntity.getIdentifier())
                                                 .name(envEntity.getName())
                                                 .build())
                                .infrastructureDefinition(
                                    InfrastructureDef.builder().type(InfrastructureType.KUBERNETES_DIRECT).build())
                                .build())
            .build());

    final DeploymentStageNode node4 = new DeploymentStageNode();
    node4.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .build())
            .infrastructure(
                PipelineInfrastructure.builder()
                    .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                    .infrastructureDefinition(
                        InfrastructureDef.builder().type(InfrastructureType.KUBERNETES_DIRECT).build())
                    .build())
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .build());

    final DeploymentStageNode node5 = new DeploymentStageNode();
    node5.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .build())
            .infrastructure(
                PipelineInfrastructure.builder()
                    .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                    .infrastructureDefinition(
                        InfrastructureDef.builder().type(InfrastructureType.KUBERNETES_DIRECT).build())
                    .build())
            .gitOpsEnabled(Boolean.TRUE)
            .build());

    final DeploymentStageNode node6 = new DeploymentStageNode();
    node6.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().serviceRef(ParameterField.<String>builder().value("svc").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value("env").build())
                             // default to false
                             .deployToAll(ParameterField.createValueField(null))
                             .gitOpsClusters(ParameterField.createValueField(null))
                             .build())
            .gitOpsEnabled(Boolean.TRUE)
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .build());

    final DeploymentStageNode node7 = new DeploymentStageNode();
    node7.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().serviceRef(ParameterField.<String>builder().value("svc").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value("env").build())
                             // default to false
                             .deployToAll(ParameterField.createValueField(false))
                             .gitOpsClusters(ParameterField.createValueField(null))
                             .build())
            .gitOpsEnabled(Boolean.TRUE)
            .deploymentType(ServiceDefinitionType.KUBERNETES)
            .build());

    return new Object[][] {{node1}, {node2}, {node3}, {node4}, {node5}, {node6}};
  }
}
