/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository,
 * also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.services.impl;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.CreditType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.Credit;
import io.harness.credit.mappers.CreditObjectConverter;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.CreditRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    List<Credit> totalCredits = creditRepository.findByAccountIdentifier(accountIdentifier);
    List<Credit> activeCredits = new ArrayList<>();
    List<Credit> inactiveCredits = new ArrayList<>();

    totalCredits.forEach(credit -> {
      if (CreditStatus.ACTIVE.equals(credit.getCreditStatus())) {
        activeCredits.add(credit);
      } else {
        inactiveCredits.add(credit);
      }
    });
    activeCredits.sort(Comparator.comparingLong(Credit::getExpiryTime));
    inactiveCredits.sort(Comparator.comparingLong(Credit::getExpiryTime));

    totalCredits = activeCredits;
    totalCredits.addAll(inactiveCredits);

    return totalCredits.stream().map(creditObjectConverter::<CreditDTO>toDTO).collect(Collectors.toList());
  }

  @Override
  public List<CreditDTO> getCredits(String accountIdentifier, CreditType creditType, CreditStatus creditStatus) {
    List<Credit> credits = creditRepository.findByAccountIdentifierAndCreditTypeAndCreditStatus(
        accountIdentifier, CreditType.FREE, CreditStatus.ACTIVE);
    credits.sort(Comparator.comparingLong(Credit::getPurchaseTime).reversed());

    return credits.stream().map(creditObjectConverter::<CreditDTO>toDTO).collect(Collectors.toList());
  }

  @Override
  public CreditDTO purchaseCredit(String accountIdentifier, CreditDTO creditDTO) {
    if (!accountIdentifier.equals(creditDTO.getAccountIdentifier())) {
      String errorMessage =
          String.format("AccountIdentifier: [%s] did not match with the Credit information", accountIdentifier);
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    Credit credit = creditObjectConverter.toEntity(creditDTO);
    credit.setAccountIdentifier(accountIdentifier);
    Credit savedCredit = creditRepository.save(credit);
    return creditObjectConverter.toDTO(savedCredit);
  }

  @Override
  public void setCreditStatusExpired(Credit entity) {
    entity.setCreditStatus(CreditStatus.EXPIRED);
    creditRepository.save(entity);
  }
}
