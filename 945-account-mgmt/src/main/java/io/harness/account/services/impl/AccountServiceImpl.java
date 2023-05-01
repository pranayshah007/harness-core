/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.account.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageResponse;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.signup.dto.SignupDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(GTM)
public class AccountServiceImpl implements AccountService {
  private final AccountClient accountClient;

  @Override
  public AccountDTO createAccount(SignupDTO dto) {
    String username = dto.getEmail().split("@")[0];

    AccountDTO accountDTO = AccountDTO.builder()
                                .name(username)
                                .companyName(username)
                                .defaultExperience(DefaultExperience.NG)
                                .isProductLed(true)
                                .build();

    return CGRestUtils.getResponse(accountClient.create(accountDTO));
  }

  @Override
  public AccountDTO createAccount(AccountDTO accountDTO) {
    return CGRestUtils.getResponse(accountClient.create(accountDTO));
  }

  @Override
  public Boolean updateDefaultExperienceIfApplicable(String accountId, DefaultExperience defaultExperience) {
    return CGRestUtils.getResponse(accountClient.updateDefaultExperienceIfApplicable(accountId, defaultExperience));
  }

  @Override
  public String getBaseUrl(String accountId) {
    return CGRestUtils.getResponse(accountClient.getBaseUrl(accountId));
  }

  @Override
  public AccountDTO getAccount(String accountId) {
    return CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));
  }

  @Override
  public AccountDTO getOnPremAccount() {
    PageResponse<AccountDTO> pageResponse = CGRestUtils.getResponse(accountClient.listAccounts(0, 2));
    String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
    AccountDTO accountDTO = null;
    if (pageResponse.size() > 0 && !GLOBAL_ACCOUNT_ID.equals(pageResponse.getResponse().get(0).getIdentifier())) {
      accountDTO = pageResponse.getResponse().get(0);
    } else if (pageResponse.size() > 1) {
      accountDTO = pageResponse.getResponse().get(1);
    }
    return accountDTO;
  }

  @Override
  public List<String> getAdminUsers(String accountId) {
    List<String> userIds = CGRestUtils.getResponse(accountClient.getAccountAdmins(accountId));
    return isEmpty(userIds) ? new ArrayList<String>() : userIds;
  }
}
