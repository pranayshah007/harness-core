/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.advise.handlers.EndPlanAdviserResponseHandler;
import io.harness.engine.pms.advise.handlers.IgnoreFailureAdviseHandler;
import io.harness.engine.pms.advise.handlers.InterventionWaitAdviserResponseHandler;
import io.harness.engine.pms.advise.handlers.MarkSuccessAdviseHandler;
import io.harness.engine.pms.advise.handlers.NextStepHandler;
import io.harness.engine.pms.advise.handlers.RetryAdviserResponseHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AdviseHandlerFactoryTest extends OrchestrationTestBase {
  @Inject private AdviseHandlerFactory factory;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldObtainHandler() {
    assertThat(factory.obtainHandler(AdviseType.NEXT_STEP)).isInstanceOf(NextStepHandler.class);
    assertThat(factory.obtainHandler(AdviseType.RETRY)).isInstanceOf(RetryAdviserResponseHandler.class);
    assertThat(factory.obtainHandler(AdviseType.INTERVENTION_WAIT))
        .isInstanceOf(InterventionWaitAdviserResponseHandler.class);
    assertThat(factory.obtainHandler(AdviseType.END_PLAN)).isInstanceOf(EndPlanAdviserResponseHandler.class);
    assertThat(factory.obtainHandler(AdviseType.MARK_SUCCESS)).isInstanceOf(MarkSuccessAdviseHandler.class);
    assertThat(factory.obtainHandler(AdviseType.IGNORE_FAILURE)).isInstanceOf(IgnoreFailureAdviseHandler.class);

    assertThatThrownBy(() -> factory.obtainHandler(AdviseType.UNKNOWN))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No handler Present for advise type: " + AdviseType.UNKNOWN);
  }
}
