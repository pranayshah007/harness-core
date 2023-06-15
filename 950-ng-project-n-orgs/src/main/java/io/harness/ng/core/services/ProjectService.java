/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.services;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectFilterDTO;
import io.harness.ng.core.entities.Project;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public interface ProjectService {
  Project create(String accountIdentifier, String orgIdentifier, ProjectDTO project);

  Optional<Project> get(String accountIdentifier, String orgIdentifier, String identifier);

  Optional<Project> getConsideringCase(String accountIdentifier, String orgIdentifier, String identifier);

  Project update(String accountIdentifier, String orgIdentifier, String identifier, ProjectDTO project);

  PageResponse<ProjectDTO> listProjectsForUser(String userId, String accountId, PageRequest pageRequest);

  List<ProjectDTO> listProjectsForUser(String userId, String accountId);

  ActiveProjectsCountDTO accessibleProjectsCount(String userId, String accountId, long startInterval, long endInterval);

  /**
   * Use this method with caution, verify that the pageable sort is able to make use of the indexes.
   */
  Page<Project> listPermittedProjects(String accountIdentifier, Pageable pageable, ProjectFilterDTO projectFilterDTO);

  List<ProjectDTO> listPermittedProjects(String accountIdentifier, ProjectFilterDTO projectFilterDTO);

  ActiveProjectsCountDTO permittedProjectsCount(
      String accountIdentifier, ProjectFilterDTO projectFilterDTO, long startInterval, long endInterval);
  /**
   * Use this method with caution, verify that the criteria and pageable sort is able to make use of the indexes.
   */
  Page<Project> list(Criteria criteria, Pageable pageable);

  /**
   * Use this method with caution, verify that the criteria is able to make use of the indexes.
   */
  List<Project> list(Criteria criteria);

  boolean delete(String accountIdentifier, String orgIdentifier, String identifier, Long version);

  boolean restore(String accountIdentifier, String orgIdentifier, String identifier);

  Map<String, Integer> getProjectsCountPerOrganization(String accountIdentifier, List<String> orgIdentifiers);

  Long countProjects(String accountIdentifier);
}
