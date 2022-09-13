/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.account.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.signup.dto.SignupDTO;

import com.google.inject.Inject;
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

    return NGRestUtils.getCgResponse(accountClient.create(accountDTO));
  }

  @Override
  public Boolean updateDefaultExperienceIfApplicable(String accountId, DefaultExperience defaultExperience) {
    return NGRestUtils.getCgResponse(accountClient.updateDefaultExperienceIfApplicable(accountId, defaultExperience));
  }

  @Override
  public String getBaseUrl(String accountId) {
    return NGRestUtils.getCgResponse(accountClient.getBaseUrl(accountId));
  }

  @Override
  public AccountDTO getAccount(String accountId) {
    return NGRestUtils.getCgResponse(accountClient.getAccountDTO(accountId));
  }
}
