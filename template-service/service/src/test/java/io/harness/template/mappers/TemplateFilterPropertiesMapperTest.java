/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;
import io.harness.template.resources.beans.TemplateFilterProperties;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class TemplateFilterPropertiesMapperTest extends CategoryTest {
  @InjectMocks TemplateFilterPropertiesMapper templateFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testTemplateFilterToEntity() {
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        TemplateFilterPropertiesDTO.builder()
            .templateIdentifiers(List.of("abc"))
            .templateNames(List.of("A"))
            .templateEntityTypes(List.of(TemplateEntityType.PIPELINE_TEMPLATE))
            .childTypes(List.of("child1"))
            .description("desc")
            .repoName("repo1")
            .build();
    templateFilterPropertiesDTO.setTags(Map.of("key1", "value1"));
    TemplateFilterProperties templateFilterProperties =
        (TemplateFilterProperties) templateFilterPropertiesMapper.toEntity(templateFilterPropertiesDTO);
    assertNotNull(templateFilterProperties);
    assertEquals(
        templateFilterProperties.getTemplateIdentifiers(), templateFilterPropertiesDTO.getTemplateIdentifiers());
    assertEquals(templateFilterProperties.getTemplateNames(), templateFilterPropertiesDTO.getTemplateNames());
    assertEquals(
        templateFilterProperties.getTemplateEntityTypes(), templateFilterPropertiesDTO.getTemplateEntityTypes());
    assertEquals(templateFilterProperties.getChildTypes(), templateFilterPropertiesDTO.getChildTypes());
    assertEquals(templateFilterProperties.getDescription(), templateFilterPropertiesDTO.getDescription());
    assertEquals(templateFilterProperties.getRepoName(), templateFilterPropertiesDTO.getRepoName());
    assertEquals(templateFilterProperties.getTags(), TagMapper.convertToList(templateFilterPropertiesDTO.getTags()));
    assertEquals(templateFilterPropertiesDTO.getFilterType(), templateFilterProperties.getType());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testTemplateFilterWriteDto() {
    TemplateFilterProperties templateFilterProperties =
        TemplateFilterProperties.builder()
            .templateIdentifiers(List.of("abc"))
            .templateNames(List.of("A"))
            .templateEntityTypes(List.of(TemplateEntityType.PIPELINE_TEMPLATE))
            .childTypes(List.of("child1"))
            .description("desc")
            .repoName("repo1")
            .build();
    templateFilterProperties.setTags(List.of(NGTag.builder().key("key1").value("value1").build()));
    TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
        (TemplateFilterPropertiesDTO) templateFilterPropertiesMapper.writeDTO(templateFilterProperties);
    assertNotNull(templateFilterPropertiesDTO);
    assertEquals(
        templateFilterPropertiesDTO.getTemplateIdentifiers(), templateFilterProperties.getTemplateIdentifiers());
    assertEquals(templateFilterPropertiesDTO.getTemplateNames(), templateFilterProperties.getTemplateNames());
    assertEquals(
        templateFilterPropertiesDTO.getTemplateEntityTypes(), templateFilterProperties.getTemplateEntityTypes());
    assertEquals(templateFilterPropertiesDTO.getChildTypes(), templateFilterProperties.getChildTypes());
    assertEquals(templateFilterPropertiesDTO.getDescription(), templateFilterProperties.getDescription());
    assertEquals(templateFilterPropertiesDTO.getRepoName(), templateFilterProperties.getRepoName());
    assertEquals(templateFilterPropertiesDTO.getTags(), TagMapper.convertToMap(templateFilterProperties.getTags()));
  }
}
