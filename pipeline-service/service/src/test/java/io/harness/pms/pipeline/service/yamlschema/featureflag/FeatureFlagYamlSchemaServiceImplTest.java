/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.featureflag;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.rule.Owner;
import io.harness.utils.FeatureRestrictionsGetter;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class FeatureFlagYamlSchemaServiceImplTest {
  @Mock private YamlSchemaProvider yamlSchemaProvider;
  @Mock private PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @Mock private YamlSchemaGenerator yamlSchemaGenerator;
  @Mock private FeatureRestrictionsGetter featureRestrictionsGetter;
  @InjectMocks private FeatureFlagYamlServiceImpl featureFlagYamlService;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSchema() throws IOException {
    try (MockedStatic<YamlSchemaUtils> mockStatic = Mockito.mockStatic(YamlSchemaUtils.class)) {
      String accountId = "accountId";
      String orgId = "orgId";
      String projectId = "projectId";
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode ffSchema = objectMapper.readTree(getResource());
      doReturn(ffSchema).when(yamlSchemaProvider).getYamlSchema(EntityType.FEATURE_FLAG_STAGE, orgId, projectId, null);
      when(YamlSchemaUtils.getNodeEntityTypesByYamlGroup(any(), any()))
          .thenReturn(Collections.singletonList(EntityType.HTTP_STEP));

      ArgumentCaptor<List> entityTypesArgumentCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<JsonNode> schemaArgumentCaptor = ArgumentCaptor.forClass(JsonNode.class);

      PartialSchemaDTO response =
          featureFlagYamlService.getFeatureFlagYamlSchema(accountId, projectId, orgId, null, null);
      verify(yamlSchemaProvider, times(1))
          .mergeAllV2StepsDefinitions(any(), any(), any(), any(), entityTypesArgumentCaptor.capture());
      verify(yamlSchemaGenerator, times(1)).modifyRefsNamespace(schemaArgumentCaptor.capture(), any());

      List<EntityType> v2EntityTypes = entityTypesArgumentCaptor.getValue();
      assertEquals(v2EntityTypes.size(), 1);
      assertEquals(v2EntityTypes.get(0), EntityType.HTTP_STEP);

      JsonNode schemaInProcess = schemaArgumentCaptor.getValue();
      assertNotNull(schemaInProcess);
      assertEquals(schemaInProcess.get(DEFINITIONS_NODE).size(), 1);
      assertNotNull(schemaInProcess.get(DEFINITIONS_NODE).get("cf"));
    }
  }

  private String getResource() throws IOException {
    return IOUtils.resourceToString("ffStageSchema.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
