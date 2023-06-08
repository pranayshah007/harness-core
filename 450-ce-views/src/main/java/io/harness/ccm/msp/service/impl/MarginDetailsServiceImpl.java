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
import io.harness.ccm.msp.service.intf.MarginDetailsBqService;
import io.harness.ccm.msp.service.intf.MarginDetailsService;
import io.harness.ccm.msp.service.intf.MspValidationService;

import com.google.inject.Inject;
import java.util.List;

public class MarginDetailsServiceImpl implements MarginDetailsService {
  @Inject private MarginDetailsDao marginDetailsDao;
  @Inject private MarginDetailsBqService marginDetailsBqService;
  @Inject private MspValidationService mspValidationService;

  @Override
  public String save(MarginDetails marginDetails) {
    mspValidationService.validateAccountIsManagedByMspAccount(
        marginDetails.getMspAccountId(), marginDetails.getAccountId());
    String uuid = marginDetailsDao.save(marginDetails);
    marginDetailsBqService.insertMarginDetailsInBQ(marginDetails);
    return uuid;
  }

  @Override
  public MarginDetails get(String mspAccountId, String managedAccountId) {
    mspValidationService.validateAccountIsManagedByMspAccount(mspAccountId, managedAccountId);
    return marginDetailsDao.getMarginDetailsForAccount(mspAccountId, managedAccountId);
  }

  @Override
  public List<MarginDetails> list(String mspAccountId) {
    return marginDetailsDao.list(mspAccountId);
  }

  @Override
  public MarginDetails update(MarginDetails marginDetails) {
    mspValidationService.validateAccountIsManagedByMspAccount(
        marginDetails.getMspAccountId(), marginDetails.getAccountId());
    MarginDetails updatedMarginDetails = marginDetailsDao.update(marginDetails);
    marginDetailsBqService.updateMarginDetailsInBQ(updatedMarginDetails);
    return updatedMarginDetails;
  }

  @Override
  public MarginDetails unsetMargins(String uuid) {
    MarginDetails marginDetails = marginDetailsDao.get(uuid);
    mspValidationService.validateAccountIsManagedByMspAccount(
        marginDetails.getMspAccountId(), marginDetails.getAccountId());
    MarginDetails updatedMarginDetails = marginDetailsDao.unsetMarginRules(uuid);
    marginDetailsBqService.updateMarginDetailsInBQ(updatedMarginDetails);
    return updatedMarginDetails;
  }

  @Override
  public void updateMarkupAmount(String mspAccountId, String managedAccountId, AmountDetails markupAmountDetails,
      AmountDetails totalSpendDetails) {
    mspValidationService.validateAccountIsManagedByMspAccount(mspAccountId, managedAccountId);
    marginDetailsDao.updateMarkupAndTotalSpend(mspAccountId, managedAccountId, markupAmountDetails, totalSpendDetails);
  }
}