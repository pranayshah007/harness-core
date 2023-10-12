/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.services;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;

import java.util.List;

@OwnedBy(GTM)
public interface AccountService {
  AccountDTO createAccount(AccountDTO accountDTO);
  Boolean updateDefaultExperienceIfApplicable(String accountId, DefaultExperience defaultExperience);
  String getBaseUrl(String accountId);
  AccountDTO getAccount(String accountId);
  AccountDTO getOnPremAccount();
  List<String> getAdminUsers(String accountId);
}
