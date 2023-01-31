/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.validation;

import io.harness.account.AccountClient;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.account.AccountTrustLevel;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CIAccountValidationServiceImpl implements CIAccountValidationService {
  @Inject private CIMiningPatternJob ciMiningPatternJob;
  @Inject private UserClient userClient;
  @Inject private AccountClient accountClient;

  @Inject
  public CIAccountValidationServiceImpl() {}

  @Override
  public boolean isAccountValidForExecution(String accountId) {
    Integer trustLevel = CGRestUtils.getResponse(accountClient.getAccountTrustLevel(accountId));

    if (AccountTrustLevel.UNINITIALIZED.equals(trustLevel)) {
      trustLevel = initializeAccountTrustLevel(accountId);
      Boolean result = CGRestUtils.getResponse(accountClient.updateAccountTrustLevel(accountId, trustLevel));
      if (result == false) {
        throw new CIStageExecutionException("Account is not trusted for CI build. Please reach support@harness.io");
      }
    }

    if (trustLevel.equals(AccountTrustLevel.NEW_USER)) {
      throw new CIStageExecutionException("Account is not trusted for CI build. Please reach support@harness.io");
    }

    return true;
  }

  private Integer initializeAccountTrustLevel(String accountId) {
    Set<String> domains = ciMiningPatternJob.getValidDomains();

    List<UserInfo> users = CGRestUtils.getResponse(userClient.listUsersEmails(accountId));

    List<String> usersEmail = users.stream().map(UserInfo::getEmail).collect(Collectors.toList());

    for (String email : usersEmail) {
      String domain = email.split("@")[1];

      if (domains.contains(domain)) {
        return AccountTrustLevel.BASIC_USER;
      }
    }

    return AccountTrustLevel.NEW_USER;
  }
}
