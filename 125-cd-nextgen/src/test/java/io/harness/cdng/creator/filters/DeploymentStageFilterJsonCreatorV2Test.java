/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

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
}
