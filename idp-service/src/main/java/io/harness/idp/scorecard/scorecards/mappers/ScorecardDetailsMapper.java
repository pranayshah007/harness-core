/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.mappers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

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
  public ScorecardDetailsResponse toDTO(
      ScorecardEntity scorecardEntity, Map<String, CheckEntity> checkEntityMap, String harnessAccount) {
    ScorecardDetailsResponse response = new ScorecardDetailsResponse();

    ScorecardDetails details = new ScorecardDetails();
    details.setIdentifier(scorecardEntity.getIdentifier());
    details.setName(scorecardEntity.getName());
    details.setDescription(scorecardEntity.getDescription());
    details.setFilter(scorecardEntity.getFilter());
    details.setPublished(scorecardEntity.isPublished());
    details.setWeightageStrategy(scorecardEntity.getWeightageStrategy());

    List<ScorecardChecksDetails> scorecardChecksDetails = new ArrayList<>();
    List<String> checksMissing = new ArrayList<>();
    scorecardEntity.getChecks().forEach(checks -> {
      String accountIdentifier = checks.isCustom() ? harnessAccount : GLOBAL_ACCOUNT_ID;
      CheckEntity checkEntity = checkEntityMap.get(accountIdentifier + DOT_SEPARATOR + checks.getIdentifier());
      if (checkEntity != null && !checkEntity.isDeleted()) {
        ScorecardChecksDetails scorecardChecksDetail = new ScorecardChecksDetails();
        scorecardChecksDetail.setName(checkEntity.getName());
        scorecardChecksDetail.setIdentifier(checkEntity.getIdentifier());
        scorecardChecksDetail.setDescription(checkEntity.getDescription());
        scorecardChecksDetail.setWeightage(checks.getWeightage());
        scorecardChecksDetail.setCustom(checks.isCustom());
        scorecardChecksDetails.add(scorecardChecksDetail);
      } else {
        checksMissing.add(checks.getIdentifier());
      }
    });
    details.setChecksMissing(checksMissing);
    response.setScorecard(details);
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
                               .isCustom(scorecardCheck.isCustom())
                               .build())
                    .collect(Collectors.toList()))
        .filter(details.getFilter())
        .published(details.isPublished())
        .weightageStrategy(details.getWeightageStrategy())
        .build();
  }
}
