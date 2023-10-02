/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;

import software.wings.beans.Account;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public class AccountMapper {
  public static AccountDTO toAccountDTO(Account account) {
    return AccountDTO.builder()
        .identifier(account.getUuid())
        .name(account.getAccountName())
        .companyName(account.getCompanyName())
        .defaultExperience(account.getDefaultExperience())
        .isCrossGenerationAccessEnabled(account.isCrossGenerationAccessEnabled())
        .authenticationMechanism(account.getAuthenticationMechanism())
        .isNextGenEnabled(account.isNextGenEnabled())
        .serviceAccountConfig(account.getServiceAccountConfig())
        .isProductLed(account.isProductLed())
        .isTwoFactorAdminEnforced(account.isTwoFactorAdminEnforced())
        .ringName(account.getRingName())
        .createdAt(account.getCreatedAt())
        .sessionTimeoutInMinutes(account.getSessionTimeOutInMinutes())
        .publicAccessEnabled(account.isPublicAccessEnabled())
        .build();
  }

  public static Account fromAccountDTO(AccountDTO dto) {
    Account account = Account.Builder.anAccount()
                          .withUuid(dto.getIdentifier())
                          .withAccountName(dto.getName())
                          .withCompanyName(dto.getCompanyName())
                          .withDefaultExperience(dto.getDefaultExperience())
                          .withNextGenEnabled(dto.isNextGenEnabled())
                          .withServiceAccountConfig(dto.getServiceAccountConfig())
                          .withPublicAccessEnabled(dto.isPublicAccessEnabled())
                          .build();
    if (dto.getSessionTimeoutInMinutes() != null) {
      account.setSessionTimeOutInMinutes(dto.getSessionTimeoutInMinutes());
    }
    return account;
  }

  public static GatewayAccountRequestDTO toGatewayAccountRequest(Account account) {
    return GatewayAccountRequestDTO.builder()
        .uuid(account.getUuid())
        .accountName(account.getAccountName())
        .companyName(account.getCompanyName())
        .createdFromNG(account.isCreatedFromNG())
        .isNextGenEnabled(account.isNextGenEnabled())
        .defaultExperience(account.getDefaultExperience())
        .build();
  }
}
