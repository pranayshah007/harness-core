/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.TestClass;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class YamlSchemaHelperTest extends CategoryTest {
  YamlSchemaHelper yamlSchemaHelper;

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void initializeSchemaMapAndGetSchema() throws IOException {
    final List<YamlSchemaRootClass> yamlSchemaRootClasses =
        Arrays.asList(YamlSchemaRootClass.builder()
                          .entityType(EntityType.CONNECTORS)
                          .clazz(TestClass.ClassWhichContainsInterface.class)
                          .build());

    yamlSchemaHelper = new YamlSchemaHelper(yamlSchemaRootClasses);
    String schema = getResource("testSchema/testOutputSchema.json");
    ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode jsonNode = objectMapper.readTree(schema);

    YamlSchemaGenerator yamlSchemaGenerator = new YamlSchemaGenerator(
        new JacksonClassHelper(objectMapper), new SwaggerGenerator(Jackson.newObjectMapper()), yamlSchemaRootClasses);
    Map<EntityType, JsonNode> entityTypeJsonNodeMap = yamlSchemaGenerator.generateYamlSchema();
    yamlSchemaHelper.initializeSchemaMaps(entityTypeJsonNodeMap);
    final YamlSchemaWithDetails schemaForEntityType =
        yamlSchemaHelper.getSchemaDetailsForEntityType(EntityType.CONNECTORS);
    assertThat(schemaForEntityType).isNotNull();
    assertThat(schemaForEntityType.getSchema()).isEqualTo(jsonNode);
  }

  private String getResource(String resource) throws IOException {
    return IOUtils.resourceToString(resource, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
