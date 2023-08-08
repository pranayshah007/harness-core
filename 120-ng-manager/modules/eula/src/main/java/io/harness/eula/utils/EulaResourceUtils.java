/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.eula.dto.EulaDTO;
import io.harness.spec.server.ng.v1.model.EulaSignRequest;

import com.google.inject.Singleton;
import org.apache.commons.lang3.EnumUtils;

@Singleton
@OwnedBy(HarnessTeam.PL)
public class EulaResourceUtils {
  public EulaDTO toEulaDTO(EulaSignRequest eulaSignRequest, String accountIdentifier) {
    return EulaDTO.builder()
        .accountIdentifier(accountIdentifier)
        .agreement(EnumUtils.getEnum(AgreementType.class, eulaSignRequest.getAgreementType().toString()))
        .build();
  }
}