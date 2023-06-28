/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import io.harness.account.AccountClient;
import io.harness.ccm.msp.dao.ManagedAccountDao;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import java.util.List;

public class ManagedAccountServiceImpl implements ManagedAccountService {
  @Inject private ManagedAccountDao managedAccountDao;
  @Inject private AccountClient accountClient;

  private static final String INVALID_MSP_ACCOUNT_ID_EXCEPTION = "Invalid msp account id";

  private static final String INVALID_MANAGED_ACCOUNT_ID_EXCEPTION = "Invalid managed account id.";

  @Override
  public String save(String mspAccountId, String managedAccountId) {
    AccountDTO mspAccountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(mspAccountId));
    AccountDTO managedAccountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(managedAccountId));
    if (mspAccountDTO != null && managedAccountDTO != null) {
      return managedAccountDao.save(ManagedAccount.builder()
                                        .accountName(managedAccountDTO.getName())
                                        .accountId(managedAccountId)
                                        .mspAccountId(mspAccountId)
                                        .build());
    } else {
      if (managedAccountDTO == null) {
        throw new InvalidRequestException(INVALID_MANAGED_ACCOUNT_ID_EXCEPTION);
      } else {
        throw new InvalidRequestException(INVALID_MSP_ACCOUNT_ID_EXCEPTION);
      }
    }
  }

  @Override
  public ManagedAccount get(String mspAccountId, String managedAccountId) {
    return managedAccountDao.getDetailsForAccount(mspAccountId, managedAccountId);
  }

  @Override
  public List<ManagedAccount> list(String mspAccountId) {
    return managedAccountDao.list(mspAccountId);
  }

  @Override
  public ManagedAccount update(ManagedAccount managedAccount) {
    return managedAccountDao.update(managedAccount);
  }

  @Override
  public boolean delete(String accountId) {
    return managedAccountDao.delete(accountId);
  }
}
