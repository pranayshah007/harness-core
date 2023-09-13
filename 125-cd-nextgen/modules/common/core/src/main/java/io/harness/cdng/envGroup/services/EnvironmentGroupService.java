/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.services;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public interface EnvironmentGroupService {
  Optional<EnvironmentGroupEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupIdentifier, boolean deleted);

  EnvironmentGroupEntity create(EnvironmentGroupEntity entity);

  Page<EnvironmentGroupEntity> list(
      Criteria criteria, Pageable pageRequest, String projectIdentifier, String orgIdentifier, String accountId);

  EnvironmentGroupEntity delete(String accountId, String orgIdentifier, String projectIdentifier, String envGroupId,
      Long version, boolean forceDelete);

  EnvironmentGroupEntity update(EnvironmentGroupEntity requestedEntity);

  /**
   * Deletes all environment groups linked to a particular harness project.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   */
  boolean deleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier);

  /**
   * Deletes all environment groups linked to a particular harness org.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   */
  boolean deleteAllInOrg(String accountId, String orgIdentifier);

  Criteria formCriteria(String accountId, String orgIdentifier, String projectIdentifier, boolean deleted,
      String searchTerm, String filterIdentifier, EnvironmentGroupFilterPropertiesDTO filterProperties,
      boolean includeAllEnvGroupsAccessibleAtScope);
}
