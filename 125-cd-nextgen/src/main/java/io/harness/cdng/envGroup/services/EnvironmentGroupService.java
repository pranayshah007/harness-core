/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.services;

import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface EnvironmentGroupService {
  Optional<EnvironmentGroupEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupIdentifier, boolean deleted);

  EnvironmentGroupEntity create(EnvironmentGroupEntity entity);

  Page<EnvironmentGroupEntity> list(
      Criteria criteria, Pageable pageRequest, String projectIdentifier, String orgIdentifier, String accountId);

  EnvironmentGroupEntity delete(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupId, Long version);

  EnvironmentGroupEntity update(EnvironmentGroupEntity requestedEntity);

  void deleteAllEnvGroupInProject(String accountId, String orgIdentifier, String projectIdentifier);

  Criteria formCriteria(String accountId, String orgIdentifier, String projectIdentifier, boolean deleted,
      String searchTerm, String filterIdentifier, EnvironmentGroupFilterPropertiesDTO filterProperties);
}
