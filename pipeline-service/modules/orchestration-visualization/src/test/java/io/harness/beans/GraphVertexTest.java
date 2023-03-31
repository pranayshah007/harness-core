/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({RecastOrchestrationUtils.class})
public class GraphVertexTest extends OrchestrationVisualizationTestBase {
  @Before
  public void initialize() {
    Mockito.mockStatic(RecastOrchestrationUtils.class);
    PowerMockito.when(RecastOrchestrationUtils.toJson(any(Outcome.class))).thenReturn("test");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetPmsStepParameters() {
    PmsStepParameters stepParameters = new PmsStepParameters(new HashMap<>());
    GraphVertex graphVertex = GraphVertex.builder().stepParameters(stepParameters).build();
    PmsStepParameters pmsStepParameters = graphVertex.getPmsStepParameters();
    assertThat(pmsStepParameters)
        .isEqualTo(PmsStepParameters.parse(
            OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(stepParameters)));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetPmsOutcomes() {
    // sending outcomes as null
    GraphVertex graphVertex = GraphVertex.builder().outcomeDocuments(null).build();
    Map<String, PmsOutcome> pmsOutcomes = graphVertex.getPmsOutcomes();
    assertThat(pmsOutcomes).isNotNull();
    assertThat(pmsOutcomes).isEmpty();

    // sending outcomeDocument as not null
    Map<String, String> jsons = new HashMap<>();
    jsons.put("key", "test");
    Map<String, PmsOutcome> pmsOutcomeMap = PmsOutcomeMapper.convertJsonToOrchestrationMap(jsons);
    assertThat(pmsOutcomeMap).isNotNull();
    graphVertex.setOutcomeDocuments(pmsOutcomeMap);
    pmsOutcomes = graphVertex.getPmsOutcomes();
    assertThat(pmsOutcomes).isNotNull();
  }
}
