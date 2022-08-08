/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.yaml.schema.beans.SchemaConstants.CONST_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ENUM_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REQUIRED_NODE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.rule.Owner;
import io.harness.yaml.TestClass;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.DX)
public class YamlSchemaProviderTest extends CategoryTest {
  YamlSchemaProvider yamlSchemaProvider;
  String schema;

  @Before
  public void setup() throws IOException {
    initMocks(this);
    // this is just for initialization
    final List<YamlSchemaRootClass> yamlSchemaRootClasses =
        Collections.singletonList(YamlSchemaRootClass.builder()
                                      .entityType(EntityType.SECRETS)
                                      .availableAtProjectLevel(true)
                                      .availableAtOrgLevel(true)
                                      .availableAtAccountLevel(true)
                                      .clazz(TestClass.ClassWhichContainsInterface.class)
                                      .build());
    ObjectMapper objectMapper = new ObjectMapper();
    YamlSchemaHelper yamlSchemaHelper = Mockito.spy(new YamlSchemaHelper(yamlSchemaRootClasses));
    yamlSchemaProvider = new YamlSchemaProvider(yamlSchemaHelper);
    schema = getResource("testSchema/sampleSchema.json");
    YamlSchemaGenerator yamlSchemaGenerator = new YamlSchemaGenerator(
        new JacksonClassHelper(objectMapper), new SwaggerGenerator(Jackson.newObjectMapper()), yamlSchemaRootClasses);
    Map<EntityType, JsonNode> entityTypeJsonNodeMap = yamlSchemaGenerator.generateYamlSchema();
    yamlSchemaHelper.initializeSchemaMaps(entityTypeJsonNodeMap);
    doReturn(YamlSchemaWithDetails.builder()
                 .isAvailableAtAccountLevel(true)
                 .isAvailableAtOrgLevel(true)
                 .isAvailableAtProjectLevel(true)
                 .schema(objectMapper.readTree(schema))
                 .build())
        .when(yamlSchemaHelper)
        .getSchemaDetailsForEntityType(EntityType.CONNECTORS);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlSchema() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode yamlSchema = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, null);
    final JsonNode yamlSchema_1 = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, "abc", null, Scope.ORG);
    final JsonNode yamlSchema_2 = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, "abc", "xyz", Scope.PROJECT);
    final JsonNode yamlSchema_3 = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, Scope.PROJECT);
    final JsonNode yamlSchema_4 = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, Scope.ORG);
    final JsonNode yamlSchema_5 = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, Scope.ACCOUNT);

    assertThat(yamlSchema).isNotNull();
    assertThat(yamlSchema).isEqualTo(objectMapper.readTree(schema));
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_1))
                   .get(ORG_KEY)
                   .get(CONST_NODE)
                   .textValue())
        .isEqualTo("abc");
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_1))
                   .get(PROJECT_KEY))
        .isNull();
    assertRequiredNode(yamlSchema_1, ORG_KEY);
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_2))
                   .get(ORG_KEY)
                   .get(CONST_NODE)
                   .textValue())
        .isEqualTo("abc");
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_2))
                   .get(PROJECT_KEY)
                   .get(CONST_NODE)
                   .textValue())
        .isEqualTo("xyz");
    assertRequiredNode(yamlSchema_2, ORG_KEY);
    assertRequiredNode(yamlSchema_2, PROJECT_KEY);
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_3))
                   .get(ORG_KEY))
        .isNotNull();
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_3))
                   .get(PROJECT_KEY))
        .isNotNull();
    assertRequiredNode(yamlSchema_3, ORG_KEY);
    assertRequiredNode(yamlSchema_3, PROJECT_KEY);
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_4))
                   .get(ORG_KEY))
        .isNotNull();
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_4))
                   .get(PROJECT_KEY))
        .isNull();
    assertRequiredNode(yamlSchema_4, ORG_KEY);
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_5))
                   .get(ORG_KEY))
        .isNull();
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema_5))
                   .get(PROJECT_KEY))
        .isNull();
  }

  private void assertRequiredNode(JsonNode yamlschema, String key) {
    AtomicBoolean requiredStatus = new AtomicBoolean(false);
    yamlSchemaProvider.getSecondLevelNode(yamlschema).get(REQUIRED_NODE).iterator().forEachRemaining(requiredNode -> {
      requiredStatus.set(requiredStatus.get() || requiredNode.textValue().equals(key));
    });
    assertThat(requiredStatus.get()).isTrue();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlSchemaWithFieldSetAtSecondLevel() {
    JsonNode yamlSchema = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, null);
    yamlSchema = yamlSchemaProvider.updateArrayFieldAtSecondLevelInSchema(yamlSchema, "type", ENUM_NODE, "XYZ");
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema))
                   .get("type")
                   .get(ENUM_NODE)
                   .elements()
                   .next()
                   .textValue())
        .isEqualTo("XYZ");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testUpsertInObjectFieldAtSecondLevelInSchema() {
    JsonNode yamlSchema = yamlSchemaProvider.getYamlSchema(EntityType.CONNECTORS, null, null, null);
    yamlSchema =
        yamlSchemaProvider.upsertInObjectFieldAtSecondLevelInSchema(yamlSchema, "identifier", CONST_NODE, "id");
    assertThat(yamlSchemaProvider.getSecondLevelNodeProperties(yamlSchemaProvider.getSecondLevelNode(yamlSchema))
                   .get("identifier")
                   .get(CONST_NODE)
                   .textValue())
        .isEqualTo("id");
  }

  private String getResource(String resource) throws IOException {
    return IOUtils.resourceToString(resource, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
