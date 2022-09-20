/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service;

import io.harness.freeze.beans.FreezeResponse;
import io.harness.freeze.beans.FreezeStatus;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface FreezeCRUDService {
  FreezeResponse createFreezeConfig(String deploymentFreezeYaml, String accountId, String orgId, String projectId);

  FreezeResponse updateFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId, String freezeIdentifier);

  FreezeResponse deleteFreezeConfig(String freezeId, String accountId, String orgId, String projectId);

  FreezeResponse getFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId);

  Page<FreezeResponse> list(Criteria criteria, Pageable pageRequest);

  FreezeResponse deleteFreezeConfigs(String freezeIdentifiers, String accountId, String orgId, String projectId);

  FreezeResponse updateActiveStatus(
      FreezeStatus freezeStatus, String accountId, String orgId, String projectId, List<String> freezeIdentifiers);
}
