/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.TestClass;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlSchemaUtilsTest extends CategoryTest {
  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetSwaggerName() {
    final String swaggerName = YamlSchemaUtils.getSwaggerName(TestClass.ClassWithApiModelOverride.class);
    final String swaggerName1 = YamlSchemaUtils.getSwaggerName(TestClass.ClassWithoutApiModelOverride.class);
    assertThat(swaggerName).isEqualTo("testName");
    assertThat(swaggerName1).isEqualTo("ClassWithoutApiModelOverride");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetSchemaPathForEntityType() {
    final String schemaPath = YamlSchemaUtils.getSchemaPathForEntityType(EntityType.CONNECTORS, "abc");
    assertThat(schemaPath).isEqualTo("abc/" + EntityType.CONNECTORS.getYamlName() + "/all.json");
    final String schemaPath1 = YamlSchemaUtils.getSchemaPathForEntityType(EntityType.CONNECTORS, "");
    assertThat(schemaPath1).isEqualTo(EntityType.CONNECTORS.getYamlName() + "/all.json");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetFieldName() {
    final Field[] declaredFields = TestClass.ClassWithApiModelOverride.class.getDeclaredFields();
    final Set<String> result = Arrays.stream(declaredFields)
                                   .map(YamlSchemaUtils::getFieldName)
                                   .filter(field -> !field.equals("$jacocoData"))
                                   .collect(Collectors.toSet());
    assertThat(result).containsExactlyInAnyOrder("a", "testString", "b", "apimodelproperty", "jsontypeinfo");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateByFeatureFlags() {
    YamlSchemaMetadata yamlSchemaMetadata = YamlSchemaMetadata.builder().build();
    Set<String> enabledFeatureFlags = new HashSet<>();
    assertTrue(YamlSchemaUtils.validateByFeatureFlags(yamlSchemaMetadata, enabledFeatureFlags));
    yamlSchemaMetadata.setFeatureFlags(ImmutableList.of("FF1"));
    assertFalse(YamlSchemaUtils.validateByFeatureFlags(yamlSchemaMetadata, enabledFeatureFlags));
    enabledFeatureFlags.add("FF1");
    assertTrue(YamlSchemaUtils.validateByFeatureFlags(yamlSchemaMetadata, enabledFeatureFlags));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateByFeatureRestrictions() {
    YamlSchemaMetadata yamlSchemaMetadata = YamlSchemaMetadata.builder().build();
    Map<String, Boolean> featureRestrictionsMap = new HashMap<>();
    assertTrue(YamlSchemaUtils.validateByFeatureRestrictions(yamlSchemaMetadata, featureRestrictionsMap));
    yamlSchemaMetadata.setFeatureRestrictions(ImmutableList.of("TEST1"));
    assertFalse(YamlSchemaUtils.validateByFeatureRestrictions(yamlSchemaMetadata, featureRestrictionsMap));
    featureRestrictionsMap.put("TEST1", false);
    assertFalse(YamlSchemaUtils.validateByFeatureRestrictions(yamlSchemaMetadata, featureRestrictionsMap));
    featureRestrictionsMap.put("TEST1", true);
    assertTrue(YamlSchemaUtils.validateByFeatureRestrictions(yamlSchemaMetadata, featureRestrictionsMap));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateSchemaMetadata() {
    YamlSchemaWithDetails yamlSchemaWithDetails = YamlSchemaWithDetails.builder().build();
    ModuleType moduleType = ModuleType.CD;
    Set<String> enabledFeatureFlags = new HashSet<>();
    Map<String, Boolean> featureRestrictionsMap = new HashMap<>();
    // Should return false because moduleType CD is not supported by step.
    assertFalse(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    yamlSchemaWithDetails.setYamlSchemaMetadata(YamlSchemaMetadata.builder().build());
    assertFalse(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    // Adding supportedModule in YamlSchemaMetadata, so it should return true on basis of moduleType.
    yamlSchemaWithDetails.getYamlSchemaMetadata().setModulesSupported(Collections.singletonList(ModuleType.CD));
    assertTrue(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    // Adding ff requirement for step. Should return false.
    yamlSchemaWithDetails.getYamlSchemaMetadata().setFeatureFlags(Collections.singletonList("FF1"));
    assertFalse(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    // Enabling the required ff.
    enabledFeatureFlags.add("FF1");
    assertTrue(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    // Adding feature restrictions requirement for step.
    yamlSchemaWithDetails.getYamlSchemaMetadata().setFeatureRestrictions(Collections.singletonList("TEST1"));
    assertFalse(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
    // Enabling the feature restriction.
    featureRestrictionsMap.put("TEST1", true);
    assertTrue(YamlSchemaUtils.validateSchemaMetadata(
        yamlSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNamespaceFromModuleType() {
    assertEquals(YamlSchemaUtils.getNamespaceFromModuleType(ModuleType.CD), "cd/");
    assertEquals(YamlSchemaUtils.getNamespaceFromModuleType(ModuleType.CI), "ci/");
    assertEquals(YamlSchemaUtils.getNamespaceFromModuleType(ModuleType.CE), "ce/");
    assertEquals(YamlSchemaUtils.getNamespaceFromModuleType(ModuleType.CF), "cf/");
    assertEquals(YamlSchemaUtils.getNamespaceFromModuleType(ModuleType.PMS), "");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNodeEntityTypesByYamlGroup() {
    List<YamlSchemaRootClass> yamlSchemaRootClasses = new ArrayList<>();
    assertTrue(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STEP").isEmpty());
    yamlSchemaRootClasses.add(YamlSchemaRootClass.builder().build());
    assertTrue(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STEP").isEmpty());
    yamlSchemaRootClasses.add(
        YamlSchemaRootClass.builder()
            .entityType(EntityType.HTTP_STEP)
            .yamlSchemaMetadata(
                YamlSchemaMetadata.builder().yamlGroup(YamlGroup.builder().group("STAGE").build()).build())
            .build());
    assertTrue(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STEP").isEmpty());
    assertEquals(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STAGE").size(), 1);
    assertEquals(
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STAGE").get(0), EntityType.HTTP_STEP);
    yamlSchemaRootClasses.add(
        YamlSchemaRootClass.builder()
            .entityType(EntityType.SHELL_SCRIPT_STEP)
            .yamlSchemaMetadata(
                YamlSchemaMetadata.builder().yamlGroup(YamlGroup.builder().group("STEP").build()).build())
            .build());
    assertEquals(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STEP").size(), 1);
    assertEquals(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, "STEP").get(0),
        EntityType.SHELL_SCRIPT_STEP);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddOneOfInExecutionWrapperConfigWithoutValidation() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode schema = objectMapper.readTree(getResource("testSchema/ExecutionWrapperConfigSchema.json"));
    Set<Class<?>> yamlSchemaSubtypes = new HashSet<>();
    assertNull(getOneOfNodeFromSchema(schema));
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(
        schema.get(SchemaConstants.DEFINITIONS_NODE), yamlSchemaSubtypes, "");
    ArrayNode oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);

    yamlSchemaSubtypes.add(this.getClass());
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(
        schema.get(SchemaConstants.DEFINITIONS_NODE), yamlSchemaSubtypes, "");
    oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 2);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddOneOfInExecutionWrapperConfig() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode schema = objectMapper.readTree(getResource("testSchema/ExecutionWrapperConfigSchema.json"));
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = Collections.singletonList(
        YamlSchemaWithDetails.builder()
            .moduleType(ModuleType.CD)
            .schemaClassName("SchemaClass")
            .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                    .yamlGroup(YamlGroup.builder().group("step").build())
                                    .modulesSupported(Collections.singletonList(ModuleType.CD))
                                    .featureFlags(Collections.singletonList("FF1"))
                                    .featureRestrictions(Collections.singletonList("TEST1"))
                                    .build())
            .build());
    // oneOf node should be null in base schema.
    assertNull(getOneOfNodeFromSchema(schema));
    // FF and Feature restrictions disables.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(schema.get(SchemaConstants.DEFINITIONS_NODE),
        yamlSchemaWithDetailsList, ModuleType.CD, Collections.emptySet(), Collections.emptyMap());
    ArrayNode oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);

    // Enabling FF.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(schema.get(SchemaConstants.DEFINITIONS_NODE),
        yamlSchemaWithDetailsList, ModuleType.CD, Collections.singleton("FF1"), Collections.emptyMap());
    oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);

    // Enabling Feature restrictions.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(schema.get(SchemaConstants.DEFINITIONS_NODE),
        yamlSchemaWithDetailsList, ModuleType.CD, Collections.emptySet(), Collections.singletonMap("TEST1", true));
    oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);

    // ModuleType is not supported.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(schema.get(SchemaConstants.DEFINITIONS_NODE),
        yamlSchemaWithDetailsList, ModuleType.PMS, Collections.emptySet(), Collections.singletonMap("TEST1", true));
    oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);

    // All validations should pass and one element should be added in oneOf node.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(schema.get(SchemaConstants.DEFINITIONS_NODE),
        yamlSchemaWithDetailsList, ModuleType.CD, Collections.singleton("FF1"),
        Collections.singletonMap("TEST1", true));
    oneOfNode = getOneOfNodeFromSchema(schema);
    assertEquals(oneOfNode.size(), 1);
  }

  private String getResource(String resource) throws IOException {
    return IOUtils.resourceToString(resource, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }

  private ArrayNode getOneOfNodeFromSchema(JsonNode schema) {
    return (ArrayNode) schema.get(SchemaConstants.DEFINITIONS_NODE)
        .get(SchemaConstants.EXECUTION_WRAPPER_CONFIG_NODE)
        .get(SchemaConstants.PROPERTIES_NODE)
        .get(SchemaConstants.STEP_NODE)
        .get(SchemaConstants.ONE_OF_NODE);
  }
}