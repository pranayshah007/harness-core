/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.dto.ProjectResponse.ProjectResponseBuilder;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.favorites.entities.Favorite;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.entities.Project;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@UtilityClass
public class ProjectMapper {
  public static Project toProject(ProjectDTO createProjectDTO) {
    return Project.builder()
        .orgIdentifier(createProjectDTO.getOrgIdentifier())
        .identifier(createProjectDTO.getIdentifier())
        .name(createProjectDTO.getName())
        .description(Optional.ofNullable(createProjectDTO.getDescription()).orElse(""))
        .color(Optional.ofNullable(createProjectDTO.getColor()).orElse(HARNESS_BLUE))
        .tags(convertToList(createProjectDTO.getTags()))
        .version(createProjectDTO.getVersion())
        .modules(ModuleType.getModules())
        .build();
  }

  public static ProjectDTO writeDTO(Project project) {
    return ProjectDTO.builder()
        .orgIdentifier(project.getOrgIdentifier())
        .identifier(project.getIdentifier())
        .name(project.getName())
        .description(project.getDescription())
        .color(project.getColor())
        .tags(convertToMap(project.getTags()))
        .build();
  }

  public static ProjectResponseBuilder toProjectResponseBuilder(Project project) {
    return ProjectResponse.builder()
        .createdAt(project.getCreatedAt())
        .lastModifiedAt(project.getLastModifiedAt())
        .project(writeDTO(project));
  }
  public static Page<ProjectResponse> toResponseWithFavouritesInfo(
      Page<Project> projects, List<Favorite> favoriteProjects) {
    List<String> favoriteProjectIds =
        favoriteProjects.stream().map(Favorite::getResourceIdentifier).collect(Collectors.toList());
    return projects.map(project
        -> ProjectMapper.toProjectResponseBuilder(project)
               .isFavorite(favoriteProjectIds.contains(project.getIdentifier()))
               .build());
  }
}
