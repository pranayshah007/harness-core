/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.cdng.service.beans.ServiceDefinitionType.KUBERNETES;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureType;
import io.harness.yaml.core.failurestrategy.OnFailureConfig;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.joor.Reflect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class DeploymentStagePMSPlanCreatorV2Test extends CDNGTestBase {
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private KryoSerializer kryoSerializer;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private AccessControlClient accessControlClient;
  @InjectMocks private DeploymentStagePMSPlanCreatorV2 deploymentStagePMSPlanCreator;

  @Mock private EnvironmentInfraFilterHelper environmentInfraFilterHelper;

  private AutoCloseable mocks;
  ObjectMapper mapper = new ObjectMapper();
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    Reflect.on(deploymentStagePMSPlanCreator).set("kryoSerializer", kryoSerializer);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  private String getYamlFromPath(String path) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    return new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddCDExecutionDependencies() throws IOException {
    YamlField executionField = getYamlFieldFromPath("cdng/plan/service.yml");

    String executionNodeId = executionField.getNode().getUuid();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    deploymentStagePMSPlanCreator.addCDExecutionDependencies(planCreationResponseMap, executionField);
    assertThat(planCreationResponseMap.containsKey(executionNodeId)).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfig")
  @PrepareForTest(YamlUtils.class)
  public void testCreatePlanForChildrenNodes_0(DeploymentStageNode node) throws IOException {
    node.setFailureStrategies(
        ParameterField.createValueField(List.of(FailureStrategyConfig.builder()
                                                    .onFailure(OnFailureConfig.builder()
                                                                   .errors(List.of(NGFailureType.ALL_ERRORS))
                                                                   .action(AbortFailureActionConfig.builder().build())
                                                                   .build())
                                                    .build())));

    JsonNode jsonNode = mapper.valueToTree(node);
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
                                  .currentField(new YamlField(new YamlNode("spec", jsonNode)))
                                  .build();
    MockedStatic<YamlUtils> mockSettings = Mockito.mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
    when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any())).thenReturn(new YamlNode("spec", jsonNode));
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
        deploymentStagePMSPlanCreator.createPlanForChildrenNodes(ctx, node);
    mockSettings.close();

    assertThat(planCreationResponseMap).hasSize(11);
    assertThat(planCreationResponseMap.values()
                   .stream()
                   .map(PlanCreationResponse::getPlanNode)
                   .filter(Objects::nonNull)
                   .map(PlanNode::getIdentifier)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(
            "provisioner", "service", "infrastructure", "artifacts", "manifests", "configFiles", "hooks");
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void failIfProjectIsFrozen() {
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setMetadata(ExecutionMetadata.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(accessControlClient.hasAccess(any(ResourceScope.class), any(Resource.class), anyString())).thenReturn(false);
    assertThatThrownBy(() -> deploymentStagePMSPlanCreator.failIfProjectIsFrozen(ctx))
        .isInstanceOf(NGFreezeException.class)
        .matches(ex -> ex.getMessage().equals("Execution can't be performed because project is frozen"));

    verify(freezeEvaluateService, times(1)).getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void failIfProjectIsFrozenWithOverridePermission() {
    doReturn(false).when(featureFlagHelperService).isEnabled(anyString(), any());
    List<FreezeSummaryResponseDTO> freezeSummaryResponseDTOList = Lists.newArrayList(createGlobalFreezeResponse());
    doReturn(freezeSummaryResponseDTOList)
        .when(freezeEvaluateService)
        .getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setMetadata(ExecutionMetadata.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(io.harness.pms.contracts.plan.PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(true);
    deploymentStagePMSPlanCreator.failIfProjectIsFrozen(ctx);

    verify(freezeEvaluateService, times(0)).getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
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
    FreezeConfigEntity freezeConfigEntity =
        NGFreezeDtoMapper.toFreezeConfigEntity("accountId", null, null, yaml, FreezeType.GLOBAL);
    return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
  }

  private Object[][] getDeploymentStageConfig() {
    String svcId = "svcId";
    String envId = "envId";
    Map<String, Object> step = Map.of("name", "teststep");
    Map<String, Object> provisionStep = Map.of("name", "testprovisionstep");

    final DeploymentStageNode node1 = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .service(
                ServiceYamlV2.builder().uuid("serviceuuid").serviceRef(ParameterField.createValueField(svcId)).build())
            .environment(EnvironmentYamlV2.builder()
                             .uuid("envuuid")
                             .environmentRef(ParameterField.<String>builder().value(envId).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .provisioner(ExecutionElementConfig.builder()
                                              .uuid("provuuid")
                                              .steps(List.of(ExecutionWrapperConfig.builder()
                                                                 .uuid("provstepuuid")
                                                                 .step(mapper.valueToTree(provisionStep))
                                                                 .build()))
                                              .build())
                             .infrastructureDefinitions(ParameterField.createValueField(
                                 asList(InfraStructureDefinitionYaml.builder()
                                            .identifier(ParameterField.createValueField("infra"))
                                            .build())))
                             .build())
            .deploymentType(KUBERNETES)
            .execution(ExecutionElementConfig.builder()
                           .uuid("executionuuid")
                           .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                           .build())
            .build());

    final DeploymentStageNode node2 = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .service(
                ServiceYamlV2.builder().uuid("serviceuuid").serviceRef(ParameterField.createValueField(svcId)).build())
            .environment(EnvironmentYamlV2.builder()
                             .uuid("envuuid")
                             .environmentRef(ParameterField.<String>builder().value(envId).build())
                             .deployToAll(ParameterField.createValueField(false))
                             .provisioner(ExecutionElementConfig.builder()
                                              .uuid("provuuid")
                                              .steps(List.of(ExecutionWrapperConfig.builder()
                                                                 .uuid("provstepuuid")
                                                                 .step(mapper.valueToTree(provisionStep))
                                                                 .build()))
                                              .build())
                             .infrastructureDefinition(ParameterField.createValueField(
                                 InfraStructureDefinitionYaml.builder()
                                     .identifier(ParameterField.createValueField("infra"))
                                     .build()))
                             .build())
            .deploymentType(KUBERNETES)
            .execution(ExecutionElementConfig.builder()
                           .uuid("executionuuid")
                           .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                           .build())
            .build());

    return new Object[][] {{node1}, {node2}};
  }

  private Object[][] getDeploymentStageConfigForMultiSvcMultiEvs() {
    String svcId = "svcId";
    String envId = "envId";
    Map<String, Object> step = Map.of("name", "teststep");

    final DeploymentStageNode nodeEnvsFilters = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .service(
                ServiceYamlV2.builder().uuid("serviceuuid").serviceRef(ParameterField.createValueField(svcId)).build())
            .environments(EnvironmentsYaml.builder()
                              .uuid("environments-uuid")
                              .values(ParameterField.createValueField(
                                  asList(EnvironmentYamlV2.builder()
                                             .uuid("envuuid")
                                             .environmentRef(ParameterField.<String>builder().value(envId).build())
                                             .deployToAll(ParameterField.createValueField(false))
                                             .infrastructureDefinitions(ParameterField.createValueField(
                                                 asList(InfraStructureDefinitionYaml.builder()
                                                            .identifier(ParameterField.createValueField("infra"))
                                                            .build())))
                                             .build())))
                              .filters(ParameterField.createValueField(
                                  asList(FilterYaml.builder()
                                             .type(FilterType.all)
                                             .entities(Set.of(Entity.environments, Entity.infrastructures))
                                             .build())))
                              .build())
            .deploymentType(KUBERNETES)
            .execution(ExecutionElementConfig.builder()
                           .uuid("executionuuid")
                           .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                           .build())
            .build());

    final DeploymentStageNode multiSvcMultienvsNodeWithFilter = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .services(ServicesYaml.builder()
                          .uuid("services-uuid")
                          .values(ParameterField.createValueField(
                              Arrays.asList(ServiceYamlV2.builder()
                                                .uuid("serviceuuid")
                                                .serviceRef(ParameterField.createValueField(svcId))
                                                .build())))
                          .build())
            .environments(EnvironmentsYaml.builder()
                              .uuid("environments-uuid")
                              .values(ParameterField.createValueField(
                                  asList(EnvironmentYamlV2.builder()
                                             .uuid("envuuid")
                                             .environmentRef(ParameterField.<String>builder().value(envId).build())
                                             .deployToAll(ParameterField.createValueField(false))
                                             .infrastructureDefinitions(ParameterField.createValueField(
                                                 asList(InfraStructureDefinitionYaml.builder()
                                                            .identifier(ParameterField.createValueField("infra"))
                                                            .build())))
                                             .build())))
                              .filters(ParameterField.createValueField(
                                  asList(FilterYaml.builder()
                                             .type(FilterType.all)
                                             .entities(Set.of(Entity.environments, Entity.infrastructures))
                                             .build())))
                              .build())
            .deploymentType(KUBERNETES)
            .execution(ExecutionElementConfig.builder()
                           .uuid("executionuuid")
                           .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                           .build())
            .build());

    final DeploymentStageNode multiSvcWithEnvGroupNodeWithFilter = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .services(ServicesYaml.builder()
                          .uuid("services-uuid")
                          .values(ParameterField.createValueField(
                              asList(ServiceYamlV2.builder()
                                         .uuid("serviceuuid")
                                         .serviceRef(ParameterField.createValueField(svcId))
                                         .build())))
                          .build())
            .environmentGroup(EnvironmentGroupYaml.builder()
                                  .environments(ParameterField.createValueField(
                                      asList(EnvironmentYamlV2.builder()
                                                 .uuid("envuuid")
                                                 .environmentRef(ParameterField.<String>builder().value(envId).build())
                                                 .deployToAll(ParameterField.createValueField(false))
                                                 .infrastructureDefinitions(ParameterField.createValueField(
                                                     asList(InfraStructureDefinitionYaml.builder()
                                                                .identifier(ParameterField.createValueField("infra"))
                                                                .build())))
                                                 .build())))
                                  .filters(ParameterField.createValueField(
                                      asList(FilterYaml.builder()
                                                 .type(FilterType.all)
                                                 .entities(Set.of(Entity.environments, Entity.infrastructures))
                                                 .build())))
                                  .build())
            .deploymentType(KUBERNETES)
            .execution(ExecutionElementConfig.builder()
                           .uuid("executionuuid")
                           .steps(List.of(ExecutionWrapperConfig.builder().step(mapper.valueToTree(step)).build()))
                           .build())
            .build());

    return new Object[][] {{multiSvcMultienvsNodeWithFilter}, {multiSvcWithEnvGroupNodeWithFilter}, {nodeEnvsFilters}};
  }
  // TODO VS: Fix these tests

  //  @Test
  //  @Owner(developers = OwnerRule.ROHITKARELIA)
  //  @Category(UnitTests.class)
  //  public void testfilterInfras() {
  //    List<FilterYaml> filterYamlList =
  //        asList(FilterYaml.builder().type(FilterType.all).entities(Set.of(Entity.infrastructures)).build());
  //    Set<InfrastructureEntity> infrastructureEntitySet =
  //        Set.of(InfrastructureEntity.builder()
  //                   .accountId("accountId")
  //                   .identifier("infra-id")
  //                   .envIdentifier("envId")
  //                   .tag(NGTag.builder().key("infra").value("dev").build())
  //                   .build());
  //    doReturn(infrastructureEntitySet).when(environmentInfraFilterHelper).applyFilteringOnInfras(any(), any());
  //    List<EnvironmentYamlV2> environmentYamlV2List =
  //        deploymentStagePMSPlanCreator.filterInfras(filterYamlList, "envId", infrastructureEntitySet);
  //    assertThat(environmentYamlV2List.size()).isEqualTo(infrastructureEntitySet.size());
  //  }
  //
  //  @Test
  //  @Owner(developers = OwnerRule.ROHITKARELIA)
  //  @Category(UnitTests.class)
  //  public void testcreateInfraDefinitionYaml() {
  //    InfraStructureDefinitionYaml infraDefinitionYaml = deploymentStagePMSPlanCreator.createInfraDefinitionYaml(
  //        InfrastructureEntity.builder().identifier("infra-id").build());
  //    assertThat(infraDefinitionYaml).isNotNull();
  //    assertThat(infraDefinitionYaml.getIdentifier()).isNotNull();
  //  }
  //
  //  @Test
  //  @Owner(developers = OwnerRule.ROHITKARELIA)
  //  @Category(UnitTests.class)
  //  @Parameters(method = "getDeploymentStageConfigForMultiSvcMultiEvs")
  //  public void testCreatePlanForChildrenNodesWithFilters_0(DeploymentStageNode node) {
  //    when(environmentInfraFilterHelper.areFiltersPresent(any())).thenReturn(true);
  //
  //    doReturn(true).when(featureFlagHelperService).isEnabled("accountId",
  //    FeatureName.CDS_FILTER_INFRA_CLUSTERS_ON_TAGS);
  //
  //    node.setFailureStrategies(List.of(FailureStrategyConfig.builder()
  //                                          .onFailure(OnFailureConfig.builder()
  //                                                         .errors(List.of(NGFailureType.ALL_ERRORS))
  //                                                         .action(AbortFailureActionConfig.builder().build())
  //                                                         .build())
  //                                          .build()));
  //
  //    JsonNode jsonNode = mapper.valueToTree(node);
  //    PlanCreationContext ctx = PlanCreationContext.builder()
  //                                  .globalContext(Map.of("metadata",
  //                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
  //                                  .currentField(new YamlField(new YamlNode("spec", jsonNode)))
  //                                  .build();
  //    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
  //        deploymentStagePMSPlanCreator.createPlanForChildrenNodes(ctx, node);
  //
  //    assertThat(planCreationResponseMap).hasSize(9);
  //    assertThat(planCreationResponseMap.values()
  //                   .stream()
  //                   .map(PlanCreationResponse::getPlanNode)
  //                   .filter(Objects::nonNull)
  //                   .map(PlanNode::getIdentifier)
  //                   .collect(Collectors.toSet()))
  //        .containsAnyOf("service", "infrastructure", "artifacts", "manifests", "configFiles");
  //  }
  private DeploymentStageNode buildNode(DeploymentStageConfig config) {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setUuid("nodeuuid");
    node.setDeploymentStageConfig(config);
    return node;
  }

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetIdentifierWithExpressionForGitOps() {
    // Gitops with single service, single env.
    DeploymentStageNode node = DeploymentStageNode.builder()
                                   .deploymentStageConfig(DeploymentStageConfig.builder().gitOpsEnabled(true).build())
                                   .build();
    PlanCreationContext context =
        PlanCreationContext.builder().currentField(new YamlField("node", new YamlNode(new TextNode("abcc")))).build();

    assertThat(deploymentStagePMSPlanCreator.getIdentifierWithExpression(context, node, "id1")).isEqualTo("id1");

    // Gitops with single service, multi env.
    node = DeploymentStageNode.builder()
               .deploymentStageConfig(DeploymentStageConfig.builder()
                                          .gitOpsEnabled(true)
                                          .environments(EnvironmentsYaml.builder().build())
                                          .build())
               .build();
    assertThat(deploymentStagePMSPlanCreator.getIdentifierWithExpression(context, node, "id1")).isEqualTo("id1");

    // Gitops with multi service, multi env.
    node = DeploymentStageNode.builder()
               .deploymentStageConfig(DeploymentStageConfig.builder()
                                          .gitOpsEnabled(true)
                                          .services(ServicesYaml.builder().build())
                                          .environments(EnvironmentsYaml.builder().build())
                                          .build())
               .build();
    assertThat(deploymentStagePMSPlanCreator.getIdentifierWithExpression(context, node, "id1"))
        .isEqualTo("id1<+strategy.identifierPostFix>");
  }
}
