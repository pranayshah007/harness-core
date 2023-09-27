/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.CreditType;
import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.Credit;
import io.harness.credit.utils.CreditStatus;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

@OwnedBy(GTM)
@HarnessRepo
@Transactional
public interface CreditRepository extends CrudRepository<Credit, String> {
  List<Credit> findByAccountIdentifier(String accountIdentifier);

  List<Credit> findByAccountIdentifierAndCreditTypeAndCreditStatus(
      String accountIdentifier, CreditType creditType, CreditStatus creditStatus);
}
