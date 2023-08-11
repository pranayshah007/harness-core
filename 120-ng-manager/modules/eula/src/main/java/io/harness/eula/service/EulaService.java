/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Service allows Users to sign End User License Agreements for an account.
 * Add different Agreements which needs to be signed with enum AgreementType
 * and stores all the signed agreements for an account in eula db.
 */
@OwnedBy(PL)
public interface EulaService {
  /**
   * Signs an End User License Agreement for an account.
   *
   * @param eulaDTO containing accountId and agreementType to be signed
   * @return true, if agreement is signed successfully
   */
  boolean sign(@NotNull EulaDTO eulaDTO);

  /**
   * Checks whether an End User License Agreement is signed for an account and agreementType.
   *
   * @param agreementType the agreementType
   * @param accountIdentifier the accountId
   * @return true, if agreement is signed
   */
  boolean isSigned(@NotNull AgreementType agreementType, @NotEmpty String accountIdentifier);
}