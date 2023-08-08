/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;
import io.harness.eula.entity.Eula;
import io.harness.eula.events.EulaSignEvent;
import io.harness.eula.mapper.EulaMapper;
import io.harness.eula.service.EulaService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.eula.spring.EulaRepository;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EulaServiceImpl implements EulaService {
  EulaRepository eulaRepository;
  EulaMapper eulaMapper;
  OutboxService outboxService;
  TransactionTemplate transactionTemplate;

  @Override
  public boolean sign(EulaDTO eulaDTO) {
    if (isSigned(eulaDTO.getAgreement(), eulaDTO.getAccountIdentifier())) {
      return false;
    }
    Eula newEula = eulaMapper.toEntity(eulaDTO);
    Optional<Eula> existingEula = get(eulaDTO.getAccountIdentifier());
    if (existingEula.isPresent()) {
      Set<AgreementType> signedAgreements = existingEula.get().getSignedAgreements();
      signedAgreements.add(eulaDTO.getAgreement());
      newEula.setSignedAgreements(signedAgreements);
    }
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Eula eula = eulaRepository.upsert(newEula);
      // send an audit event that an End Level User Agreement has been signed for an account.
      outboxService.save(new EulaSignEvent(eula.getAccountIdentifier(), eulaDTO.getAgreement()));
      return true;
    }));
  }

  @Override
  public boolean isSigned(AgreementType agreementType, String accountIdentifier) {
    Optional<Eula> eula = get(accountIdentifier);
    return eula.isPresent() && eula.get().getSignedAgreements().contains(agreementType);
  }

  private Optional<Eula> get(String accountIdentifier) {
    return eulaRepository.findByAccountIdentifier(accountIdentifier);
  }
}