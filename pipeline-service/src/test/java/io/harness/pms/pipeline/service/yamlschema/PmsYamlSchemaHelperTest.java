/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.rule.Owner;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsYamlSchemaHelperTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  ;
  @InjectMocks PmsYamlSchemaHelper pmsYamlSchemaHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEnabledFeatureFlags() {
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(
        YamlSchemaWithDetails.builder()
            .schemaClassName("schemaClass")
            .yamlSchemaMetadata(YamlSchemaMetadata.builder().featureFlags(ImmutableList.of("FF1", "FF2")).build())
            .build());
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled("accountId", "FF1");
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled("accountId", "FF2");
    Set<String> enabledFeatureFlags =
        pmsYamlSchemaHelper.getEnabledFeatureFlags("accountId", yamlSchemaWithDetailsList);
    assertEquals(enabledFeatureFlags.size(), 1);
    assertTrue(enabledFeatureFlags.contains("FF1"));
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled("accountId", "FF1");
    enabledFeatureFlags = pmsYamlSchemaHelper.getEnabledFeatureFlags("accountId", yamlSchemaWithDetailsList);
    assertEquals(enabledFeatureFlags.size(), 0);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testProcessStageSchema() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode schema = objectMapper.readTree(getResource());

    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();

    assertNull(getOneOfNodeInStages(schema));
    pmsYamlSchemaHelper.processStageSchema(
        yamlSchemaWithDetailsList, (ObjectNode) schema.get(SchemaConstants.DEFINITIONS_NODE));
    assertNotNull(getOneOfNodeInStages(schema));
    // OneOf nodes would be empty.(Removed StageElemenetConfig from schema)
    assertEquals(getOneOfNodeInStages(schema).size(), 0);

    yamlSchemaWithDetailsList.add(YamlSchemaWithDetails.builder()
                                      .schemaClassName("StageClass")
                                      .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                                              .namespace("cd")
                                                              .yamlGroup(YamlGroup.builder().group("STAGE").build())
                                                              .build())
                                      .build());
    pmsYamlSchemaHelper.processStageSchema(
        yamlSchemaWithDetailsList, (ObjectNode) schema.get(SchemaConstants.DEFINITIONS_NODE));
    // /cd/StageClass would be added in oneOf node.
    assertEquals(getOneOfNodeInStages(schema).size(), 1);
  }

  private String getResource() throws IOException {
    return IOUtils.resourceToString(
        "stageElementWrapperConfigSchema.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }

  private JsonNode getOneOfNodeInStages(JsonNode schema) {
    return schema.get(SchemaConstants.DEFINITIONS_NODE)
        .get(SchemaConstants.STAGE_ELEMENT_WRAPPER_CONFIG)
        .get(SchemaConstants.PROPERTIES_NODE)
        .get(SchemaConstants.STAGE_NODE)
        .get(SchemaConstants.ONE_OF_NODE);
  }
}
