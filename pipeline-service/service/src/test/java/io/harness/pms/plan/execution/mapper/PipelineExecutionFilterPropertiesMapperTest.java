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
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.entity.PipelineExecutionFilterProperties;
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
public class PipelineExecutionFilterPropertiesMapperTest extends CategoryTest {
  @InjectMocks PipelineExecutionFilterPropertiesMapper pipelineExecutionFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineExecutionFilterToEntity() {
    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .moduleProperties(Document.parse("{\"name\": \"A\"}"))
            .pipelineName("pipeline1")
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1234L).build())
            .tags(Map.of("key1", "value1"))
            .build();

    PipelineExecutionFilterProperties pipelineExecutionFilterProperties =
        (PipelineExecutionFilterProperties) pipelineExecutionFilterPropertiesMapper.toEntity(
            pipelineExecutionFilterPropertiesDTO);
    assertNotNull(pipelineExecutionFilterProperties);
    assertEquals(pipelineExecutionFilterProperties.getTimeRange(), pipelineExecutionFilterProperties.getTimeRange());
    assertEquals(
        pipelineExecutionFilterProperties.getPipelineName(), pipelineExecutionFilterPropertiesDTO.getPipelineName());
    assertEquals(
        pipelineExecutionFilterProperties.getPipelineTags(), pipelineExecutionFilterPropertiesDTO.getPipelineTags());
    assertEquals(pipelineExecutionFilterProperties.getModuleProperties(),
        pipelineExecutionFilterPropertiesDTO.getModuleProperties());
    assertEquals(pipelineExecutionFilterProperties.getStatus(), pipelineExecutionFilterPropertiesDTO.getStatus());
    assertEquals(pipelineExecutionFilterProperties.getTags(),
        TagMapper.convertToList(pipelineExecutionFilterPropertiesDTO.getTags()));
  }

  @Test
  @Owner(developers = ANINDITAA)
  @Category(UnitTests.class)
  public void testPipelineExecutionFilterWriteDTO() {
    PipelineExecutionFilterProperties pipelineExecutionFilterProperties =
        PipelineExecutionFilterProperties.builder()
            .moduleProperties(Document.parse("{\"name\":\"A\"}"))
            .pipelineName("pipeline1")
            .pipelineTags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1234L).build())
            .tags(List.of(NGTag.builder().key("key1").value("value1").build()))
            .type(FilterType.PIPELINEEXECUTION)
            .build();

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        (PipelineExecutionFilterPropertiesDTO) pipelineExecutionFilterPropertiesMapper.writeDTO(
            pipelineExecutionFilterProperties);
    assertNotNull(pipelineExecutionFilterPropertiesDTO);
    assertEquals(
        pipelineExecutionFilterPropertiesDTO.getPipelineTags(), pipelineExecutionFilterProperties.getPipelineTags());
    assertEquals(
        pipelineExecutionFilterPropertiesDTO.getPipelineName(), pipelineExecutionFilterProperties.getPipelineName());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getModuleProperties(),
        pipelineExecutionFilterProperties.getModuleProperties());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getStatus(), pipelineExecutionFilterProperties.getStatus());
    assertEquals(pipelineExecutionFilterPropertiesDTO.getTags(),
        TagMapper.convertToMap(pipelineExecutionFilterProperties.getTags()));
  }
}