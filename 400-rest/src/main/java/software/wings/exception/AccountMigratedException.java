/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(HarnessTeam.SPG)
public class AccountMigratedException extends WingsException {
  public AccountMigratedException(String accountId) {
    super(String.format("Account %s migrated to NextGen", accountId), null, ErrorCode.ACCOUNT_MIGRATED, Level.ERROR,
        USER, null);
  }
}
