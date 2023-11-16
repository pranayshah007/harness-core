/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.entity.CheckStatusEntity;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckGraph;
import io.harness.spec.server.idp.v1.model.CheckStatsResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CheckService {
  void createCheck(CheckDetails checkDetails, String accountIdentifier);
  void updateCheck(CheckDetails checkDetails, String accountIdentifier);
  Page<CheckEntity> getChecksByAccountId(
      Boolean custom, String accountIdentifier, Pageable pageRequest, String searchTerm);
  List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers);
  void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete);
  CheckDetails getCheckDetails(String accountIdentifier, String identifier, Boolean custom);
  List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, Set<String> identifiers);
  CheckStatsResponse getCheckStats(String accountIdentifier, String identifier, Boolean custom);
  List<CheckGraph> getCheckGraph(String accountIdentifier, String identifier, Boolean custom);
  void computeCheckStatus();
  Map<String, CheckStatusEntity> getCheckStatusByAccountIdAndIdentifiers(
      String accountIdentifier, List<String> identifiers);
}
