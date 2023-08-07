package io.harness.idp.scorecard.scores.resources;

import io.harness.spec.server.idp.v1.ScoresApi;

import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.NotNull;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

public class ScoreApiImpl implements ScoresApi {
  @Override
  public Response getAllScorecardSummary(
      @QueryParam("entity_identifier") @Parameter(
          description = "Identifier for entity to get the scores for score card ") @NotNull String entityIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount) {
    return null;
  }

  @Override
  public Response getRecalibratedScoreForScorecard(
      @QueryParam("entity_identifier") @Parameter(
          description = "Identifier for entity to get the scores for score card ") @NotNull String entityIdentifier,
      @QueryParam("scorecard_identifier") @Parameter(
          description = "Identifier for score card ") @NotNull String scorecardIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount) {
    return null;
  }

  @Override
  public Response getScorecardsGraphsScoreSummary(
      @QueryParam("entity_identifier") @Parameter(
          description = "Identifier for entity to get the scores for score card ") @NotNull String entityIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      @QueryParam("scorecard_identifier") @Parameter(
          description = "Identifier for scorecard ") String scorecardIdentifier) {
    return null;
  }

  @Override
  public Response getScorecardsScoresOverview(
      @QueryParam("entity_identifier") @Parameter(
          description = "Identifier for entity to get the scores for score card ") @NotNull String entityIdentifier,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount) {
    return null;
  }
}
