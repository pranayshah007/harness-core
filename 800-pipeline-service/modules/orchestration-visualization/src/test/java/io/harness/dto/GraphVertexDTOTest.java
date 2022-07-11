/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dto;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphVertexDTOTest extends OrchestrationVisualizationTestBase {
  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetOrchestrationMapOutcomes() {
    Map<String, Object> outcomeMap = ImmutableMap.of("status", "success");

    Map<String, PmsOutcome> pmsOutcomeMap = new HashMap<>();
    pmsOutcomeMap.put("outcome", PmsOutcome.parse(outcomeMap));

    Map<String, OrchestrationMap> expectedOrchestrationMap = new HashMap<>();
    expectedOrchestrationMap.put("outcome", OrchestrationMap.parse(outcomeMap));

    GraphVertexDTO graphVertexDTO = GraphVertexDTO.builder().outcomes(pmsOutcomeMap).build();

    assertThat(graphVertexDTO.getOrchestrationMapOutcomes()).isEqualTo(expectedOrchestrationMap);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetOrchestrationMapStepDetails() {
    Map<String, Object> stepDetailsMap = ImmutableMap.of("status", "success");

    Map<String, PmsStepDetails> pmsStepDetails = new HashMap<>();
    pmsStepDetails.put("outcome", PmsStepDetails.parse(stepDetailsMap));

    Map<String, OrchestrationMap> expectedOrchestrationMap = new HashMap<>();
    expectedOrchestrationMap.put("outcome", OrchestrationMap.parse(stepDetailsMap));

    GraphVertexDTO graphVertexDTO = GraphVertexDTO.builder().stepDetails(pmsStepDetails).build();

    assertThat(graphVertexDTO.getOrchestrationMapStepDetails()).isEqualTo(expectedOrchestrationMap);
  }
}
