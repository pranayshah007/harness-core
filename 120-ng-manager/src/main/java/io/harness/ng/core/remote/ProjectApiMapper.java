/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;

import static javax.ws.rs.core.UriBuilder.fromPath;

import io.harness.NGCommonEntityConstants;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.spec.server.ng.model.CreateProjectRequest;
import io.harness.spec.server.ng.model.ModuleType;
import io.harness.spec.server.ng.model.ProjectResponse;
import io.harness.spec.server.ng.model.UpdateProjectRequest;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.domain.Pageable;

public class ProjectApiMapper {
  public static final int PAGE = 1;

  private final Validator validator;

  @Inject
  public ProjectApiMapper(Validator validator) {
    this.validator = validator;
  }

  public ProjectDTO getProjectDto(CreateProjectRequest createProjectRequest) {
    ProjectDTO projectDTO = ProjectDTO.builder()
                                .name(createProjectRequest.getProject().getName())
                                .identifier(createProjectRequest.getProject().getSlug())
                                .orgIdentifier(createProjectRequest.getProject().getOrg())
                                .color(createProjectRequest.getProject().getColor())
                                .modules(toModules(createProjectRequest.getProject().getModules()))
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
                                .identifier(updateProjectRequest.getProject().getSlug())
                                .orgIdentifier(updateProjectRequest.getProject().getOrg())
                                .color(updateProjectRequest.getProject().getColor())
                                .modules(toModules(updateProjectRequest.getProject().getModules()))
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
    io.harness.spec.server.ng.model.Project proj = new io.harness.spec.server.ng.model.Project();
    proj.setOrg(project.getOrgIdentifier());
    proj.setSlug(project.getIdentifier());
    proj.setName(project.getName());
    proj.setDescription(project.getDescription());
    proj.setColor(project.getColor());
    proj.setModules(toApiModules(project.getModules()));
    proj.setTags(getTags(project.getTags()));

    projectResponse.setProject(proj);
    projectResponse.setCreated(project.getCreatedAt());
    projectResponse.setUpdated(project.getLastModifiedAt());

    return projectResponse;
  }

  private Map<String, String> getTags(List<NGTag> tags) {
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyMap();
    }
    return tags.stream().collect(Collectors.toMap(NGTag::getKey, NGTag::getValue));
  }

  public Pageable getPageRequest(int page, int limit) {
    SortOrder order = aSortOrder().withField(ProjectKeys.lastModifiedAt, DESC).build();
    return PageUtils.getPageRequest(new PageRequest(page, limit, ImmutableList.of(order)));
  }

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(Link.fromUri(fromPath(path)
                               .queryParam(NGCommonEntityConstants.PAGE, page)
                               .queryParam(NGCommonEntityConstants.PAGE_SIZE, limit)
                               .build())
                  .rel(NGCommonEntityConstants.SELF_REL)
                  .build());

    if (page >= PAGE) {
      links.add(Link.fromUri(fromPath(path)
                                 .queryParam(NGCommonEntityConstants.PAGE, page - 1)
                                 .queryParam(NGCommonEntityConstants.PAGE_SIZE, limit)
                                 .build())
                    .rel(NGCommonEntityConstants.PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path)
                                 .queryParam(NGCommonEntityConstants.PAGE, page + 1)
                                 .queryParam(NGCommonEntityConstants.PAGE_SIZE, limit)
                                 .build())
                    .rel(NGCommonEntityConstants.NEXT_REL)
                    .build());
    }

    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
}