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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.ModuleType;
import io.harness.SchemaCacheKey;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOValue;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.cache.SchemaCacheUtils;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsValue;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsWrapperValue;
import io.harness.rule.Owner;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.cache.Cache;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class SchemaFetcherTest {
  @Mock Cache<SchemaCacheKey, YamlSchemaDetailsWrapperValue> schemaDetailsCache;
  @Mock Cache<SchemaCacheKey, PartialSchemaDTOWrapperValue> schemaCache;
  @Mock SchemaGetterFactory schemaGetterFactory;
  @Mock LocalSchemaGetter localSchemaGetter;
  @InjectMocks SchemaFetcher schemaFetcher;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchSchemaDetail() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode approvalSchema = objectMapper.readTree(getResource());

    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = new ArrayList<>();
    yamlSchemaWithDetailsList.add(YamlSchemaWithDetails.builder()
                                      .schema(approvalSchema)
                                      .yamlSchemaMetadata(YamlSchemaMetadata.builder().build())
                                      .schemaClassName("className")
                                      .moduleType(ModuleType.PMS)
                                      .isAvailableAtAccountLevel(false)
                                      .isAvailableAtAccountLevel(false)
                                      .isAvailableAtProjectLevel(true)
                                      .build());
    YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper =
        YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build();
    doReturn(localSchemaGetter).when(schemaGetterFactory).obtainGetter(any(), any());
    doReturn(yamlSchemaDetailsWrapper).when(localSchemaGetter).getSchemaDetails();
    // Cache miss
    YamlSchemaDetailsWrapper response = schemaFetcher.fetchSchemaDetail("accountId", ModuleType.PMS);
    assertNotNull(response);
    assertEquals(response.getYamlSchemaWithDetailsList().size(), 1);
    assertEquals(response.getYamlSchemaWithDetailsList().get(0), yamlSchemaWithDetailsList.get(0));

    // Making the cache hit
    doReturn(true).when(schemaDetailsCache).containsKey(any());

    List<YamlSchemaDetailsValue> yamlSchemaDetailsValues = new ArrayList<>();
    yamlSchemaDetailsValues.add(YamlSchemaDetailsValue.builder()
                                    .schema("{}")
                                    .yamlSchemaMetadata(YamlSchemaMetadata.builder().build())
                                    .schemaClassName("className")
                                    .moduleType(ModuleType.PMS)
                                    .isAvailableAtAccountLevel(false)
                                    .isAvailableAtAccountLevel(false)
                                    .isAvailableAtProjectLevel(true)
                                    .build());

    YamlSchemaDetailsWrapperValue yamlSchemaDetailsWrapperValue =
        YamlSchemaDetailsWrapperValue.builder().yamlSchemaWithDetailsList(yamlSchemaDetailsValues).build();
    doReturn(yamlSchemaDetailsWrapperValue).when(schemaDetailsCache).get(any());
    response = schemaFetcher.fetchSchemaDetail("accountId", ModuleType.PMS);

    assertNotNull(response);
    assertEquals(response.getYamlSchemaWithDetailsList().size(), 1);
    assertEquals(response.getYamlSchemaWithDetailsList().get(0),
        SchemaCacheUtils.toYamlSchemaWithDetails(yamlSchemaDetailsValues.get(0)));

    // Exception while fetching schema details.
    doThrow(new RuntimeException()).when(schemaDetailsCache).containsKey(any());
    assertNull(schemaFetcher.fetchSchemaDetail("accountId", ModuleType.PMS));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchSchema() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode approvalSchema = objectMapper.readTree(getResource());
    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(PartialSchemaDTO.builder()
                                 .schema(approvalSchema)
                                 .nodeName("nodeName")
                                 .moduleType(ModuleType.PMS)
                                 .namespace("")
                                 .nodeType("nodeType")
                                 .build());

    doReturn(localSchemaGetter).when(schemaGetterFactory).obtainGetter(any(), any());
    doReturn(partialSchemaDTOList).when(localSchemaGetter).getSchema(any());

    // Cache miss
    List<PartialSchemaDTO> response = schemaFetcher.fetchSchema("accountId", ModuleType.PMS, null);
    assertNotNull(response);
    assertEquals(response.size(), 1);
    assertEquals(response, partialSchemaDTOList);

    doReturn(true).when(schemaCache).containsKey(any());
    List<PartialSchemaDTOValue> partialSchemaDTOValues = new ArrayList<>();
    partialSchemaDTOValues.add(PartialSchemaDTOValue.builder()
                                   .schema("{}")
                                   .nodeName("nodeName")
                                   .moduleType(ModuleType.PMS)
                                   .namespace("")
                                   .nodeType("nodeType")
                                   .build());

    PartialSchemaDTOWrapperValue partialSchemaDTOWrapperValue =
        PartialSchemaDTOWrapperValue.builder().partialSchemaValueList(partialSchemaDTOValues).build();
    doReturn(partialSchemaDTOWrapperValue).when(schemaCache).get(any());
    // Making the cache hit
    response = schemaFetcher.fetchSchema("accountId", ModuleType.PMS, null);
    assertNotNull(response);
    assertEquals(response.size(), 1);
    assertEquals(response, SchemaCacheUtils.getPartialSchemaDTOList(partialSchemaDTOWrapperValue));

    // Exception while fetching schema details.
    doThrow(new RuntimeException()).when(schemaCache).containsKey(any());
    assertNull(schemaFetcher.fetchSchema("accountId", ModuleType.PMS, null));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testInvalidateAllCache() {
    schemaFetcher.invalidateAllCache();
    verify(schemaCache, times(1)).clear();
    verify(schemaDetailsCache, times(1)).clear();
  }

  private String getResource() throws IOException {
    return IOUtils.resourceToString(
        "approvalStageSchema.json", StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}
