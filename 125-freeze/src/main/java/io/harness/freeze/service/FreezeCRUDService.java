/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.service;

import io.harness.beans.Scope;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseWrapperDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.response.FrozenExecutionDetails;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface FreezeCRUDService {
  FreezeResponseDTO createFreezeConfig(String deploymentFreezeYaml, String accountId, String orgId, String projectId);

  FreezeResponseDTO manageGlobalFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId);

  FreezeResponseDTO updateFreezeConfig(
      String deploymentFreezeYaml, String accountId, String orgId, String projectId, String freezeIdentifier);

  void deleteFreezeConfig(String freezeId, String accountId, String orgId, String projectId);

  FreezeResponseDTO getFreezeConfig(String freezeIdentifier, String accountId, String orgId, String projectId);

  FrozenExecutionDetails getFrozenExecutionDetails(
      String accountId, String orgId, String projectId, String planExecutionId, String baseUrl);

  Page<FreezeSummaryResponseDTO> list(Criteria criteria, Pageable pageRequest);

  FreezeResponseWrapperDTO deleteFreezeConfigs(
      List<String> freezeIdentifiers, String accountId, String orgId, String projectId);

  void deleteByScope(Scope scope);

  FreezeResponseWrapperDTO updateActiveStatus(
      FreezeStatus freezeStatus, String accountId, String orgId, String projectId, List<String> freezeIdentifiers);

  FreezeResponseDTO getGlobalFreeze(String accountId, String orgId, String projectId);

  FreezeSummaryResponseDTO getGlobalFreezeSummary(String accountId, String orgId, String projectId);

  List<FreezeResponseDTO> getParentGlobalFreezeSummary(String accountId, String orgId, String projectId);
}
