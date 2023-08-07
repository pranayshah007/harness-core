/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.resources;

import static io.harness.idp.common.Constants.IDP_PERMISSION;
import static io.harness.idp.common.Constants.IDP_RESOURCE_TYPE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.scorecards.mappers.ScorecardMapper;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ScorecardsApi;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class ScorecardsApiImpl implements ScorecardsApi {
  private final ScorecardService scorecardService;

  @Inject
  public ScorecardsApiImpl(ScorecardService scorecardService) {
    this.scorecardService = scorecardService;
  }

  @Override
  public Response getScorecards(String harnessAccount) {
    List<Scorecard> scorecards = scorecardService.getAllScorecardsAndChecksDetails(harnessAccount);
    return Response.status(Response.Status.OK).entity(ScorecardMapper.toResponseList(scorecards)).build();
  }

  @Override
  public Response getScorecard(String scorecardId, String harnessAccount) {
    try {
      ScorecardDetailsResponse response = scorecardService.getScorecardDetails(harnessAccount, scorecardId);
      return Response.status(Response.Status.OK).entity(response).build();
    } catch (Exception e) {
      String errorMessage =
          String.format("Error occurred while fetching scorecard details for accountId: [%s], scorecardId: [%s]",
              harnessAccount, scorecardId);
      log.error(errorMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response createScorecard(@Valid ScorecardDetailsRequest body, @AccountIdentifier String harnessAccount) {
    try {
      scorecardService.saveScorecard(body, harnessAccount);
      return Response.status(Response.Status.CREATED).build();
    } catch (Exception e) {
      log.error("Could not create scorecard", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_RESOURCE_TYPE, permission = IDP_PERMISSION)
  public Response updateScorecard(String scorecardId, @Valid ScorecardDetailsRequest body, String harnessAccount) {
    try {
      scorecardService.updateScorecard(body, harnessAccount);
      return Response.status(Response.Status.OK).build();
    } catch (Exception e) {
      log.error("Could not update scorecard", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
