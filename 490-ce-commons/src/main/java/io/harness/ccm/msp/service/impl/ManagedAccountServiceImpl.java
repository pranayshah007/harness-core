/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import io.harness.ccm.msp.dao.ManagedAccountDao;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.service.intf.ManagedAccountService;

import com.google.inject.Inject;
import java.util.List;

public class ManagedAccountServiceImpl implements ManagedAccountService {
  @Inject private ManagedAccountDao managedAccountDao;

  @Override
  public String save(ManagedAccount managedAccount) {
    return managedAccountDao.save(managedAccount);
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
