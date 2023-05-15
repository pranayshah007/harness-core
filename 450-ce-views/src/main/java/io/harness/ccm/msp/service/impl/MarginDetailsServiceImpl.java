/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.ManagedAccountDetails;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class MarginDetailsServiceImpl implements MarginDetailsService {
  @Inject MarginDetailsDao marginDetailsDao;

  @Override
  public String save(MarginDetails marginDetails) {
    return marginDetailsDao.save(marginDetails);
  }

  @Override
  public String addManagedAccount(String mspAccountId, String managedAccountId, String managedAccountName) {
    return marginDetailsDao.save(MarginDetails.builder()
                                     .accountId(managedAccountId)
                                     .accountName(managedAccountName)
                                     .mspAccountId(mspAccountId)
                                     .build());
  }

  @Override
  public MarginDetails update(MarginDetails marginDetails) {
    return marginDetailsDao.update(marginDetails);
  }

  @Override
  public MarginDetails unsetMargins(String uuid, String accountId) {
    return marginDetailsDao.unsetMarginRules(uuid, accountId);
  }

  @Override
  public MarginDetails get(String uuid) {
    return marginDetailsDao.get(uuid);
  }

  @Override
  public MarginDetails get(String mspAccountId, String managedAccountId) {
    return marginDetailsDao.getMarginDetailsForAccount(mspAccountId, managedAccountId);
  }

  @Override
  public List<MarginDetails> list(String mspAccountId) {
    return marginDetailsDao.list(mspAccountId);
  }

  @Override
  public List<ManagedAccountDetails> listManagedAccountDetails(String mspAccountId) {
    List<MarginDetails> marginDetailsList = marginDetailsDao.list(mspAccountId);
    List<ManagedAccountDetails> managedAccountDetails = new ArrayList<>();
    marginDetailsList.forEach(marginDetails
        -> managedAccountDetails.add(ManagedAccountDetails.builder()
                                         .accountId(marginDetails.getAccountId())
                                         .accountName(marginDetails.getAccountName())
                                         .build()));
    return managedAccountDetails;
  }
}
