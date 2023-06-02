/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.strategy.v1.StrategyConfigV1;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.steps.matrix.StrategyInfo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public interface StrategyConfigServiceV1 {
  List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfigV1 strategyConfig, String childNodeId);

  // YAML Expansion will fail if count is more that the supported limit.
  StrategyInfo expandJsonNode(StrategyConfigV1 strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit);
}
