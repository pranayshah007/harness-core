/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.rule.OwnerRule.SHALINI;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.PipelineServiceConfiguration;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class SchemaFetcherTest extends CategoryTest {
  @Mock PipelineServiceConfiguration pipelineServiceConfiguration;
  @InjectMocks SchemaFetcher schemaFetcher;
  private final String VERSION_V0 = "v0";
  private final String VERSION_V1 = "v1";
  private final String PIPELINE_JSON_PATH_V1 = "static-schema/v1/pipeline.json";
  private final String PIPELINE_JSON_PATH_V0 = "static-schema/v0/pipeline.json";
  private final String TRIGGER_JSON_PATH_V0 = "static-schema/v0/trigger.json";
  private final String PIPELINE_JSON = "pipeline.json";
  private final String TRIGGER_JSON = "trigger.json";

  @Mock ObjectMapper objectMapper;

  @Before
  public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchPipelineStaticYamlSchema() throws IOException {
    assertThatThrownBy(() -> schemaFetcher.fetchPipelineStaticYamlSchema(HarnessYamlVersion.V0))
        .hasMessage(format(
            "[PMS] Incorrect version [%s] of Pipeline Schema passed, Valid values are [v0, v1]", HarnessYamlVersion.V0))
        .isInstanceOf(InvalidRequestException.class);
    assertEquals(
        schemaFetcher.fetchPipelineStaticYamlSchema(VERSION_V0), schemaFetcher.fetchFile(PIPELINE_JSON_PATH_V0));
    assertEquals(
        schemaFetcher.fetchPipelineStaticYamlSchema(VERSION_V1), schemaFetcher.fetchFile(PIPELINE_JSON_PATH_V1));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchTriggerStaticYamlSchema() throws IOException {
    assertEquals(schemaFetcher.fetchTriggerStaticYamlSchema(), schemaFetcher.fetchFile(TRIGGER_JSON_PATH_V0));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFetchSchemaFromRepo() throws IOException {
    MockedStatic<JsonPipelineUtils> mockedStatic = Mockito.mockStatic(JsonPipelineUtils.class);
    mockedStatic.when(() -> JsonPipelineUtils.getMapper()).thenReturn(objectMapper);
    String fileUrl = "https://raw.githubusercontent.com/harness/harness-schema/main/%s/%s";
    when(pipelineServiceConfiguration.getStaticSchemaFileURL()).thenReturn(fileUrl);
    when(objectMapper.readTree(new URL(format(fileUrl, VERSION_V0, PIPELINE_JSON))))
        .thenReturn(YamlUtils.readAsJsonNode("abc"));
    assertEquals(schemaFetcher.fetchSchemaFromRepo(EntityType.PIPELINES, VERSION_V0), YamlUtils.readAsJsonNode("abc"));
    when(objectMapper.readTree(new URL(format(fileUrl, VERSION_V0, TRIGGER_JSON))))
        .thenReturn(YamlUtils.readAsJsonNode("def"));
    assertEquals(schemaFetcher.fetchSchemaFromRepo(EntityType.TRIGGERS, VERSION_V0), YamlUtils.readAsJsonNode("def"));
    assertEquals(schemaFetcher.fetchSchemaFromRepo(EntityType.WAIT_STEP, VERSION_V0), YamlUtils.readAsJsonNode("abc"));
    when(objectMapper.readTree(new URL(format(fileUrl, VERSION_V0, TRIGGER_JSON)))).thenThrow(new IOException());
    assertEquals(schemaFetcher.fetchSchemaFromRepo(EntityType.TRIGGERS, VERSION_V0),
        schemaFetcher.fetchFile(PIPELINE_JSON_PATH_V0));
  }
}
