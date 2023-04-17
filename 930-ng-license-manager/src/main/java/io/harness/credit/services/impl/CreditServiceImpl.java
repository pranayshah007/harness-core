/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository,
 * also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.credit.mappers.CreditObjectConverter;
import io.harness.credit.services.CreditService;
import io.harness.repositories.CreditRepository;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(GTM)
public class CreditServiceImpl implements CreditService {
  private final CreditObjectConverter creditObjectConverter;
  private final CreditRepository creditRepository;
  @Inject
  public CreditServiceImpl(CreditObjectConverter creditObjectConverter, CreditRepository creditRepository) {
    this.creditObjectConverter = creditObjectConverter;
    this.creditRepository = creditRepository;
  }

  @Override
  public List<CreditDTO> getCredits(String accountIdentifier) {
    return getCreditsByAccountId(accountIdentifier);
  }

  private List<CreditDTO> getCreditsByAccountId(String accountIdentifier) {
    List<Credit> credits = creditRepository.findByAccountIdentifier(accountIdentifier);
    credits.sort(Comparator.comparingLong(Credit::getExpiryTime));
    return credits.stream().map(creditObjectConverter::<CreditDTO>toDTO).collect(Collectors.toList());
  }

  @Override
  public void purchaseCredits(String accountIdentifier) {
    // Todo: need to determine the details later on, so fat its a temporary
    // dummy code just to hard code value in db and will replace it in the future
    Credit buildCredits = CICredit.builder().build();
    buildCredits.setAccountIdentifier(accountIdentifier);
    buildCredits.setQuantity(10000);
    buildCredits.setPurchaseTime(System.currentTimeMillis());
    buildCredits.setModuleType(ModuleType.CI);
    creditRepository.save(buildCredits);
  }
}
