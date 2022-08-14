/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode.StepType;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServicePlanCreatorHelperTest extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;
  @Mock ServiceEntityService serviceEntityService;
  private static final String ACCOUNT_ID = "account_id";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJ_IDENTIFIER = "projId";
  private static final String SERVICE_IDENTIFIER = "service1";

  private static final String RESOURCE_PATH = "cdng/plan/service/";

  @InjectMocks private ServicePlanCreatorHelper servicePlanCreatorHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDependenciesForServiceForV1() throws IOException {
    YamlField serviceField = getYamlFieldFromPath("cdng/plan/service.yml");

    String serviceNodeId = serviceField.getNode().getUuid();
    byte[] dummyValue = new byte[10];
    doReturn(dummyValue).when(kryoSerializer).asDeflatedBytes(any());
    Dependencies dependencies = servicePlanCreatorHelper.getDependenciesForService(serviceField,
        DeploymentStageNode.builder()
            .deploymentStageConfig(DeploymentStageConfig.builder()
                                       .serviceConfig(ServiceConfig.builder().build())
                                       .infrastructure(PipelineInfrastructure.builder().build())
                                       .build())
            .build(),
        "environmentUuid", "infraSectionUuid");
    assertThat(dependencies).isNotEqualTo(null);
    assertThat(dependencies.getDependenciesMap().containsKey(serviceNodeId)).isEqualTo(true);
    assertThat(dependencies.getDependencyMetadataMap()
                   .get(serviceNodeId)
                   .containsMetadata(YamlTypes.INFRASTRUCTURE_STEP_PARAMETERS))
        .isEqualTo(true);
    assertThat(
        dependencies.getDependencyMetadataMap().get(serviceNodeId).containsMetadata(YamlTypes.ENVIRONMENT_NODE_ID))
        .isEqualTo(true);
  }

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetResolvedServiceFieldForV2() throws IOException {
    DeploymentStageNode stageNode =
        DeploymentStageNode.builder()
            .type(StepType.Deployment)
            .deploymentStageConfig(
                DeploymentStageConfig.builder()
                    .service(ServiceYamlV2.builder().serviceRef(ParameterField.createValueField("service1")).build())
                    .build())
            .build();
    stageNode.setIdentifier("stage1");
    Map<String, PlanCreationContextValue> globalContext = new HashMap<>();
    globalContext.put("metadata",
        PlanCreationContextValue.newBuilder()
            .setAccountIdentifier(ACCOUNT_ID)
            .setOrgIdentifier(ORG_IDENTIFIER)
            .setProjectIdentifier(PROJ_IDENTIFIER)
            .build());
    PlanCreationContext context = PlanCreationContext.builder().globalContext(globalContext).build();

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("cdng/serviceV2.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();
    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();
    // Stage1 spec Node
    YamlField specField = stage1Node.getField("spec");

    String serviceYaml = "service:\n"
        + "    name: service1\n"
        + "    identifier: service1\n"
        + "    tags: {}\n"
        + "    gitOpsEnabled: false\n"
        + "    serviceDefinition:\n"
        + "        spec:\n"
        + "            variables:\n"
        + "                - name: var1\n"
        + "                  type: String\n"
        + "                  value: value1\n"
        + "                - name: var2\n"
        + "                  type: String\n"
        + "                  value: value2\n"
        + "        type: Kubernetes\n"
        + "    description: \"\"\n";

    ServiceEntity serviceEntity =
        ServiceEntity.builder().name(SERVICE_IDENTIFIER).name(SERVICE_IDENTIFIER).yaml(serviceYaml).build();
    when(serviceEntityService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(Optional.of(serviceEntity));
    YamlField serviceFieldForV2 =
        servicePlanCreatorHelper.getResolvedServiceFieldForV2(null, stageNode, specField, context);
    assertThat(serviceFieldForV2).isNotNull();
    assertThat(serviceFieldForV2.getNode().getField(YamlTypes.SERVICE_DEFINITION)).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetResolvedServiceFieldForV2WithServiceInputAsRuntime() throws IOException {
    DeploymentStageNode stageNode =
        DeploymentStageNode.builder()
            .type(StepType.Deployment)
            .deploymentStageConfig(
                DeploymentStageConfig.builder()
                    .service(ServiceYamlV2.builder()
                                 .serviceRef(ParameterField.createValueField("service1"))
                                 .serviceInputs(ParameterField.createExpressionField(true, "<+input>", null, true))
                                 .build())
                    .build())
            .build();
    stageNode.setIdentifier("stage1");
    Map<String, PlanCreationContextValue> globalContext = new HashMap<>();
    globalContext.put("metadata",
        PlanCreationContextValue.newBuilder()
            .setAccountIdentifier(ACCOUNT_ID)
            .setOrgIdentifier(ORG_IDENTIFIER)
            .setProjectIdentifier(PROJ_IDENTIFIER)
            .build());
    PlanCreationContext context = PlanCreationContext.builder().globalContext(globalContext).build();

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("cdng/serviceV2WithServiceInputAsRuntime.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();
    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();
    // Stage1 spec Node
    YamlField specField = stage1Node.getField("spec");

    String serviceYaml = "service:\n"
        + "    name: service1\n"
        + "    identifier: service1\n"
        + "    tags: {}\n"
        + "    gitOpsEnabled: false\n"
        + "    serviceDefinition:\n"
        + "        spec:\n"
        + "            variables:\n"
        + "                - name: var1\n"
        + "                  type: String\n"
        + "                  value: value1\n"
        + "                - name: var2\n"
        + "                  type: String\n"
        + "                  value: value2\n"
        + "        type: Kubernetes\n"
        + "    description: \"\"\n";

    ServiceEntity serviceEntity =
        ServiceEntity.builder().name(SERVICE_IDENTIFIER).name(SERVICE_IDENTIFIER).yaml(serviceYaml).build();
    when(serviceEntityService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, SERVICE_IDENTIFIER, false))
        .thenReturn(Optional.of(serviceEntity));
    YamlField serviceFieldForV2 =
        servicePlanCreatorHelper.getResolvedServiceFieldForV2(null, stageNode, specField, context);
    assertThat(serviceFieldForV2).isNotNull();
    assertThat(serviceFieldForV2.getNode().getField(YamlTypes.SERVICE_DEFINITION)).isNotNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testModifyArtifactsWithFixedPrimaryArtifactRefInYaml() throws IOException {
    String yaml = readFile("serviceV2WithFixedPrimaryArtifactRef.yaml");
    YamlField serviceFieldForV2 = servicePlanCreatorHelper.validateAndModifyArtifactsInYaml(null, null, yaml);
    assertThat(serviceFieldForV2).isNotNull();
    removeUuid(serviceFieldForV2.getNode().getCurrJsonNode());
    String finalYaml = YamlPipelineUtils.writeYamlString(serviceFieldForV2.getNode().getCurrJsonNode());
    assertThat(finalYaml).isNotEmpty();

    String modifiedYaml = readFile("modifiedServiceV2WithFixedPrimaryArtifactRef.yaml");
    assertThat(finalYaml).isEqualTo(modifiedYaml);
    NGServiceConfig config = YamlPipelineUtils.read(finalYaml, NGServiceConfig.class);
    assertThat(config).isNotNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testModifyArtifactsWithIncorrectPrimaryArtifactRefInYaml() throws IOException {
    String yaml = readFile("serviceV2WithIncorrectPrimaryArtifactRef.yaml");
    DeploymentStageNode deploymentStageNode = DeploymentStageNode.builder().build();
    deploymentStageNode.setIdentifier("stage1");
    assertThatThrownBy(
        () -> servicePlanCreatorHelper.validateAndModifyArtifactsInYaml(deploymentStageNode, SERVICE_IDENTIFIER, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "No artifact source exists with the identifier i5 inside service service1 of DeploymentStage - stage1");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testModifyArtifactsWithPrimaryArtifactRefAsExpressionInYaml() throws IOException {
    String yaml = readFile("serviceV2WithPrimaryArtifactRefAsExpression.yaml");
    DeploymentStageNode deploymentStageNode = DeploymentStageNode.builder().build();
    deploymentStageNode.setIdentifier("stage1");
    assertThatThrownBy(
        () -> servicePlanCreatorHelper.validateAndModifyArtifactsInYaml(deploymentStageNode, SERVICE_IDENTIFIER, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Primary artifact ref cannot be runtime or expression inside service service1 of DeploymentStage - stage1");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testModifyArtifactsWithPrimaryAndSidecarArtifactsInYaml() throws IOException {
    String yaml = readFile("modifiedServiceV2WithFixedPrimaryArtifactRef.yaml");
    YamlField serviceFieldForV2 = servicePlanCreatorHelper.validateAndModifyArtifactsInYaml(null, null, yaml);
    assertThat(serviceFieldForV2).isNotNull();
    removeUuid(serviceFieldForV2.getNode().getCurrJsonNode());
    String finalYaml = YamlPipelineUtils.writeYamlString(serviceFieldForV2.getNode().getCurrJsonNode());
    assertThat(finalYaml).isNotEmpty();

    assertThat(finalYaml).isEqualTo(yaml);
    NGServiceConfig config = YamlPipelineUtils.read(finalYaml, NGServiceConfig.class);
    assertThat(config).isNotNull();
  }

  private String readFile(String filename) {
    filename = RESOURCE_PATH + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  public void removeUuid(JsonNode node) {
    if (node.isObject()) {
      removeUuidInObject(node);
    } else if (node.isArray()) {
      removeUuidInArray(node);
    }
  }

  private void removeUuidInObject(JsonNode node) {
    ObjectNode objectNode = (ObjectNode) node;
    if (objectNode.has(YamlNode.UUID_FIELD_NAME)) {
      objectNode.remove(YamlNode.UUID_FIELD_NAME);
    } else {
      throw new InvalidRequestException("Uuid is not present at object node");
    }
    for (Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext();) {
      Map.Entry<String, JsonNode> field = it.next();
      removeUuid(field.getValue());
    }
  }

  private void removeUuidInArray(JsonNode node) {
    ArrayNode arrayNode = (ArrayNode) node;
    for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext();) {
      removeUuid(it.next());
    }
  }
}
