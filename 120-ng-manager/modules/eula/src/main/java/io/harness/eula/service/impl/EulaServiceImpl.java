/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;
import io.harness.eula.entity.Eula;
import io.harness.eula.mapper.EulaMapper;
import io.harness.eula.service.EulaService;
import io.harness.repositories.eula.spring.EulaRepository;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class EulaServiceImpl implements EulaService {
  @Inject EulaRepository eulaRepository;
  @Inject EulaMapper eulaMapper;

  @Override
  public boolean sign(EulaDTO eulaDTO) {
    Eula newEula = eulaMapper.toEntity(eulaDTO);
    Optional<Eula> existingEula = get(eulaDTO.getAccountIdentifier());
    if (existingEula.isPresent()) {
      Set<AgreementType> signedAgreements = existingEula.get().getSignedAgreements();
      signedAgreements.add(eulaDTO.getAgreement());
      newEula.setSignedAgreements(signedAgreements);
    }
    eulaRepository.upsert(newEula);
    return true;
  }

  @Override
  public boolean isSigned(EulaDTO eulaDTO) {
    Optional<Eula> eula = get(eulaDTO.getAccountIdentifier());
    return eula.isPresent() && eula.get().getSignedAgreements().contains(eulaDTO.getAgreement());
  }

  private Optional<Eula> get(String accountIdentifier) {
    return eulaRepository.findByAccountIdentifier(accountIdentifier);
  }
}