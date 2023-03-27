/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.credit.services.CreditService;
import io.harness.repositories.CreditRepository;

import com.google.inject.Inject;

@OwnedBy(GTM)
public class CreditServiceImpl implements CreditService {
  private final CreditRepository creditRepository;
  @Inject
  public CreditServiceImpl(CreditRepository creditRepository) {
    this.creditRepository = creditRepository;
  }

  @Override
  public void purchaseCredits(String accountIdentifier) {
    // Todo: need to determine the details later on, so fat its a temporary dummy code
    Credit buildCredits = CICredit.builder().build();
    buildCredits.setAccountIdentifier(accountIdentifier);
    buildCredits.setQuantity(10000);
    buildCredits.setPurchaseTime(System.currentTimeMillis());
    creditRepository.save(buildCredits);
  }
}
