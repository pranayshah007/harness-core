/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.services;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;

import java.util.Optional;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface ServiceOverrideService {
  /***
   *
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param environmentRef
   * method works with both environment scoped ref or environment identifier condition given with identifier
   * account/org/projectId params should be according to scope to search in.
   * @param serviceRef
   * method expects serviceRef param to be qualified scoped ref
   * @return
   */
  Optional<NGServiceOverridesEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef);

  NGServiceOverridesEntity upsert(NGServiceOverridesEntity requestServiceOverride);

  Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageable);

  boolean delete(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      @NotEmpty String environmentRef, @NotEmpty String serviceRef);

  boolean deleteAllInEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      @NotEmpty String environmentRef);

  /**
   * Deletes all infrastructures linked to a particular environment at project level.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @return boolean to indicate if deletion was successful
   */
  boolean deleteAllInProject(
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier);

  /**
   * Deletes all infrastructures linked to a particular environment at org level.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @return boolean to indicate if deletion was successful
   */
  boolean deleteAllInOrg(@NotEmpty String accountId, @NotEmpty String orgIdentifier);

  boolean deleteAllInProjectForAService(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String serviceRef);

  String createServiceOverrideInputsYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, String serviceIdentifier);
}
