/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.services;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.Credit;

import java.util.List;

@OwnedBy(GTM)
public interface CreditService {
  List<CreditDTO> getCredits(String accountIdentifier);

  CreditDTO purchaseCredit(String accountIdentifier, CreditDTO creditDTO);

  void setCreditStatusExpired(Credit entity);
}
