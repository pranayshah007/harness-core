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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
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
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class DeploymentStagePMSPlanCreatorV2Test extends CDNGTestBase {
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private KryoSerializer kryoSerializer;
  @InjectMocks private DeploymentStagePMSPlanCreatorV2 deploymentStagePMSPlanCreator;

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
  public void testCreatePlanForChildrenNodes_0(DeploymentStageNode node) {
    doReturn(true).when(featureFlagHelperService).isEnabled(Mockito.anyString(), eq(FeatureName.SERVICE_V2_EXPRESSION));
    node.setFailureStrategies(List.of(FailureStrategyConfig.builder()
                                          .onFailure(OnFailureConfig.builder()
                                                         .errors(List.of(NGFailureType.ALL_ERRORS))
                                                         .action(AbortFailureActionConfig.builder().build())
                                                         .build())
                                          .build()));

    JsonNode jsonNode = mapper.valueToTree(node);
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(Map.of("metadata",
                                      PlanCreationContextValue.newBuilder().setAccountIdentifier("accountId").build()))
                                  .currentField(new YamlField(new YamlNode("spec", jsonNode)))
                                  .build();
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap =
        deploymentStagePMSPlanCreator.createPlanForChildrenNodes(ctx, node);

    assertThat(planCreationResponseMap).hasSize(8);
    assertThat(planCreationResponseMap.values()
                   .stream()
                   .map(PlanCreationResponse::getPlanNode)
                   .filter(Objects::nonNull)
                   .map(PlanNode::getIdentifier)
                   .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("service", "infrastructure", "artifacts", "manifests", "configFiles");
  }

  private Object[][] getDeploymentStageConfig() {
    String svcId = "svcId";
    String envId = "envId";
    String envName = "envName";
    String svcName = "svcName";
    Map<String, Object> step = Map.of("name", "teststep");

    final DeploymentStageNode node1 = buildNode(
        DeploymentStageConfig.builder()
            .uuid("stageUuid")
            .service(
                ServiceYamlV2.builder().uuid("serviceuuid").serviceRef(ParameterField.createValueField(svcId)).build())
            .environment(EnvironmentYamlV2.builder()
                             .uuid("envuuid")
                             .environmentRef(ParameterField.<String>builder().value(envId).build())
                             .deployToAll(ParameterField.createValueField(false))
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

  private DeploymentStageNode buildNode(DeploymentStageConfig config) {
    final DeploymentStageNode node = new DeploymentStageNode();
    node.setUuid("nodeuuid");
    node.setDeploymentStageConfig(config);
    return node;
  }
}
