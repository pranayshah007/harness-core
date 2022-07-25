/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outcome.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PmsOutcomeMapper {
  public String convertOutcomeValueToJson(Outcome outcome) {
    return RecastOrchestrationUtils.toJson(outcome);
  }

  public Outcome convertJsonToOutcome(String json) {
    return json == null ? null : RecastOrchestrationUtils.fromJson(json, Outcome.class);
  }

  public List<Outcome> convertJsonToOutcome(List<String> outcomesAsJsonList) {
    if (isEmpty(outcomesAsJsonList)) {
      return Collections.emptyList();
    }
    List<Outcome> outcomes = new ArrayList<>();
    for (String jsonOutcome : outcomesAsJsonList) {
      outcomes.add(RecastOrchestrationUtils.fromJson(jsonOutcome, Outcome.class));
    }
    return outcomes;
  }

  public Map<String, PmsOutcome> convertJsonToOrchestrationMap(Map<String, String> outcomeAsJsonList) {
    if (isEmpty(outcomeAsJsonList)) {
      return Collections.emptyMap();
    }
    Map<String, PmsOutcome> outcomes = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : outcomeAsJsonList.entrySet()) {
      outcomes.put(entry.getKey(), entry.getValue() == null ? null : PmsOutcome.parse(entry.getValue()));
    }
    return outcomes;
  }
}
