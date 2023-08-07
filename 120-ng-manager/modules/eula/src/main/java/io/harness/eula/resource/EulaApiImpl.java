/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.resource;

import io.harness.eula.dto.EulaDTO;
import io.harness.eula.service.EulaService;
import io.harness.eula.utils.EulaResourceUtils;
import io.harness.spec.server.ng.v1.EulaApi;
import io.harness.spec.server.ng.v1.model.AgreementType;
import io.harness.spec.server.ng.v1.model.EulaSignRequest;
import io.harness.spec.server.ng.v1.model.EulaSignResponse;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EulaApiImpl implements EulaApi {
  private EulaService eulaService;
  private EulaResourceUtils eulaResourceUtils;

  @Override
  public Response signEula(@Valid EulaSignRequest body, String harnessAccount) {
    EulaDTO eulaDTO = eulaResourceUtils.toEulaDTO(body, harnessAccount);
    boolean isSigned = eulaService.sign(eulaDTO);
    String responseMessage;
    if (isSigned) {
      responseMessage =
          String.format("Successfully signed End Level User Agreement for %s.", eulaDTO.getAgreement().toString());
    } else {
      responseMessage =
          String.format("An End Level User Agreement is already signed for %s.", eulaDTO.getAgreement().toString());
    }
    return Response.ok().entity(new EulaSignResponse().signed(true).message(responseMessage)).build();
  }

  @Override
  public Response validateEulaSign(@NotNull AgreementType agreementType, String harnessAccount) {
    boolean isSigned = eulaService.isSigned(
        EnumUtils.getEnum(io.harness.eula.AgreementType.class, agreementType.toString()), harnessAccount);
    String responseMessage;
    if (isSigned) {
      responseMessage = String.format("An End Level User Agreement is signed for %s.", agreementType);
    } else {
      responseMessage = String.format("An End Level User Agreement is not yet signed for %s.", agreementType);
    }
    return Response.ok().entity(new EulaSignResponse().signed(isSigned).message(responseMessage)).build();
  }
}