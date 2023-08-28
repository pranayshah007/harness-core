/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.core.spring;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.entities.Project;
import io.harness.repositories.core.custom.ProjectRepositoryCustom;

import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(PL)
@HarnessRepo
public interface ProjectRepository extends PagingAndSortingRepository<Project, String>, ProjectRepositoryCustom {
  Optional<Project> findByAccountIdentifierAndOrgIdentifierAndIdentifierIgnoreCaseAndDeletedNot(
      String accountIdentifier, String orgIdentifier, String identifier, boolean notDeleted);

  Optional<Project> findByAccountIdentifierAndOrgIdentifierAndIdentifierAndDeletedNot(
      String accountIdentifier, String orgIdentifier, String identifier, boolean notDeleted);
  Long countByAccountIdentifierAndDeletedIsFalse(String accountIdentifier);
}
