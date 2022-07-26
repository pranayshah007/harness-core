/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.skip.factory;

import static io.harness.rule.OwnerRule.ALEXEI;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.rule.Owner;
import io.harness.skip.skipper.impl.NoOpSkipper;
import io.harness.skip.skipper.impl.SkipNodeSkipper;
import io.harness.skip.skipper.impl.SkipTreeSkipper;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class VertexSkipperFactoryTest extends OrchestrationVisualizationTestBase {
  @Inject private VertexSkipperFactory vertexSkipperFactory;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestObtainVertexSkipper() {
    assertThat(vertexSkipperFactory.obtainVertexSkipper(SkipType.SKIP_NODE)).isInstanceOf(SkipNodeSkipper.class);
    assertThat(vertexSkipperFactory.obtainVertexSkipper(SkipType.SKIP_TREE)).isInstanceOf(SkipTreeSkipper.class);
    assertThat(vertexSkipperFactory.obtainVertexSkipper(SkipType.NOOP)).isInstanceOf(NoOpSkipper.class);

    assertThatThrownBy(() -> vertexSkipperFactory.obtainVertexSkipper(SkipType.UNRECOGNIZED))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining(format("Unsupported skipper type : [%s]", SkipType.UNRECOGNIZED));
  }
}
