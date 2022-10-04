/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.DuplicateFileImportException;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptProcessingFailedExceptionTest extends CategoryTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testInterruptProcessingFailedException() {
    String message = "test_message";
    DuplicateFileImportException duplicateFileImportException = new DuplicateFileImportException(message);

    InterruptProcessingFailedException interruptProcessingFailedException_1 =
        new InterruptProcessingFailedException(InterruptType.ABORT, message);
    assertThat(interruptProcessingFailedException_1.getCode())
        .isEqualTo(ErrorCode.ENGINE_INTERRUPT_PROCESSING_EXCEPTION);
    assertThat(interruptProcessingFailedException_1.getParams().get("details").toString()).endsWith(message);

    InterruptProcessingFailedException interruptProcessingFailedException_2 =
        new InterruptProcessingFailedException(InterruptType.ABORT, message, duplicateFileImportException);
    assertThat(interruptProcessingFailedException_2.getCause()).isInstanceOf(DuplicateFileImportException.class);
    assertThat(interruptProcessingFailedException_2.getParams().get("details").toString()).endsWith(message);
  }
}
