/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.validation;

import io.harness.account.AccountClient;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.creditcard.remote.CreditCardClient;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.subscription.responses.AccountCreditCardValidationResponse;
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
  @Inject private CreditCardClient creditCardClient;
  @Inject private ExecutionLimits executionLimits;
  @Inject CIFeatureFlagService ciFeatureFlagService;

  public static long APPLY_DAY = 1678368362000L; // 09.3.23 Day to apply the policy. In milliseconds

  @Inject
  public CIAccountValidationServiceImpl() {}

  Integer getTrustLevel(String accountId) {
    int trustLevel = AccountTrustLevel.BASIC_USER;

    try {
      trustLevel = CGRestUtils.getResponse(accountClient.getAccountTrustLevel(accountId));

      if (AccountTrustLevel.UNINITIALIZED == trustLevel) {
        trustLevel = initializeAccountTrustLevel(accountId);
        Boolean result = CGRestUtils.getResponse(accountClient.updateAccountTrustLevel(accountId, trustLevel));
        if (result == false) {
          log.warn("Error updating account trust level");
        }
      }
    } catch (Exception e) {
      log.error("Error in setting account trust level. Proceeding as regular. {}", e);
      trustLevel = AccountTrustLevel.BASIC_USER;
    }

    return trustLevel;
  }

  @Override
  public boolean isAccountValidForExecution(String accountId) {
    Integer trustLevel = AccountTrustLevel.BASIC_USER;
    try {
      LicensesWithSummaryDTO ciLicense = ciLicenseService.getLicenseSummary(accountId);
      if (ciLicense == null) {
        throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
      }

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

      if (AccountTrustLevel.UNINITIALIZED == trustLevel) {
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

  @Override
  public long getMaxBuildPerDay(String accountId) {
    int trustLevel = obtainTrustLevel(accountId);

    ExecutionLimitSpec freeBasicUserLimits = executionLimits.getFreeBasicUser();
    ExecutionLimitSpec freeNewUserLimits = executionLimits.getFreeNewUser();

    switch (trustLevel) {
      case AccountTrustLevel.BASIC_USER:
        return freeBasicUserLimits.getDailyMaxBuildsCount();
      case AccountTrustLevel.NEW_USER:
        if (ciFeatureFlagService.isEnabled(FeatureName.CI_CREDIT_CARD_ONBOARDING, accountId) && hasValidCC(accountId)) {
          return freeBasicUserLimits.getDailyMaxBuildsCount();
        }
        return freeNewUserLimits.getDailyMaxBuildsCount();
      default:
        return freeNewUserLimits.getDailyMaxBuildsCount();
    }
  }

  @Override
  public long getMaxCreditsPerMonth(String accountId) {
    int trustLevel = obtainTrustLevel(accountId);

    ExecutionLimitSpec freeBasicUserLimits = executionLimits.getFreeBasicUser();
    ExecutionLimitSpec freeNewUserLimits = executionLimits.getFreeNewUser();

    switch (trustLevel) {
      case AccountTrustLevel.BASIC_USER:
        return freeBasicUserLimits.getMonthlyMaxCreditsCount();
      case AccountTrustLevel.NEW_USER:
        if (ciFeatureFlagService.isEnabled(FeatureName.CI_CREDIT_CARD_ONBOARDING, accountId) && hasValidCC(accountId)) {
          return freeBasicUserLimits.getMonthlyMaxCreditsCount();
        }
        return freeNewUserLimits.getMonthlyMaxCreditsCount();
      default:
        return freeNewUserLimits.getMonthlyMaxCreditsCount();
    }
  }

  private Integer obtainTrustLevel(String accountId) {
    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }

    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() != Edition.FREE) {
      throw new IllegalArgumentException("Got Max builds per day check for non free license");
    }

    int trustLevel = 0;

    AccountDTO accountDto = CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));
    Set<String> whiteListed = ciMiningPatternJob.getWhiteListed();

    if (accountDto.getCreatedAt() < APPLY_DAY || whiteListed.contains(accountId)
        || ciExecutionServiceConfig.isLocal()) {
      trustLevel = AccountTrustLevel.BASIC_USER;
    } else {
      trustLevel = getTrustLevel(accountId);
    }
    return trustLevel;
  }

  private boolean hasValidCC(String accountId) {
    AccountCreditCardValidationResponse response;
    try {
      response = NGRestUtils.getResponse(creditCardClient.validateCreditCard(accountId));
    } catch (Exception e) {
      log.error("Exception occurred while checking for valid credit cards", e);
      return true;
    }
    return response.isHasAtleastOneValidCreditCard();
  }

  private Integer initializeAccountTrustLevel(String accountId) {
    if (ciExecutionServiceConfig.isLocal()) {
      return AccountTrustLevel.BASIC_USER;
    }
    Set<String> validDomains = ciMiningPatternJob.getValidDomains();
    Set<String> whiteListedAccounts = ciMiningPatternJob.getWhiteListed();
    if (whiteListedAccounts.contains(accountId)) {
      return AccountTrustLevel.BASIC_USER;
    }

    List<UserInfo> users = CGRestUtils.getResponse(userClient.listUsersEmails(accountId));

    List<String> usersEmail = users.stream().map(UserInfo::getEmail).collect(Collectors.toList());

    for (String email : usersEmail) {
      String domain = email.split("@")[1];
      if (validDomains.contains(domain)) {
        return AccountTrustLevel.BASIC_USER;
      }
    }

    return AccountTrustLevel.NEW_USER;
  }
}
