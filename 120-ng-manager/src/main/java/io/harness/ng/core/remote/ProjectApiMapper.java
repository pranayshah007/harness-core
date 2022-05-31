/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.spec.server.ng.model.ModuleType;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;

public class ProjectApiMapper {
  public static ProjectDTO getProjectDto(
      String org, io.harness.spec.server.ng.model.CreateProjectRequest createProjectRequest) {
    return new ProjectDTO(org, createProjectRequest.getSlug(), createProjectRequest.getName(),
        createProjectRequest.getColor(), toModules(createProjectRequest.getModules()),
        createProjectRequest.getDescription(), createProjectRequest.getTags());
  }

  public static ProjectDTO getProjectDto(
      String org, String slug, io.harness.spec.server.ng.model.UpdateProjectRequest updateProjectRequest) {
    return new ProjectDTO(org, slug, updateProjectRequest.getName(), updateProjectRequest.getColor(),
        toModules(updateProjectRequest.getModules()), updateProjectRequest.getDescription(),
        updateProjectRequest.getTags());
  }

  public static List<io.harness.ModuleType> toModules(List<ModuleType> modules) {
    return modules.stream().map(module -> io.harness.ModuleType.fromString(module.name())).collect(Collectors.toList());
  }

  public static List<ModuleType> toApiModules(List<io.harness.ModuleType> modules) {
    return modules.stream().map(module -> ModuleType.fromValue(module.name())).collect(Collectors.toList());
  }

  public static ProjectResponse getProjectResponse(Project project) {
    ProjectResponse projectResponse = new ProjectResponse();
    projectResponse.setOrgIdentifier(project.getOrgIdentifier());
    projectResponse.setSlug(project.getIdentifier());
    projectResponse.setName(project.getName());
    projectResponse.setDescription(project.getDescription());
    projectResponse.setColor(project.getColor());
    projectResponse.setModules(toApiModules(project.getModules()));
    projectResponse.setTags(getTags(project.getTags()));
    projectResponse.setCreatedAt(project.getCreatedAt());
    projectResponse.setLastModifiedAt(project.getLastModifiedAt());

    return projectResponse;
  }

  private static Map<String, String> getTags(List<NGTag> tags) {
    return tags.stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue));
  }

  public static Pageable getPageRequest(Integer page, Integer limit) {
    SortOrder order =
        SortOrder.Builder.aSortOrder().withField(Project.ProjectKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
    return PageUtils.getPageRequest(new PageRequest(page, limit, ImmutableList.of(order)));
  }
}
