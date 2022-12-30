/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.envGroup;

import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface EnvironmentGroupRepositoryCustom {
  Optional<EnvironmentGroupEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountId, String orgIdentifier, String projectIdentifier, String envGroupId, boolean notDeleted);

  EnvironmentGroupEntity create(EnvironmentGroupEntity environmentGroupEntity);

  Page<EnvironmentGroupEntity> list(
      Criteria criteria, Pageable pageRequest, String projectIdentifier, String orgIdentifier, String accountId);

  boolean deleteEnvGroup(EnvironmentGroupEntity environmentGroupEntity);

  EnvironmentGroupEntity update(
      EnvironmentGroupEntity updatedEntity, EnvironmentGroupEntity originalEntity, Criteria criteria);
}
