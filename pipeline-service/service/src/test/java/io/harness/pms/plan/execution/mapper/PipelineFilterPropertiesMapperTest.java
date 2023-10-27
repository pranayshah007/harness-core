/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ANINDITAA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filter.FilterType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.pipeline.PipelineFilterProperties;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.mappers.PipelineFilterPropertiesMapper;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class PipelineFilterPropertiesMapperTest extends CategoryTest {
  @InjectMocks PipelineFilterPropertiesMapper pipelineFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineFilterToEntity() {
    PipelineFilterPropertiesDto pipelineFilterPropertiesDto =
        PipelineFilterPropertiesDto.builder()
            .name("A")
            .pipelineIdentifiers(List.of("identifier1"))
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .description("desc")
            .moduleProperties(Document.parse("{\"name\": \"A\"}"))
            .tags(Map.of("key1", "value1"))
            .build();

    PipelineFilterProperties pipelineFilterProperties =
        (PipelineFilterProperties) pipelineFilterPropertiesMapper.toEntity(pipelineFilterPropertiesDto);
    assertNotNull(pipelineFilterProperties);
    assertEquals(
        pipelineFilterProperties.getPipelineIdentifiers(), pipelineFilterPropertiesDto.getPipelineIdentifiers());
    assertEquals(pipelineFilterProperties.getPipelineTags(), pipelineFilterPropertiesDto.getPipelineTags());
    assertEquals(pipelineFilterProperties.getName(), pipelineFilterPropertiesDto.getName());
    assertEquals(pipelineFilterProperties.getModuleProperties(), pipelineFilterPropertiesDto.getModuleProperties());
    assertEquals(pipelineFilterProperties.getDescription(), pipelineFilterPropertiesDto.getDescription());
    assertEquals(pipelineFilterProperties.getTags(), TagMapper.convertToList(pipelineFilterPropertiesDto.getTags()));
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineFilterWriteDTO() {
    PipelineFilterProperties pipelineFilterProperties =
        PipelineFilterProperties.builder()
            .name("A")
            .pipelineIdentifiers(List.of("identifier1"))
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .description("desc")
            .moduleProperties(Document.parse("{\"name\": \"A\"}"))
            .type(FilterType.PIPELINESETUP)
            .tags(List.of(NGTag.builder().build()))
            .build();

    PipelineFilterPropertiesDto pipelineFilterPropertiesDto =
        (PipelineFilterPropertiesDto) pipelineFilterPropertiesMapper.writeDTO(pipelineFilterProperties);
    assertNotNull(pipelineFilterPropertiesDto);
    assertEquals(pipelineFilterPropertiesDto.getPipelineTags(), pipelineFilterProperties.getPipelineTags());
    assertEquals(pipelineFilterPropertiesDto.getName(), pipelineFilterProperties.getName());
    assertEquals(pipelineFilterPropertiesDto.getModuleProperties(), pipelineFilterProperties.getModuleProperties());
    assertEquals(pipelineFilterPropertiesDto.getDescription(), pipelineFilterProperties.getDescription());
    assertEquals(
        pipelineFilterPropertiesDto.getPipelineIdentifiers(), pipelineFilterProperties.getPipelineIdentifiers());
    assertEquals(pipelineFilterPropertiesDto.getTags(), TagMapper.convertToMap(pipelineFilterProperties.getTags()));
  }
}
