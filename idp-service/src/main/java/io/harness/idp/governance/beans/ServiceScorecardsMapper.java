/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.governance.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.ScorecardSummaryInfo;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ServiceScorecardsMapper {
  public ServiceScorecards toDTO(ScorecardSummaryInfo scorecardSummaryInfo) {
    List<ServiceScorecards.Check> checks = new ArrayList<>();
    for (CheckStatus checkStatus : scorecardSummaryInfo.getChecksStatuses()) {
      ServiceScorecards.Check check = ServiceScorecards.Check.builder()
                                          .identifier(checkStatus.getIdentifier())
                                          .name(checkStatus.getName())
                                          .status(checkStatus.getStatus().toString())
                                          .build();
      checks.add(check);
    }
    return ServiceScorecards.builder()
        .identifier(scorecardSummaryInfo.getScorecardIdentifier())
        .name(scorecardSummaryInfo.getScorecardName())
        .score(scorecardSummaryInfo.getScore())
        .check(checks)
        .build();
  }
}
