/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.scorecardchecks.events.scorecards;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.audit.ResourceTypeConstants.IDP_SCORECARDS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.idp.v1.model.ScorecardDetailsResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(IDP)
@Getter
@NoArgsConstructor
public class ScorecardDeleteEvent implements Event {
  public static final String SCORECARD_DELETED = "ScorecardDeleted";

  private ScorecardDetailsResponse oldScorecardDetailsResponse;
  private String accountIdentifier;

  public ScorecardDeleteEvent(String accountIdentifier, ScorecardDetailsResponse oldScorecardDetailsResponse) {
    this.oldScorecardDetailsResponse = oldScorecardDetailsResponse;
    this.accountIdentifier = accountIdentifier;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, oldScorecardDetailsResponse.getScorecard().getName());
    return Resource.builder()
        .identifier(oldScorecardDetailsResponse.getScorecard().getIdentifier() + "_" + accountIdentifier)
        .type(IDP_SCORECARDS)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return SCORECARD_DELETED;
  }
}