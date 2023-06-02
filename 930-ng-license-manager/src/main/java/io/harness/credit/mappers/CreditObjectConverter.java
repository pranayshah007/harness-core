/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.mappers;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.Credit;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CreditObjectConverter {
  @Inject Map<ModuleType, CreditObjectMapper> mapperMap;

  public <T extends CreditDTO> T toDTO(Credit credit) {
    ModuleType moduleType = credit.getModuleType();
    CreditDTO creditDTO = mapperMap.get(moduleType).toDTO(credit);
    creditDTO.setId(credit.getId());
    creditDTO.setAccountIdentifier(credit.getAccountIdentifier());
    creditDTO.setCreditStatus(credit.getCreditStatus());
    creditDTO.setQuantity(credit.getQuantity());
    creditDTO.setPurchaseTime(credit.getPurchaseTime());
    creditDTO.setExpiryTime(credit.getExpiryTime());
    creditDTO.setCreditType(credit.getCreditType());
    creditDTO.setModuleType(credit.getModuleType());
    return (T) creditDTO;
  }

  public <T extends Credit> T toEntity(CreditDTO creditDTO) {
    ModuleType moduleType = creditDTO.getModuleType();
    Credit credit = mapperMap.get(moduleType).toEntity(creditDTO);
    credit.setId(creditDTO.getId());
    credit.setAccountIdentifier(creditDTO.getAccountIdentifier());
    credit.setCreditStatus(creditDTO.getCreditStatus());
    credit.setQuantity(creditDTO.getQuantity());
    credit.setPurchaseTime(creditDTO.getPurchaseTime());
    credit.setExpiryTime(creditDTO.getExpiryTime());
    credit.setCreditType(creditDTO.getCreditType());
    credit.setModuleType(creditDTO.getModuleType());
    return (T) credit;
  }
}
