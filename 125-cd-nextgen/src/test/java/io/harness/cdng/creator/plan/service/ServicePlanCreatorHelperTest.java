/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
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
            .build());
    assertThat(dependencies).isNotNull();
    assertThat(dependencies.getDependenciesMap().containsKey(serviceNodeId)).isTrue();
    assertThat(dependencies.getDependencyMetadataMap()
                   .get(serviceNodeId)
                   .containsMetadata(YamlTypes.INFRASTRUCTURE_STEP_PARAMETERS))
        .isTrue();
    assertThat(
        dependencies.getDependencyMetadataMap().get(serviceNodeId).containsMetadata(YamlTypes.ENVIRONMENT_NODE_ID))
        .isTrue();
  }

  private YamlField getYamlFieldFromPath(String path) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(path);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    return YamlUtils.readTree(yaml);
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
