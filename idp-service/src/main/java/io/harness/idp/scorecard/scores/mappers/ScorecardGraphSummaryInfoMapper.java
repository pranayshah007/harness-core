/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scores.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.spec.server.idp.v1.model.ScorecardGraphSummaryInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardGraphSummaryInfoMapper {
  public ScorecardGraphSummaryInfo toDTO(ScoreEntity scoreEntity) {
    ScorecardGraphSummaryInfo scorecardGraphSummaryInfo = new ScorecardGraphSummaryInfo();
    if (scoreEntity == null) {
      return scorecardGraphSummaryInfo;
    }
    scorecardGraphSummaryInfo.setScorecardIdentifier(scoreEntity.getScorecardIdentifier());
    scorecardGraphSummaryInfo.setScore((int) scoreEntity.getScore());
    scorecardGraphSummaryInfo.setTimestamp(scoreEntity.getLastComputedTimestamp());
    return scorecardGraphSummaryInfo;
  }
}
