/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.ccm.msp.service.intf.MspValidationService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.List;

public class MspValidationServiceImpl implements MspValidationService {
  @Inject private ManagedAccountService managedAccountService;

  private static final String ACCOUNT_NOT_MSP_EXCEPTION = "Error is performing operation. Account is not MSP account";
  private static final String ACCOUNT_NOT_MANAGED_BY_MSP_EXCEPTION =
      "Error is performing operation. Account is not managed by given MSP account";

  @Override
  public void validateAccountIsMsp(String mspAccountId) {
    List<ManagedAccount> managedAccounts = managedAccountService.list(mspAccountId);
    if (managedAccounts == null || managedAccounts.size() == 0) {
      throw new InvalidRequestException(ACCOUNT_NOT_MSP_EXCEPTION);
    }
  }

  @Override
  public void validateAccountIsManagedByMspAccount(String mspAccountId, String managedAccountId) {
    List<ManagedAccount> managedAccounts = managedAccountService.list(mspAccountId);
    if (managedAccounts == null || managedAccounts.size() == 0) {
      throw new InvalidRequestException(ACCOUNT_NOT_MSP_EXCEPTION);
    }
    boolean isAccountManagedByMsp =
        managedAccounts.stream().anyMatch(managedAccount -> managedAccount.getAccountId().equals(managedAccountId));
    if (!isAccountManagedByMsp) {
      throw new InvalidRequestException(ACCOUNT_NOT_MANAGED_BY_MSP_EXCEPTION);
    }
  }
}
