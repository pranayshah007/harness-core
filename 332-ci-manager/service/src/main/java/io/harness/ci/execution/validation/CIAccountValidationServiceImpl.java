/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.validation;

import io.harness.account.AccountClient;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.license.CILicenseService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class CIAccountValidationServiceImpl implements CIAccountValidationService {
  @Inject private CIMiningPatternJob ciMiningPatternJob;
  @Inject private UserClient userClient;
  @Inject private AccountClient accountClient;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CILicenseService ciLicenseService;

  private static long APPLY_DAY = 1678368362000L; // 09.3.23 Day to apply the policy. In milliseconds

  @Inject
  public CIAccountValidationServiceImpl() {}

  @Override
  public boolean isAccountValidForExecution(String accountId) {
    Integer trustLevel = AccountTrustLevel.BASIC_USER;
    try {
      LicensesWithSummaryDTO ciLicense = ciLicenseService.getLicenseSummary(accountId);
      if (ciLicense != null) {
        if (ciLicense.getEdition() == Edition.ENTERPRISE || ciLicense.getEdition() == Edition.TEAM
            || ciLicense.getLicenseType() == LicenseType.PAID) {
          return true;
        }
      }

      AccountDTO accountDto = CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));

      if (accountDto.getCreatedAt() < APPLY_DAY) {
        return true;
      }

      Set<String> whiteListed = ciMiningPatternJob.getWhiteListed();

      if (whiteListed.contains(accountId)) {
        return true;
      }

      trustLevel = CGRestUtils.getResponse(accountClient.getAccountTrustLevel(accountId));

      if (AccountTrustLevel.UNINITIALIZED.equals(trustLevel)) {
        trustLevel = initializeAccountTrustLevel(accountId);
        Boolean result = CGRestUtils.getResponse(accountClient.updateAccountTrustLevel(accountId, trustLevel));
        if (result == false) {
          log.info("Error updating account trust level");
        }
      }
    } catch (Exception e) {
      log.info("Error in setting account trust level. Proceeding as regular. {}", e);
      trustLevel = AccountTrustLevel.BASIC_USER;
    }

    if (trustLevel < AccountTrustLevel.BASIC_USER) {
      throw new CIStageExecutionException(
          "Your domain is not trusted for CI hosted builds. Please reach support@harness.io");
    }

    return true;
  }

  private Integer initializeAccountTrustLevel(String accountId) {
    if (ciExecutionServiceConfig.isLocal()) {
      return AccountTrustLevel.BASIC_USER;
    }
    Set<String> validDomains = ciMiningPatternJob.getValidDomains();
    Set<String> whiteListed = ciMiningPatternJob.getWhiteListed();

    List<UserInfo> users = CGRestUtils.getResponse(userClient.listUsersEmails(accountId));

    List<String> usersEmail = users.stream().map(UserInfo::getEmail).collect(Collectors.toList());

    for (String email : usersEmail) {
      String domain = email.split("@")[1];

      if (validDomains.contains(domain) || whiteListed.contains(domain)) {
        return AccountTrustLevel.BASIC_USER;
      }
    }

    return AccountTrustLevel.NEW_USER;
  }
}
