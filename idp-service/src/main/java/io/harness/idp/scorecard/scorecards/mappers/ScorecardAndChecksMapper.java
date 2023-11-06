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
import io.harness.idp.scorecard.scores.beans.ScorecardAndChecks;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardAndChecksMapper {
  public ScorecardAndChecks toDTO(ScorecardEntity scorecardEntity, List<CheckEntity> checksList) {
    return ScorecardAndChecks.builder().scorecard(scorecardEntity).checks(checksList).build();
  }
}
