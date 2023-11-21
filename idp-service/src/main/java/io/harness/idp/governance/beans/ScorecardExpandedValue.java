/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.governance.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Builder
@Slf4j
public class ScorecardExpandedValue implements ExpandedValue {
  @JsonProperty("serviceScores") Map<String, List<ServiceScorecards>> serviceScores;

  @Override
  public String getKey() {
    return Constants.IDP_SCORECARD_EXPANSION_KEY;
  }

  @Override
  public String toJson() {
    return JsonUtils.asJson(serviceScores);
  }
}
