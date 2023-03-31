/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ProjectMapperTest extends CategoryTest {
  ProjectDTO projectDTO;
  Project project;
  String HARNESS_BLUE = "#0063F7";

  @Before
  public void setUp() {
    projectDTO = ProjectDTO.builder()
                     .orgIdentifier(randomAlphabetic(10))
                     .identifier(randomAlphabetic(10))
                     .name(randomAlphabetic(10))
                     .build();
    project = Project.builder()
                  .accountIdentifier(randomAlphabetic(10))
                  .orgIdentifier(randomAlphabetic(10))
                  .identifier(randomAlphabetic(10))
                  .name(randomAlphabetic(10))
                  .color(randomAlphabetic(10))
                  .description("")
                  .tags(emptyList())
                  .modules(ModuleType.getModules())
                  .lastModifiedAt(10L)
                  .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testToProject() {
    Project fromDTO = ProjectMapper.toProject(projectDTO);
    assertNotNull(fromDTO);
    assertEquals(projectDTO.getOrgIdentifier(), fromDTO.getOrgIdentifier());
    assertEquals(projectDTO.getIdentifier(), fromDTO.getIdentifier());
    assertEquals(projectDTO.getName(), fromDTO.getName());
    assertNotNull(fromDTO.getModules());
    assertEquals(HARNESS_BLUE, fromDTO.getColor());
    assertNotNull(fromDTO.getTags());
    assertNotNull(fromDTO.getDescription());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    ProjectDTO fromProject = ProjectMapper.writeDTO(project);
    assertEquals(project.getOrgIdentifier(), fromProject.getOrgIdentifier());
    assertEquals(project.getIdentifier(), fromProject.getIdentifier());
    assertEquals(project.getName(), fromProject.getName());
    assertEquals(project.getColor(), fromProject.getColor());
    assertEquals(project.getDescription(), fromProject.getDescription());
    assertEquals(emptyMap(), fromProject.getTags());
    assertEquals(project.getModules(), fromProject.getModules());
  }
}
