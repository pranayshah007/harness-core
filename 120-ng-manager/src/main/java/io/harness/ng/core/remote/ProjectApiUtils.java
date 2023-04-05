/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;

import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.spec.server.ng.v1.model.CreateProjectRequest;
import io.harness.spec.server.ng.v1.model.ModuleType;
import io.harness.spec.server.ng.v1.model.ProjectResponse;
import io.harness.spec.server.ng.v1.model.UpdateProjectRequest;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.data.domain.Pageable;

public class ProjectApiUtils {
  private final Validator validator;

  @Inject
  public ProjectApiUtils(Validator validator) {
    this.validator = validator;
  }

  public ProjectDTO getProjectDto(CreateProjectRequest createProjectRequest) {
    ProjectDTO projectDTO = ProjectDTO.builder()
                                .name(createProjectRequest.getProject().getName())
                                .identifier(createProjectRequest.getProject().getIdentifier())
                                .orgIdentifier(createProjectRequest.getProject().getOrg())
                                .color(createProjectRequest.getProject().getColor())
                                .description(createProjectRequest.getProject().getDescription())
                                .tags(createProjectRequest.getProject().getTags())
                                .build();
    validate(projectDTO);

    return projectDTO;
  }

  private void validate(ProjectDTO projectDTO) {
    Set<ConstraintViolation<ProjectDTO>> violations = validator.validate(projectDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
  }

  public ProjectDTO getProjectDto(UpdateProjectRequest updateProjectRequest) {
    ProjectDTO projectDTO = ProjectDTO.builder()
                                .name(updateProjectRequest.getProject().getName())
                                .identifier(updateProjectRequest.getProject().getIdentifier())
                                .orgIdentifier(updateProjectRequest.getProject().getOrg())
                                .color(updateProjectRequest.getProject().getColor())
                                .description(updateProjectRequest.getProject().getDescription())
                                .tags(updateProjectRequest.getProject().getTags())
                                .build();
    validate(projectDTO);
    return projectDTO;
  }

  public List<io.harness.ModuleType> toModules(List<ModuleType> modules) {
    if (CollectionUtils.isEmpty(modules)) {
      return Collections.emptyList();
    }
    return modules.stream().map(module -> io.harness.ModuleType.fromString(module.name())).collect(Collectors.toList());
  }

  public List<ModuleType> toApiModules(List<io.harness.ModuleType> modules) {
    if (CollectionUtils.isEmpty(modules)) {
      return Collections.emptyList();
    }
    return modules.stream().map(module -> ModuleType.fromValue(module.name())).collect(Collectors.toList());
  }

  public ProjectResponse getProjectResponse(Project project) {
    ProjectResponse projectResponse = new ProjectResponse();
    io.harness.spec.server.ng.v1.model.Project proj = new io.harness.spec.server.ng.v1.model.Project();
    proj.setOrg(project.getOrgIdentifier());
    proj.setIdentifier(project.getIdentifier());
    proj.setName(project.getName());
    proj.setDescription(project.getDescription());
    proj.setColor(project.getColor());
    proj.setModules(toApiModules(project.getModules()));
    proj.setTags(ApiUtils.getTags(project.getTags()));

    projectResponse.setProject(proj);
    projectResponse.setCreated(project.getCreatedAt());
    projectResponse.setUpdated(project.getLastModifiedAt());

    return projectResponse;
  }

  public Pageable getPageRequest(int page, int limit, String sort, String order) {
    List<SortOrder> sortOrders;
    String mappedFieldName = getFieldName(sort);
    if (mappedFieldName != null) {
      SortOrder.OrderType fieldOrder = EnumUtils.getEnum(SortOrder.OrderType.class, order, DESC);
      sortOrders = ImmutableList.of(aSortOrder().withField(mappedFieldName, fieldOrder).build());
    } else {
      sortOrders = ImmutableList.of(aSortOrder().withField(ProjectKeys.lastModifiedAt, DESC).build());
    }
    return PageUtils.getPageRequest(new PageRequest(page, limit, sortOrders));
  }

  private String getFieldName(String sort) {
    PageUtils.SortFields sortField = PageUtils.SortFields.fromValue(sort);
    if (sortField == null) {
      sortField = PageUtils.SortFields.UNSUPPORTED;
    }
    switch (sortField) {
      case IDENTIFIER:
        return ProjectKeys.identifier;
      case NAME:
        return ProjectKeys.name;
      case CREATED:
        return ProjectKeys.createdAt;
      case UPDATED:
        return ProjectKeys.lastModifiedAt;
      case UNSUPPORTED:
      default:
        return null;
    }
  }
}