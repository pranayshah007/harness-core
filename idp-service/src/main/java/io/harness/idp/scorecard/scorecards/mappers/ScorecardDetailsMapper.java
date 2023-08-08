/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.spec.server.idp.v1.model.ScorecardChecks;
import io.harness.spec.server.idp.v1.model.ScorecardChecksDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetails;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsRequest;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardDetailsMapper {
  public ScorecardDetailsResponse toDTO(ScorecardEntity scorecardEntity, Map<String, CheckEntity> checkEntityMap) {
    ScorecardDetailsResponse response = new ScorecardDetailsResponse();

    ScorecardDetails details = new ScorecardDetails();
    details.setIdentifier(scorecardEntity.getIdentifier());
    details.setName(scorecardEntity.getName());
    details.setDescription(scorecardEntity.getDescription());
    details.setFilters(scorecardEntity.getFilters());
    details.setPublished(scorecardEntity.isPublished());
    details.setWeightageStrategy(scorecardEntity.getWeightageStrategy());
    response.setScorecard(details);

    List<ScorecardChecksDetails> scorecardChecksDetails = new ArrayList<>();
    scorecardEntity.getChecks().forEach(checks -> {
      ScorecardChecksDetails scorecardChecksDetail = new ScorecardChecksDetails();
      CheckEntity checkEntity = checkEntityMap.get(checks.getIdentifier());
      scorecardChecksDetail.setName(checkEntity.getName());
      scorecardChecksDetail.setIdentifier(checkEntity.getIdentifier());
      scorecardChecksDetail.setDescription(checkEntity.getDescription());
      scorecardChecksDetail.setWeightage(checks.getWeightage());
      scorecardChecksDetails.add(scorecardChecksDetail);
    });
    response.setChecks(scorecardChecksDetails);
    return response;
  }

  public ScorecardEntity fromDTO(ScorecardDetailsRequest scorecardDetailsRequest, String accountIdentifier) {
    ScorecardDetails details = scorecardDetailsRequest.getScorecard();
    List<ScorecardChecks> scorecardChecks = scorecardDetailsRequest.getChecks();
    return ScorecardEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(details.getIdentifier())
        .name(details.getName())
        .description(details.getDescription())
        .checks(scorecardChecks.stream()
                    .map(scorecardCheck
                        -> ScorecardEntity.Check.builder()
                               .identifier(scorecardCheck.getIdentifier())
                               .weightage(scorecardCheck.getWeightage())
                               .build())
                    .collect(Collectors.toList()))
        .filters(details.getFilters())
        .published(details.isPublished())
        .weightageStrategy(details.getWeightageStrategy())
        .build();
  }
}
