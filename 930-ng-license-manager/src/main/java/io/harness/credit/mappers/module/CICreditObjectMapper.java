/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.mappers.module;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.entities.CICredit;
import io.harness.credit.mappers.CreditObjectMapper;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.GTM)
@Singleton
public class CICreditObjectMapper implements CreditObjectMapper<CICredit, CICreditDTO> {
  @Override
  public CICreditDTO toDTO(CICredit credit) {
    return CICreditDTO.builder().build();
  }

  @Override
  public CICredit toEntity(CICreditDTO creditDTO) {
    return CICredit.builder().build();
  }
}
