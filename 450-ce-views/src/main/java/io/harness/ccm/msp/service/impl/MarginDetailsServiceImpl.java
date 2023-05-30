/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.AmountDetails;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsService;

import com.google.inject.Inject;
import java.util.List;

public class MarginDetailsServiceImpl implements MarginDetailsService {
  @Inject MarginDetailsDao marginDetailsDao;

  @Override
  public String save(MarginDetails marginDetails) {
    return marginDetailsDao.save(marginDetails);
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
  public MarginDetails update(MarginDetails marginDetails) {
    return marginDetailsDao.update(marginDetails);
  }

  @Override
  public MarginDetails unsetMargins(String uuid) {
    return marginDetailsDao.unsetMarginRules(uuid);
  }

  @Override
  public void updateMarkupAmount(String mspAccountId, String managedAccountId, AmountDetails markupAmountDetails,
      AmountDetails totalSpendDetails) {
    marginDetailsDao.updateMarkupAndTotalSpend(mspAccountId, managedAccountId, markupAmountDetails, totalSpendDetails);
  }
}