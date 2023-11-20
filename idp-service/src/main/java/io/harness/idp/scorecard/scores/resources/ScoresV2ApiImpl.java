/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.scores.service.ScoreComputerService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ScoresV2Api;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateRequest;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateResponseV2;

import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
public class ScoresV2ApiImpl implements ScoresV2Api {
  private ScoreComputerService scoreComputerService;

  @Override
  public Response scorecardRecalibrateV2(
      @Valid ScorecardRecalibrateRequest scorecardRecalibrateRequest, @AccountIdentifier String harnessAccount) {
    try {
      ScorecardRecalibrateInfo scorecardRecalibrateInfo = scoreComputerService.computeScoresAsync(harnessAccount,
          scorecardRecalibrateRequest.getIdentifiers().getScorecardIdentifier(),
          scorecardRecalibrateRequest.getIdentifiers().getEntityIdentifier());
      ScorecardRecalibrateResponseV2 responseV2 = new ScorecardRecalibrateResponseV2();
      responseV2.setInfo(scorecardRecalibrateInfo);
      return Response.status(Response.Status.CREATED).entity(responseV2).build();
    } catch (Exception e) {
      log.error("Error in triggering score computation for account - {},  entity - {} and scorecard - {}, error = {}",
          harnessAccount, scorecardRecalibrateRequest.getIdentifiers().getEntityIdentifier(),
          scorecardRecalibrateRequest.getIdentifiers().getScorecardIdentifier(), e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}