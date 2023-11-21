/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import static io.harness.cdng.service.beans.ServiceDefinitionType.KUBERNETES;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.creator.plan.stage.DeploymentStagePlanCreationInfo;
import io.harness.cdng.creator.plan.stage.DeploymentStageType;
import io.harness.cdng.creator.plan.stage.MultiServiceEnvDeploymentStageDetailsInfo;
import io.harness.cdng.creator.plan.stage.SingleServiceEnvDeploymentStageDetailsInfo;
import io.harness.cdng.creator.plan.stage.StagePlanCreatorHelper;
import io.harness.cdng.creator.plan.stage.service.DeploymentStagePlanCreationInfoService;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterType;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.NGServiceEntityHelper;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesMetadata;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.cdng.service.steps.helpers.beans.ServiceStepV3Parameters;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGFreezeException;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.core.failurestrategy.abort.v1.AbortFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.v1.FailureConfigV1;
import io.harness.yaml.core.failurestrategy.v1.NGFailureTypeV1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class DeploymentStagePlanCreatorTest extends CDNGTestBase {
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private KryoSerializer kryoSerializer;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @Mock private AccessControlClient accessControlClient;
  @Spy private StagePlanCreatorHelper stagePlanCreatorHelper;
  @Mock private NGServiceEntityHelper serviceEntityHelper;
  @Mock private DeploymentStagePlanCreationInfoService deploymentStagePlanCreationInfoService;
  @Spy private ExecutorService executorService;
  @InjectMocks private DeploymentStagePlanCreator deploymentStagePlanCreator;

  private AutoCloseable mocks;
  ObjectMapper mapper = new ObjectMapper();
  @Before
  public void setUp() throws Exception {
    executorService = Executors.newSingleThreadExecutor();
    mocks = MockitoAnnotations.openMocks(this);

    Reflect.on(stagePlanCreatorHelper).set("kryoSerializer", kryoSerializer);
    Reflect.on(deploymentStagePlanCreator).set("kryoSerializer", kryoSerializer);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
    executorService.shutdown();
  }

  @Test
  @Owner(developers = {YOGESH, MLUKIC})
  @Category(UnitTests.class)
  @Parameters(method = "getDeploymentStageConfig")
  @PrepareForTest(YamlUtils.class)
  public void testCreatePlanForChildrenNodes(DeploymentStageNodeV1 node) throws IOException {
    doReturn(false).when(stagePlanCreatorHelper).isProjectScopedResourceConstraintQueueByFFOrSetting(any());
    node.setFailure(ParameterField.createValueField(List.of(FailureConfigV1.builder()
                                                                .errors(List.of(NGFailureTypeV1.ALL_ERRORS))
                                                                .action(AbortFailureActionConfigV1.builder().build())
                                                                .build())));

    JsonNode jsonNode = YamlUtils.readTree(node, YAMLFieldNameConstants.SPEC, null).getNode().getCurrJsonNode();
    ((ObjectNode) jsonNode).put("type", YAMLFieldNameConstants.DEPLOYMENT_STAGE_V1);

    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
                                  .currentField(new YamlField(new YamlNode(YAMLFieldNameConstants.SPEC, jsonNode)))
                                  .build();

    try (MockedStatic<YamlUtils> mockSettings = mockStatic(YamlUtils.class, CALLS_REAL_METHODS);
         MockedStatic<NGRestUtils> ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class)) {
      SettingValueResponseDTO settingValueResponseDTO =
          SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
      when(serviceEntityHelper.getServiceDefinitionTypeFromService(any(), any())).thenReturn(KUBERNETES);
      when(YamlUtils.getGivenYamlNodeFromParentPath(any(), any()))
          .thenReturn(new YamlNode(YAMLFieldNameConstants.SPEC, jsonNode));
      when(NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
          deploymentStagePlanCreator.createPlanForChildrenNodes(ctx, new YamlField(new YamlNode(jsonNode)));

      assertThat(planCreationResponseMap).hasSize(10);
      assertThat(planCreationResponseMap.values()
                     .stream()
                     .map(PlanCreationResponse::getPlanNode)
                     .filter(Objects::nonNull)
                     .map(PlanNode::getIdentifier)
                     .collect(Collectors.toSet()))
          .containsExactlyInAnyOrder(
              "provisioner", "service", "infrastructure", "artifacts", "manifests", "configFiles", "hooks");
    }
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
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
                                          .setExecutionContext(PlanExecutionContext.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(accessControlClient.hasAccess(any(ResourceScope.class), any(Resource.class), anyString())).thenReturn(false);
    assertThatThrownBy(() -> deploymentStagePlanCreator.failIfProjectIsFrozen(ctx))
        .isInstanceOf(NGFreezeException.class)
        .matches(ex -> ex.getMessage().equals("Execution can't be performed because project is frozen"));

    verify(freezeEvaluateService, times(1)).getActiveFreezeEntities(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
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
                                          .setExecutionContext(PlanExecutionContext.newBuilder().setPrincipalInfo(
                                              ExecutionPrincipalInfo.newBuilder()
                                                  .setPrincipal("prinicipal")
                                                  .setPrincipalType(PrincipalType.USER)
                                                  .build()))
                                          .build()))
                                  .build();
    when(
        accessControlClient.hasAccess(any(Principal.class), any(ResourceScope.class), any(Resource.class), anyString()))
        .thenReturn(true);
    deploymentStagePlanCreator.failIfProjectIsFrozen(ctx);

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
        NGFreezeDtoMapper.toFreezeConfigEntity("accountId", "orgId", "projId", yaml, FreezeType.GLOBAL);
    return NGFreezeDtoMapper.prepareFreezeResponseSummaryDto(freezeConfigEntity);
  }

  private Object[][] getDeploymentStageConfig() {
    String svcId = "svcId";
    String envId = "envId";
    Map<String, Object> step = Map.of("name", "teststep", "id", "teststep", "type", "K8sRollingDeploy");
    Map<String, Object> provisionStep = Map.of("name", "testprovisionstep");

    final DeploymentStageNodeV1 node1 = buildNode(
        DeploymentStageConfigV1.builder()
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
            .steps(List.of(mapper.valueToTree(step)))
            .build());

    final DeploymentStageNodeV1 node2 = buildNode(
        DeploymentStageConfigV1.builder()
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
            .steps(List.of(mapper.valueToTree(step)))
            .build());

    return new Object[][] {{node1}, {node2}};
  }

  private Object[][] getDeploymentStageConfigForMultiSvcMultiEvs() {
    String svcId = "svcId";
    String envId = "envId";
    Map<String, Object> step = Map.of("name", "teststep");

    final DeploymentStageNodeV1 nodeEnvsFilters = buildNode(
        DeploymentStageConfigV1.builder()
            .uuid("stageUuid")
            .service(
                ServiceYamlV2.builder().uuid("serviceuuid").serviceRef(ParameterField.createValueField(svcId)).build())
            .environments(EnvironmentsYaml.builder()
                              .uuid("environments-uuid")
                              .values(ParameterField.createValueField(
                                  List.of(EnvironmentYamlV2.builder()
                                              .uuid("envuuid")
                                              .environmentRef(ParameterField.<String>builder().value(envId).build())
                                              .deployToAll(ParameterField.createValueField(false))
                                              .infrastructureDefinitions(ParameterField.createValueField(
                                                  asList(InfraStructureDefinitionYaml.builder()
                                                             .identifier(ParameterField.createValueField("infra"))
                                                             .build())))
                                              .build())))
                              .filters(ParameterField.createValueField(
                                  List.of(FilterYaml.builder()
                                              .type(FilterType.all)
                                              .entities(Set.of(Entity.environments, Entity.infrastructures))
                                              .build())))
                              .build())
            .steps(List.of(mapper.valueToTree(step)))
            .build());

    final DeploymentStageNodeV1 multiSvcMultienvsNodeWithFilter = buildNode(
        DeploymentStageConfigV1.builder()
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
            .steps(List.of(mapper.valueToTree(step)))
            .build());

    final DeploymentStageNodeV1 multiSvcWithEnvGroupNodeWithFilter = buildNode(
        DeploymentStageConfigV1.builder()
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
                                  .envGroupRef(ParameterField.<String>builder().value("envGroup").build())
                                  .build())
            .steps(List.of(mapper.valueToTree(step)))
            .build());

    final DeploymentStageNodeV1 multiSvcSingleEnv =
        buildNode(DeploymentStageConfigV1.builder()
                      .uuid("stageUuid")
                      .services(ServicesYaml.builder()
                                    .uuid("services-uuid")
                                    .values(ParameterField.createValueField(
                                        Arrays.asList(ServiceYamlV2.builder()
                                                          .uuid("serviceuuid")
                                                          .serviceRef(ParameterField.createValueField(svcId))
                                                          .build())))
                                    .build())
                      .environment(EnvironmentYamlV2.builder()
                                       .uuid("envuuid")
                                       .environmentRef(ParameterField.<String>builder().value(envId).build())
                                       .deployToAll(ParameterField.createValueField(false))
                                       .infrastructureDefinitions(ParameterField.createValueField(
                                           asList(InfraStructureDefinitionYaml.builder()
                                                      .identifier(ParameterField.createValueField("infra"))
                                                      .build())))
                                       .build())
                      .steps(List.of(mapper.valueToTree(step)))
                      .build());

    return new Object[][] {{nodeEnvsFilters}, {multiSvcMultienvsNodeWithFilter}, {multiSvcWithEnvGroupNodeWithFilter},
        {multiSvcSingleEnv}};
  }

  private DeploymentStageNodeV1 buildNode(DeploymentStageConfigV1 config) {
    final DeploymentStageNodeV1 node = DeploymentStageNodeV1.builder().spec(config).build();
    node.setUuid("nodeuuid");
    return node;
  }

  @Test
  @Owner(developers = {VAIBHAV_SI, MLUKIC})
  @Category(UnitTests.class)
  public void testGetIdentifierWithExpressionForGitOps() {
    // Gitops with single service, single env.
    DeploymentStageNodeV1 node =
        DeploymentStageNodeV1.builder().spec(DeploymentStageConfigV1.builder().gitOpsEnabled(true).build()).build();
    PlanCreationContext context =
        PlanCreationContext.builder().currentField(new YamlField("node", new YamlNode(new TextNode("abcc")))).build();

    assertThat(deploymentStagePlanCreator.getIdentifierWithExpression(context, node, "id1")).isEqualTo("id1");

    // Gitops with single service, multi env.
    node = DeploymentStageNodeV1.builder()
               .spec(DeploymentStageConfigV1.builder()
                         .gitOpsEnabled(true)
                         .environments(EnvironmentsYaml.builder().build())
                         .build())
               .build();
    assertThat(deploymentStagePlanCreator.getIdentifierWithExpression(context, node, "id1")).isEqualTo("id1");

    // Gitops with multi service, multi env.
    node = DeploymentStageNodeV1.builder()
               .spec(DeploymentStageConfigV1.builder()
                         .gitOpsEnabled(true)
                         .services(ServicesYaml.builder().build())
                         .environments(EnvironmentsYaml.builder().build())
                         .build())
               .build();
    assertThat(deploymentStagePlanCreator.getIdentifierWithExpression(context, node, "id1"))
        .isEqualTo("id1<+strategy.identifierPostFix>");
  }

  @Test
  @Owner(developers = {TATHAGAT, MLUKIC})
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageFromServicesError_0() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/v1/pipeline.yaml");
    YamlField pipeline = new YamlField(YAMLFieldNameConstants.PIPELINE, YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField(YAMLFieldNameConstants.SPEC, getStageNodeAtIndex(pipeline, 5));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(
            ()
                -> deploymentStagePlanCreator.useServicesYamlFromStage(
                    DeploymentStageConfigV1.builder()
                        .services(ServicesYaml.builder().useFromStage(ServiceUseFromStageV2.builder().build()).build())
                        .build(),
                    specField))
        .withMessageContaining("Stage identifier is empty in useFromStage");
  }

  @Test
  @Owner(developers = {TATHAGAT, MLUKIC})
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageFromServicesError_1() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/v1/pipeline.yaml");
    YamlField pipeline = new YamlField(YAMLFieldNameConstants.PIPELINE, YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField(YAMLFieldNameConstants.SPEC, getStageNodeAtIndex(pipeline, 5));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> deploymentStagePlanCreator.useServicesYamlFromStage(
                            DeploymentStageConfigV1.builder()
                                .services(ServicesYaml.builder()
                                              .useFromStage(ServiceUseFromStageV2.builder().stage("stage2").build())
                                              .build())
                                .build(),
                            specField))
        .withMessageContaining(
            "Could not find multi service configuration in stage [stage2], hence not possible to propagate service from that stage");
  }

  @Test
  @Owner(developers = {TATHAGAT, MLUKIC})
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageFromServicesError_2() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/v1/pipeline.yaml");
    YamlField pipeline = new YamlField(YAMLFieldNameConstants.PIPELINE, YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField(YAMLFieldNameConstants.SPEC, getStageNodeAtIndex(pipeline, 6));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> deploymentStagePlanCreator.useServicesYamlFromStage(
                            DeploymentStageConfigV1.builder()
                                .services(ServicesYaml.builder()
                                              .useFromStage(ServiceUseFromStageV2.builder().stage("random").build())
                                              .build())
                                .build(),
                            specField))
        .withMessageContaining("Stage with identifier [random] given for service propagation does not exist");
  }

  @Test
  @Owner(developers = {TATHAGAT, MLUKIC})
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageFromServicesError_3() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/v1/pipeline.yaml");
    YamlField pipeline = new YamlField(YAMLFieldNameConstants.PIPELINE, YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField(YAMLFieldNameConstants.SPEC, getStageNodeAtIndex(pipeline, 7));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> deploymentStagePlanCreator.useServicesYamlFromStage(
                            DeploymentStageConfigV1.builder()
                                .services(ServicesYaml.builder()
                                              .useFromStage(ServiceUseFromStageV2.builder().stage("stage5").build())
                                              .build())
                                .build(),
                            specField))
        .withMessageContaining(
            "Invalid identifier [stage5] given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
  }

  @Test
  @Owner(developers = {TATHAGAT, MLUKIC})
  @Category(UnitTests.class)
  public void addServiceNodeUseFromStageFromServicesWithMetadata() throws IOException {
    String pipelineYaml = readFileIntoUTF8String("cdng/creator/servicePlanCreator/v1/pipeline.yaml");
    YamlField pipeline = new YamlField(YamlNode.fromYamlPath(pipelineYaml, ""));
    YamlField specField = new YamlField(YAMLFieldNameConstants.SPEC, getStageNodeAtIndex(pipeline, 9));
    ServicesYaml services = deploymentStagePlanCreator.useServicesYamlFromStage(
        DeploymentStageConfigV1.builder()
            .services(
                ServicesYaml.builder()
                    .servicesMetadata(
                        ServicesMetadata.builder().parallel(ParameterField.createValueField(Boolean.TRUE)).build())
                    .useFromStage(ServiceUseFromStageV2.builder().stage("stage7").build())
                    .build())
            .build(),
        specField);
    assertThat(services.getUseFromStage()).isNull();
    assertThat(services.getValues()).isNotNull();
    assertThat(services.getValues()
                   .getValue()
                   .stream()
                   .map(ServiceYamlV2::getServiceRef)
                   .map(ParameterField::getValue)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("service1", "service2");
    assertThat(services.getServicesMetadata().getParallel().getValue()).isEqualTo(Boolean.TRUE);
  }

  @Test
  @Owner(developers = {NAMANG, MLUKIC})
  @Category(UnitTests.class)
  public void testSaveSingleServiceEnvDeploymentStagePlanCreationSummary_NegativeCases() throws InterruptedException {
    PlanCreationContext ctx = PlanCreationContext.builder().build();
    PlanCreationResponse faultyServicePlanCreationResponse =
        PlanCreationResponse.builder()
            .planNode(PlanNode.builder().stepParameters(ServiceStepParameters.builder().build()).build())
            .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode = (DeploymentStageNodeV1) getDeploymentStageConfig()[0][0];
    DeploymentStageNodeV1 multiDeploymentStageNode =
        (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[0][0];
    CountDownLatch latch = new CountDownLatch(7);

    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(null, ctx, deploymentStageNode);
      latch.countDown();
    });

    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          PlanCreationResponse.builder().build(), ctx, deploymentStageNode);
      latch.countDown();
    });

    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          PlanCreationResponse.builder().planNode(PlanNode.builder().build()).build(), ctx, deploymentStageNode);
      latch.countDown();
    });

    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          faultyServicePlanCreationResponse, ctx, deploymentStageNode);
      latch.countDown();
    });

    // multi deployment cases
    // multiSvcMultiEnvsNodeWithFilter
    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          faultyServicePlanCreationResponse, ctx,
          (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[0][0]);
      latch.countDown();
    });

    // multiSvcWithEnvGroupNodeWithFilter
    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          faultyServicePlanCreationResponse, ctx,
          (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[1][0]);
      latch.countDown();
    });

    // nodeEnvsFilters
    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          faultyServicePlanCreationResponse, ctx,
          (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[2][0]);
      latch.countDown();
    });
    assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
    verify(executorService, times(7)).submit(any(Runnable.class));
    verifyNoMoreInteractions(deploymentStagePlanCreationInfoService);
  }

  @Test
  @Owner(developers = {NAMANG, MLUKIC})
  @Category(UnitTests.class)
  public void testSaveSingleServiceEnvDeploymentStagePlanCreationSummary() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                                   .setExecutionUuid("planExeId")
                                                                   .setPipelineIdentifier("pipelineId")
                                                                   .build())
                                          .build()))
                                  .build();
    PlanCreationResponse servicePlanCreationResponse =
        PlanCreationResponse.builder()
            .planNode(PlanNode.builder()
                          .stepParameters(ServiceStepV3Parameters.builder()
                                              .envRef(ParameterField.createValueField("acc.env"))
                                              .infraId(ParameterField.createValueField("acc.infra"))
                                              .serviceRef(ParameterField.createValueField("acc.ser"))
                                              .deploymentType(KUBERNETES)
                                              .build())
                          .build())
            .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode = (DeploymentStageNodeV1) getDeploymentStageConfig()[0][0];
    deploymentStageNode.setId("stageId");
    deploymentStageNode.setName("stage Name");
    executorService.submit(() -> {
      deploymentStagePlanCreator.saveSingleServiceEnvDeploymentStagePlanCreationSummary(
          servicePlanCreationResponse, ctx, deploymentStageNode);
      latch.countDown();
    });

    verify(executorService, times(2)).submit(any(Runnable.class));
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    verify(deploymentStagePlanCreationInfoService, times(1))
        .save(DeploymentStagePlanCreationInfo.builder()
                  .planExecutionId("planExeId")
                  .accountIdentifier("accountId")
                  .orgIdentifier("orgId")
                  .projectIdentifier("projId")
                  .pipelineIdentifier("pipelineId")
                  .stageType(DeploymentStageType.SINGLE_SERVICE_ENVIRONMENT)
                  .deploymentType(KUBERNETES)
                  .stageIdentifier("stageId")
                  .stageName("stage Name")
                  .deploymentStageDetailsInfo(SingleServiceEnvDeploymentStageDetailsInfo.builder()
                                                  .envIdentifier("acc.env")
                                                  .serviceIdentifier("acc.ser")
                                                  .infraIdentifier("acc.infra")
                                                  .build())
                  .build());
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
  @Category(UnitTests.class)
  public void testMultiServiceSingleEnvDeploymentStagePlanCreationSummary() throws InterruptedException {
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                                   .setExecutionUuid("planExeId")
                                                                   .setPipelineIdentifier("pipelineId")
                                                                   .build())
                                          .build()))
                                  .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode =
        (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[3][0];
    deploymentStageNode.setId("stageId");
    deploymentStageNode.setName("stage Name");
    // Dummy spec Node.
    deploymentStagePlanCreator.saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(
        ctx, deploymentStageNode, new YamlField("node", new YamlNode(new TextNode("abcc"))));

    verify(deploymentStagePlanCreationInfoService, times(1))
        .save(DeploymentStagePlanCreationInfo.builder()
                  .planExecutionId("planExeId")
                  .accountIdentifier("accountId")
                  .orgIdentifier("orgId")
                  .projectIdentifier("projId")
                  .pipelineIdentifier("pipelineId")
                  .stageType(DeploymentStageType.MULTI_SERVICE_ENVIRONMENT)
                  .stageIdentifier("stageId")
                  .stageName("stage Name")
                  .deploymentStageDetailsInfo(
                      MultiServiceEnvDeploymentStageDetailsInfo.builder()
                          .envIdentifiers(asList("envId").stream().collect(Collectors.toSet()))
                          .serviceIdentifiers(asList("svcId").stream().collect(Collectors.toSet()))
                          .infraIdentifiers(asList("infra").stream().collect(Collectors.toSet()))
                          .build())
                  .build());
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
  @Category(UnitTests.class)
  public void testSingleServiceMultiEnvDeploymentStagePlanCreationSummary() throws InterruptedException {
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                                   .setExecutionUuid("planExeId")
                                                                   .setPipelineIdentifier("pipelineId")
                                                                   .build())
                                          .build()))
                                  .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode =
        (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[0][0];
    deploymentStageNode.setId("stageId");
    deploymentStageNode.setName("stage Name");
    // Dummy spec Node.
    deploymentStagePlanCreator.saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(
        ctx, deploymentStageNode, new YamlField("node", new YamlNode(new TextNode("abcc"))));

    verify(deploymentStagePlanCreationInfoService, times(1))
        .save(DeploymentStagePlanCreationInfo.builder()
                  .planExecutionId("planExeId")
                  .accountIdentifier("accountId")
                  .orgIdentifier("orgId")
                  .projectIdentifier("projId")
                  .pipelineIdentifier("pipelineId")
                  .stageType(DeploymentStageType.MULTI_SERVICE_ENVIRONMENT)
                  .stageIdentifier("stageId")
                  .stageName("stage Name")
                  .deploymentStageDetailsInfo(
                      MultiServiceEnvDeploymentStageDetailsInfo.builder()
                          .envIdentifiers(asList("envId").stream().collect(Collectors.toSet()))
                          .serviceIdentifiers(asList("svcId").stream().collect(Collectors.toSet()))
                          .infraIdentifiers(asList("infra").stream().collect(Collectors.toSet()))
                          .build())
                  .build());
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
  @Category(UnitTests.class)
  public void testMultiServiceMultiEnvDeploymentStagePlanCreationSummary() throws InterruptedException {
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                                   .setExecutionUuid("planExeId")
                                                                   .setPipelineIdentifier("pipelineId")
                                                                   .build())
                                          .build()))
                                  .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode =
        (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[1][0];
    deploymentStageNode.setId("stageId");
    deploymentStageNode.setName("stage Name");
    // Dummy spec Node.
    deploymentStagePlanCreator.saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(
        ctx, deploymentStageNode, new YamlField("node", new YamlNode(new TextNode("abcc"))));

    verify(deploymentStagePlanCreationInfoService, times(1))
        .save(DeploymentStagePlanCreationInfo.builder()
                  .planExecutionId("planExeId")
                  .accountIdentifier("accountId")
                  .orgIdentifier("orgId")
                  .projectIdentifier("projId")
                  .pipelineIdentifier("pipelineId")
                  .stageType(DeploymentStageType.MULTI_SERVICE_ENVIRONMENT)
                  .stageIdentifier("stageId")
                  .stageName("stage Name")
                  .deploymentStageDetailsInfo(
                      MultiServiceEnvDeploymentStageDetailsInfo.builder()
                          .envIdentifiers(asList("envId").stream().collect(Collectors.toSet()))
                          .serviceIdentifiers(asList("svcId").stream().collect(Collectors.toSet()))
                          .infraIdentifiers(asList("infra").stream().collect(Collectors.toSet()))
                          .build())
                  .build());
  }

  @Test
  @Owner(developers = {ABHINAV_MITTAL, MLUKIC})
  @Category(UnitTests.class)
  public void testEnvGroupDeploymentStagePlanCreationSummary() throws InterruptedException {
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder()
                                          .setAccountIdentifier("accountId")
                                          .setOrgIdentifier("orgId")
                                          .setProjectIdentifier("projId")
                                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                                   .setExecutionUuid("planExeId")
                                                                   .setPipelineIdentifier("pipelineId")
                                                                   .build())
                                          .build()))
                                  .build();

    // plan creation response cases
    DeploymentStageNodeV1 deploymentStageNode =
        (DeploymentStageNodeV1) getDeploymentStageConfigForMultiSvcMultiEvs()[2][0];
    deploymentStageNode.setId("stageId");
    deploymentStageNode.setName("stage Name");
    // Dummy spec Node.
    deploymentStagePlanCreator.saveDeploymentStagePlanCreationSummaryForMultiServiceMultiEnv(
        ctx, deploymentStageNode, new YamlField("node", new YamlNode(new TextNode("abcc"))));

    verify(deploymentStagePlanCreationInfoService, times(1))
        .save(DeploymentStagePlanCreationInfo.builder()
                  .planExecutionId("planExeId")
                  .accountIdentifier("accountId")
                  .orgIdentifier("orgId")
                  .projectIdentifier("projId")
                  .pipelineIdentifier("pipelineId")
                  .stageType(DeploymentStageType.MULTI_SERVICE_ENVIRONMENT)
                  .stageIdentifier("stageId")
                  .stageName("stage Name")
                  .deploymentStageDetailsInfo(
                      MultiServiceEnvDeploymentStageDetailsInfo.builder()
                          .envIdentifiers(asList("envId").stream().collect(Collectors.toSet()))
                          .serviceIdentifiers(asList("svcId").stream().collect(Collectors.toSet()))
                          .infraIdentifiers(asList("infra").stream().collect(Collectors.toSet()))
                          .envGroup("envGroup")
                          .build())
                  .build());
  }

  private static YamlNode getStageNodeAtIndex(YamlField pipeline, int idx) {
    return pipeline.getNode()
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode()
        .getField(YAMLFieldNameConstants.STAGES)
        .getNode()
        .asArray()
        .get(idx)
        .getField(YAMLFieldNameConstants.SPEC)
        .getNode();
  }
}
