/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.status.resource.IDPStatusResource;
import io.harness.idp.status.response.IDPStatusDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class IDPStatusResourceImpl implements IDPStatusResource {
  @Override
  public ResponseDTO<IDPStatusDTO> getIDPStatus(@NotEmpty String accountId) {
    // TODO Create Service & DAO layer once the approach is decided
    //  For now just exposing hardcoded API for FE to consume
    IDPStatusDTO idpStatusDTO = IDPStatusDTO.builder().accountId(accountId).build();
    return ResponseDTO.newResponse(idpStatusDTO);
  }
}
