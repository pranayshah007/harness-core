/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.cdng.service.beans.ServiceDefinitionType.KUBERNETES;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VINIT_KUMAR;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentInfraUseFromStage;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.InfraUseFromStage;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.plancreator.strategy.HarnessForConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@RunWith(JUnitParamsRunner.class)
public class DeploymentStageFilterJsonCreatorV2Test extends CategoryTest {
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentService environmentService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private InfrastructureEntityService infraService;
  @Spy @InjectMocks private StageFilterCreatorHelper stageFilterCreatorHelper;
  @InjectMocks private DeploymentStageFilterJsonCreatorV2 filterCreator;

  private final ClassLoader classLoader = this.getClass().getClassLoader();

  private final ServiceEntity serviceEntity = ServiceEntity.builder()
                                                  .accountId("accountId")
                                                  .identifier("service-id")
                                                  .orgIdentifier("orgId")
                                                  .projectIdentifier("projectId")
                                                  .name("my-service")
                                                  .type(KUBERNETES)
                                                  .yaml("service:\n"
                                                      + "    name: my-service\n"
                                                      + "    identifier: service-id\n"
                                                      + "    tags: {}\n"
                                                      + "    serviceDefinition:\n"
                                                      + "        type: Kubernetes\n"
                                                      + "        spec:\n"
                                                      + "            variables:\n"
                                                      + "                - name: svar1\n"
                                                      + "                  type: String\n"
                                                      + "                  value: ServiceVariable1\n"
                                                      + "    gitOpsEnabled: false\n")
                                                  .build();
  private final Environment envEntity = Environment.builder()
                                            .accountId("accountId")
                                            .identifier("env-id")
                                            .orgIdentifier("orgId")
                                            .projectIdentifier("projectId")
                                            .name("my-env")
                                            .build();

  private final InfrastructureEntity infra = InfrastructureEntity.builder()
                                                 .accountId("accountId")
                                                 .orgIdentifier("orgId")
                                                 .identifier("infra-id")
                                                 .projectIdentifier("projectId")
                                                 .name("infra-name")
                                                 .type(InfrastructureType.KUBERNETES_DIRECT)
                                                 .build();
  private AutoCloseable mocks;
  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .getMetadata("accountId", "orgId", "projectId", "service-id", false);
    doReturn(List.of(serviceEntity))
        .when(serviceEntityService)
        .getMetadata("accountId", "orgId", "projectId", List.of("service-id"));
    doReturn(Optional.of(envEntity)).when(environmentService).get("accountId", "orgId", "projectId", "env-id", false);
    doReturn(Optional.of(envEntity))
        .when(environmentService)
        .getMetadata("accountId", "orgId", "projectId", "env-id", false);

    doReturn(Optional.of(infra)).when(infraService).get("accountId", "orgId", "projectId", "env-id", "infra-id");
    doReturn(Lists.newArrayList(infra))
        .when(infraService)
        .getAllInfrastructuresWithYamlFromIdentifierList(
            "accountId", "orgId", "projectId", "env-id", null, Lists.newArrayList("infra-id"));
    doReturn(true).when(ngFeatureFlagHelperService).isEnabled("accountId", FeatureName.CDS_SCOPE_INFRA_TO_SERVICES);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfig")
  public void getFilters(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[\"Kubernetes\"],\"environmentNames\":[\"env-id\"],\"serviceNames\":[\"service-id\"],\"infrastructureTypes\":[\"KubernetesDirect\"]}");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigUseFromStageInvalid")
  public void getFiltersWithUseFromStageInvalid(DeploymentStageNode node) throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(pipeline, 6));
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(specField)
                                    .build();
    assertThatThrownBy(() -> filterCreator.getFilter(ctx, node))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Only one of serviceRef and useFromStage fields are allowed in service. Please remove one and try again");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigUseFromStageForEnvironmentInvalid")
  public void getFiltersWithUseFromStageForEnvironmentInvalid(DeploymentStageNode node) throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(pipeline, 6));
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(specField)
                                    .build();
    assertThatThrownBy(() -> filterCreator.getFilter(ctx, node))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Only one of environmentRef and useFromStage fields are allowed in environment. Please remove one and try again");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigUseFromStageForServicesInvalid")
  public void getFiltersWithUseFromStageForServicesInvalid(DeploymentStageNode node) throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(pipeline, 6));
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(specField)
                                    .build();
    assertThatThrownBy(() -> filterCreator.getFilter(ctx, node))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Only one of services.values and services.useFromStage is allowed in CD stage yaml");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigWithFilters")
  public void getFiltersWithEnvFilters(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[\"Kubernetes\"],\"environmentNames\":[\"env-id\"],\"serviceNames\":[\"service-id\"],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigGitops")
  public void getFiltersGitops(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[\"Kubernetes\"],\"environmentNames\":[\"env-id\"],\"serviceNames\":[\"service-id\"],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigEnvGroup")
  public void getFiltersEnvGroup(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo("{\"deploymentTypes\":[],\"environmentNames\":[],\"serviceNames\":[],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfigEnvironments")
  public void getFiltersEnvironments(DeploymentStageNode node) {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .currentField(new YamlField(new YamlNode(null)))
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo("{\"deploymentTypes\":[],\"environmentNames\":[],\"serviceNames\":[],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "dataForUseFromStage")
  public void getFiltersWhenUseFromStage(DeploymentStageNode node) throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/pipeline.yaml");
    YamlField pipeline = new YamlField("pipeline", YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField("spec", getStageNodeAtIndex(pipeline, 6));
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(specField)
                                    .build();
    PipelineFilter filter = filterCreator.getFilter(ctx, node);
    assertThat(filter.toJson())
        .isEqualTo(
            "{\"deploymentTypes\":[],\"environmentNames\":[\"env-id\"],\"serviceNames\":[],\"infrastructureTypes\":[]}");
  }

  private Object[][] dataForUseFromStage() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build())
            .infrastructure(PipelineInfrastructure.builder()
                                .environment(EnvironmentYaml.builder()
                                                 .identifier(envEntity.getIdentifier())
                                                 .name(envEntity.getName())
                                                 .build())
                                .useFromStage(InfraUseFromStage.builder().stage("stage1").build())
                                .build())
            .build());

    final DeploymentStageNode node2 = new DeploymentStageNode();
    node2.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(
                ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("stage1").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             // default to false
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinitions(ParameterField.createValueField(
                                 List.of(InfraStructureDefinitionYaml.builder()
                                             .identifier(ParameterField.createValueField("some-random-infra"))
                                             .build())))
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}, {node2}};
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

  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateMultiServices() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .services(ServicesYaml.builder().values(ParameterField.createValueField(Collections.emptyList())).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(YamlNode.fromYamlPath(getYaml("validateMultiServices.yaml"), ""));
    YamlField currentField = fullYamlField.fromYamlPath("spec");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessage("Atleast one service is required, Please select a service and try again");
  }
  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveStageTemplateWithUseFromStage() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(
                ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("stageId").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField =
        new YamlField(YamlNode.fromYamlPath(getYaml("stageTemplateSpecWithUseFromStage.yaml"), ""));
    YamlField currentField = fullYamlField.fromYamlPath("spec");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining("Stage template that propagates service from another stage cannot be saved");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveStageTemplateWithUseFromStageForServices() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .services(
                ServicesYaml.builder().useFromStage(ServiceUseFromStageV2.builder().stage("stageId").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField =
        new YamlField(YamlNode.fromYamlPath(getYaml("stageTemplateSpecWithUseFromStageForServices.yaml"), ""));
    YamlField currentField = fullYamlField.fromYamlPath("spec");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining("Stage template that propagates services from another stage cannot be saved");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testSaveStageTemplateWithUseFromStageFromEnvironment() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .useFromStage(EnvironmentInfraUseFromStage.builder().stage("stageId").build())
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField =
        new YamlField(YamlNode.fromYamlPath(getYaml("stageTemplateSpecWithUsageFromStageFromEnvironment.yaml"), ""));
    YamlField currentField = fullYamlField.fromYamlPath("spec");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining("Stage template that propagates environment from another stage cannot be saved");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidationEnvironmentProvisioners() throws IOException {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(new YamlField(new YamlNode("stage", new ObjectNode(null))))
                                    .build();

    final DeploymentStageNode node = getDeploymentStageNodeFromYaml("deployStageWithEnvironmentAndProvisioner.yaml");

    PipelineFilter filter = filterCreator.getFilter(ctx, node);

    assertThat(filter).isNotNull();
    assertThat(filter.toJson())
        .isEqualTo("{\"deploymentTypes\":[],\"environmentNames\":[],\"serviceNames\":[],\"infrastructureTypes\":[]}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidationEnvironmentProvisionersWithDuplicateIdentifiers() throws IOException {
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(new YamlField(new YamlNode("stage", new ObjectNode(null))))
                                    .build();

    final DeploymentStageNode node =
        getDeploymentStageNodeFromYaml("deployStageWithEnvironmentAndProvisionerDuplicateIdentifiers.yaml");

    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(
            "Environment contains duplicates provisioner identifiers [duplicateIdentifier], stage []");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithUseFromStageReferredStageNotPresentForServicePropagation() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder().build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField =
        new YamlField(YamlNode.fromYamlPath(getYaml("pipelineWithUseFromStageReferredStageNotExists.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[0]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(String.format(
            "Stage with identifier [%s] given for service propagation does not exist. Please add it and try again.",
            "s1"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithParallelStagesAndUseFromStageReferredStageNotPresentForServicePropagation()
      throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder().build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithParallelStagesAndUseFromStageReferredStageNotExists.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[0]/parallel/[1]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(String.format(
            "Stage with identifier [%s] given for service propagation does not exist. Please add it and try again.",
            "s1"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithParallelStagesAndUseFromStageReferredStageIsPresentForServicePropagation()
      throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.createValueField("env"))
                             .infrastructureDefinition(
                                 ParameterField.createValueField(InfraStructureDefinitionYaml.builder().build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithParallelStagesAndUseFromStageReferredStageExists.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[1]/parallel/[1]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    filterCreator.getFilter(ctx, node);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithParallelStagesAndUseFromStageReferredStageNotPresentForServicePropagation2()
      throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder().build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithParallelStagesAndUseFromStageReferredStageNotExists2.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[1]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(String.format(
            "Stage with identifier [%s] given for service propagation does not exist. Please add it and try again.",
            "s1"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithParallelStagesAndUseFromStageReferredStageIsPresentForServicePropagation2()
      throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.createValueField("env"))
                             .infrastructureDefinition(
                                 ParameterField.createValueField(InfraStructureDefinitionYaml.builder().build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithParallelStagesAndUseFromStageReferredStageExists2.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[1]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    filterCreator.getFilter(ctx, node);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithUseFromStageReferredStageNotPresentForMultiServicePropagation() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .services(ServicesYaml.builder().useFromStage(ServiceUseFromStageV2.builder().stage("s1").build()).build())
            .environment(EnvironmentYamlV2.builder().build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithUseFromStageReferredStageNotExistsForMultiServices.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[0]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(String.format(
            "Stage with identifier [%s] given for multi-service propagation does not exist. Please add it and try again.",
            "s1"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testSavePipelineWithUseFromStageNotPresentForEnvironmentPropagation() throws IOException {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder().build())
            .environment(EnvironmentYamlV2.builder()
                             .useFromStage(EnvironmentInfraUseFromStage.builder().stage("s1").build())
                             .build())
            .deploymentType(KUBERNETES)
            .build());
    YamlField fullYamlField = new YamlField(
        YamlNode.fromYamlPath(getYaml("pipelineWithUseFromStageReferredStageNotExistsForEnvironment.yaml"), ""));

    YamlField currentField = fullYamlField.fromYamlPath("pipeline/stages/[0]/stage");
    FilterCreationContext ctx = FilterCreationContext.builder()
                                    .setupMetadata(SetupMetadata.newBuilder()
                                                       .setAccountId("accountId")
                                                       .setOrgId("orgId")
                                                       .setProjectId("projectId")
                                                       .build())
                                    .currentField(currentField)
                                    .build();
    assertThatExceptionOfType(InvalidYamlRuntimeException.class)
        .isThrownBy(() -> filterCreator.getFilter(ctx, node))
        .withMessageContaining(String.format(
            "Stage with identifier [%s] given for environment propagation does not exist. Please add it and try again.",
            "s1"));
  }

  private Object[][] getDeploymentStageConfigWithFilters() throws IOException {
    final DeploymentStageNode node1 =
        getDeploymentStageNodeFromYaml("multisvcinfra/gitops/deployStageWithEnvironmentAndFilter.yaml");

    return new Object[][] {{node1}};
  }
  private Object[][] getDeploymentStageConfig() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .serviceConfig(
                ServiceConfig.builder()
                    .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                    .serviceDefinition(ServiceDefinition.builder().type(KUBERNETES).build())
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
                               .serviceDefinition(ServiceDefinition.builder().type(KUBERNETES).build())
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

    final DeploymentStageNode node3 = new DeploymentStageNode();
    node3.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             // default to false
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinitions(ParameterField.createValueField(
                                 List.of(InfraStructureDefinitionYaml.builder()
                                             .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                             .build())))
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    final DeploymentStageNode node4 = new DeploymentStageNode();
    node4.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}, {node2}, {node3}, {node4}};
  }

  private Object[][] getDeploymentStageConfigUseFromStageInvalid() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .useFromStage(ServiceUseFromStageV2.builder().stage("stage1").build())
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField(infra.getIdentifier()))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}};
  }

  private Object[][] getDeploymentStageConfigUseFromStageForEnvironmentInvalid() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .useFromStage(EnvironmentInfraUseFromStage.builder().stage("stage1").build())
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}};
  }

  private Object[][] getDeploymentStageConfigUseFromStageForServicesInvalid() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .services(
                ServicesYaml.builder()
                    .values(ParameterField.createValueField(List.of(
                        ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("service_1")).build())))
                    .useFromStage(ServiceUseFromStageV2.builder().stage("stage1").build())
                    .build())
            .environment(EnvironmentYamlV2.builder()
                             .useFromStage(EnvironmentInfraUseFromStage.builder().stage("stage1").build())
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .build())
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}};
  }

  private Object[][] getDeploymentStageConfigGitops() {
    final DeploymentStageNode node1 = new DeploymentStageNode();
    node1.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(true))
                             .build())
            .gitOpsEnabled(true)
            .deploymentType(KUBERNETES)
            .build());

    final DeploymentStageNode node2 = new DeploymentStageNode();
    node2.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .gitOpsClusters(ParameterField.createValueField(List.of(
                                 ClusterYaml.builder().identifier(ParameterField.createValueField("c1")).build())))
                             .build())
            .gitOpsEnabled(true)
            .deploymentType(KUBERNETES)
            .build());

    final DeploymentStageNode node3 = new DeploymentStageNode();
    node3.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.createValueField(serviceEntity.getIdentifier()))
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .gitOpsClusters(ParameterField.<List<ClusterYaml>>builder()
                                                 .expression(true)
                                                 .expressionValue("<+input>")
                                                 .build())
                             .build())
            .gitOpsEnabled(true)
            .deploymentType(KUBERNETES)
            .build());

    return new Object[][] {{node1}, {node2}, {node3}};
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
            .deploymentType(KUBERNETES)
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
            .deploymentType(KUBERNETES)
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
            .deploymentType(KUBERNETES)
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
            .deploymentType(KUBERNETES)
            .build());

    // multiservice with strategy
    final DeploymentStageNode node8 = new DeploymentStageNode();
    node8.setStrategy(ParameterField.createValueField(
        StrategyConfig.builder()
            .repeat(HarnessForConfig.builder().items(ParameterField.createValueField(List.of("a", "b"))).build())
            .build()));
    node8.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .services(
                ServicesYaml.builder()
                    .values(ParameterField.createValueField(List.of(
                        ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("service_1")).build())))
                    .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value("env").build())
                             // default to false
                             .deployToAll(ParameterField.createValueField(false))
                             .gitOpsClusters(ParameterField.createValueField(null))
                             .build())
            .gitOpsEnabled(Boolean.TRUE)
            .deploymentType(KUBERNETES)
            .build());

    final DeploymentStageNode node9 = new DeploymentStageNode();
    node9.setDeploymentStageConfig(
        DeploymentStageConfig.builder()
            .service(ServiceYamlV2.builder()
                         .serviceRef(ParameterField.<String>builder().value(serviceEntity.getIdentifier()).build())
                         .build())
            .environment(EnvironmentYamlV2.builder()
                             .environmentRef(ParameterField.<String>builder().value(envEntity.getIdentifier()).build())
                             .build())
            .deploymentType(KUBERNETES)
            .gitOpsEnabled(Boolean.FALSE)
            .build());

    return new Object[][] {{node1}, {node2}, {node3}, {node4}, {node5}, {node6}, {node7}, {node8}, {node9}};
  }
  private Object[][] getDeploymentStageConfigEnvGroup() throws IOException {
    final DeploymentStageNode node1 =
        getDeploymentStageNodeFromYaml("multisvcinfra/deployStageWithEnvironmentGroupAndFilters.yaml");
    final DeploymentStageNode node2 =
        getDeploymentStageNodeFromYaml("multisvcinfra/deployStageWithEnvironmentGroupAndFiltersRuntime.yaml");
    return new Object[][] {{node1}, {node2}};
  }

  private Object[][] getDeploymentStageConfigEnvironments() throws IOException {
    final DeploymentStageNode node1 =
        getDeploymentStageNodeFromYaml("multisvcinfra/deployStageWithMultiEnvironments.yaml");

    final DeploymentStageNode node2 =
        getDeploymentStageNodeFromYaml("multisvcinfra/deployStageWithMultiEnvironmentsAndFilters.yaml");

    final DeploymentStageNode node3 =
        getDeploymentStageNodeFromYaml("multisvcinfra/deployStageWithMultiEnvironmentsAndFilterAsRuntime.yaml");
    return new Object[][] {{node1}, {node2}, {node3}};
  }

  private DeploymentStageNode getDeploymentStageNodeFromYaml(String filePath) throws IOException {
    String stageYaml = getYaml(filePath);
    return YamlPipelineUtils.read(stageYaml, DeploymentStageNode.class);
  }

  private String getYaml(String filePath) throws IOException {
    final URL testFile = classLoader.getResource(filePath);
    return Resources.toString(testFile, Charsets.UTF_8);
  }

  private static YamlNode getStageNodeAtIndex(YamlField pipeline, int idx) {
    return pipeline.getNode()
        .getField("pipeline")
        .getNode()
        .getField("stages")
        .getNode()
        .asArray()
        .get(idx)
        .getField("stage")
        .getNode()
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode();
  }
}
