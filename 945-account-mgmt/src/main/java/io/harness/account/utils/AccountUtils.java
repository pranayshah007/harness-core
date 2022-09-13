/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account.utils;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class AccountUtils {
  private static final int ACCOUNT_NAME_RETRIES = 20;

  private final AccountClient accountClient;

  private static final SecureRandom random = new SecureRandom();

  /**
   * Creates a string by taking an email username and appends "-x" (where x is a random number between 1000-9999).
   * If this string is a duplicate account name we repeat the process.
   * @param email
   * @return A unique account name
   */
  public String generateAccountName(String email) {
    String username = email.split("@")[0];

    int count = 0;

    while (count < ACCOUNT_NAME_RETRIES) {
      String newAccountName = username + "-" + (1000 + random.nextInt(9000));
      if (!isDuplicateAccountName(newAccountName)) {
        return newAccountName;
      }
      count++;
    }

    throw new GeneralException(String.format("Failed to generate a unique Account Name for email=%s", email));
  }

  private boolean isDuplicateAccountName(String accountName) {
    return NGRestUtils.getCgResponse(accountClient.doesAccountExist(accountName));
  }
}
