/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckListItem;

import java.util.List;

public interface CheckService {
  void createCheck(CheckDetails checkDetails, String accountIdentifier);
  void updateCheck(CheckDetails checkDetails, String accountIdentifier);
  List<CheckListItem> getChecksByAccountId(boolean custom, String accountIdentifier);
  List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers);
  CheckDetails getCheckDetails(String accountIdentifier, String identifier);
  List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, List<String> identifiers);
  void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete);
}
