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
import io.harness.spec.server.idp.v1.model.Check;
import io.harness.spec.server.idp.v1.model.Scorecard;
import io.harness.spec.server.idp.v1.model.ScorecardResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ScorecardMapper {
  public Scorecard toDTO(ScorecardEntity scorecardEntity, List<CheckEntity> checkEntityList) {
    Scorecard scorecard = new Scorecard();
    scorecard.setName(scorecardEntity.getName());
    scorecard.setIdentifier(scorecardEntity.getIdentifier());
    scorecard.setDescription(scorecardEntity.getDescription());
    List<Check> checks = new ArrayList<>();
    checkEntityList.forEach(checkEntity -> {
      Check check = new Check();
      check.setName(checkEntity.getName());
      check.setIdentifier(checkEntity.getIdentifier());
      check.setDescription(checkEntity.getDescription());
      check.setExpression(checkEntity.getExpression());
      checks.add(check);
    });
    scorecard.setChecks(checks);
    return scorecard;
  }

  public List<ScorecardResponse> toResponseList(List<Scorecard> scorecards) {
    List<ScorecardResponse> response = new ArrayList<>();
    scorecards.forEach(scorecard -> response.add(new ScorecardResponse().scorecard(scorecard)));
    return response;
  }
}
