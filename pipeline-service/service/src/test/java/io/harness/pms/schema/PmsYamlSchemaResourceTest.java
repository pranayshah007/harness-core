/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.TRIGGERS;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.EntityType;
import io.harness.PipelineServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import javax.ws.rs.NotSupportedException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PmsYamlSchemaResourceTest extends PipelineServiceTestBase {
  @InjectMocks PmsYamlSchemaResourceImpl pmsYamlSchemaResource;
  @Mock PMSYamlSchemaService pmsYamlSchemaService;
  String project = "project";
  String org = "org";
  String acc = "acc";
  String id = "id";

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetYamlSchema() {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode textNode1 = objectMapper.createObjectNode();
    textNode1.set("myObject", new TextNode("placeholder1"));
    doReturn(textNode1)
        .when(pmsYamlSchemaService)
        .getStaticSchemaForAllEntities(YAMLFieldNameConstants.PIPELINE, "", "", "v0");
    ResponseDTO<JsonNode> yamlSchema1 =
        pmsYamlSchemaResource.getYamlSchema(PIPELINES, project, org, Scope.PROJECT, id, acc);
    assertThat(yamlSchema1.getData()).isEqualTo(textNode1);

    ObjectNode textNode2 = objectMapper.createObjectNode();
    textNode1.set("myObject", new TextNode("placeholder2"));
    doReturn(textNode2)
        .when(pmsYamlSchemaService)
        .getStaticSchemaForAllEntities(YAMLFieldNameConstants.TRIGGER, "", "", "v0");

    ResponseDTO<JsonNode> yamlSchema2 =
        pmsYamlSchemaResource.getYamlSchema(TRIGGERS, project, org, Scope.PROJECT, id, acc);
    assertThat(yamlSchema2.getData()).isEqualTo(textNode2);

    for (EntityType value : EntityType.values()) {
      if (value.equals(PIPELINES) || value.equals(TRIGGERS)) {
        continue;
      }
      assertThatThrownBy(() -> pmsYamlSchemaResource.getYamlSchema(value, project, org, Scope.PROJECT, id, acc))
          .isInstanceOf(NotSupportedException.class);
    }
  }
}
