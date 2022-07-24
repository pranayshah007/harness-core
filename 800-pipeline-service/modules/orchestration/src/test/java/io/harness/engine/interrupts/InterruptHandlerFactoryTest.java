/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.handlers.AbortAllInterruptHandler;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.handlers.CustomFailureInterruptHandler;
import io.harness.engine.interrupts.handlers.ExpireAllInterruptHandler;
import io.harness.engine.interrupts.handlers.IgnoreFailedInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkExpiredInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkFailedInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkSuccessInterruptHandler;
import io.harness.engine.interrupts.handlers.PauseAllInterruptHandler;
import io.harness.engine.interrupts.handlers.ResumeAllInterruptHandler;
import io.harness.engine.interrupts.handlers.RetryInterruptHandler;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptHandlerFactoryTest extends OrchestrationTestBase {
  @Inject private InterruptHandlerFactory handlerFactory;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestObtain() {
    assertThat(handlerFactory.obtainHandler(InterruptType.ABORT_ALL)).isInstanceOf(AbortAllInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.PAUSE_ALL)).isInstanceOf(PauseAllInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.RESUME_ALL)).isInstanceOf(ResumeAllInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.EXPIRE_ALL)).isInstanceOf(ExpireAllInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.RETRY)).isInstanceOf(RetryInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.MARK_EXPIRED))
        .isInstanceOf(MarkExpiredInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.MARK_SUCCESS))
        .isInstanceOf(MarkSuccessInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.MARK_FAILED)).isInstanceOf(MarkFailedInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.IGNORE)).isInstanceOf(IgnoreFailedInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.ABORT)).isInstanceOf(AbortInterruptHandler.class);
    assertThat(handlerFactory.obtainHandler(InterruptType.CUSTOM_FAILURE))
        .isInstanceOf(CustomFailureInterruptHandler.class);

    assertThatThrownBy(() -> handlerFactory.obtainHandler(InterruptType.UNKNOWN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No Handler Available for Interrupt Type: UNKNOWN");
  }
}
