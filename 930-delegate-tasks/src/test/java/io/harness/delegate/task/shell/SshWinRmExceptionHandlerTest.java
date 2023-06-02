/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.SSH_INVALID_CREDENTIALS_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.SSH_INVALID_CREDENTIALS_HINT;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.WinRmCommandExecutionException;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class SshWinRmExceptionHandlerTest extends CategoryTest {
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSshInvalidCredentials() {
    Exception exception = WingsException.builder().message("msg").code(ErrorCode.INVALID_CREDENTIAL).build();

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, true);
    assertThat(taskNGDataException.getCause()).isInstanceOf(HintException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo(format(SSH_INVALID_CREDENTIALS_HINT, "SSH"));
    assertThat(taskNGDataException.getCause().getCause().getMessage())
        .isEqualTo(format(SSH_INVALID_CREDENTIALS_EXPLANATION, "SSH"));
    assertThat(taskNGDataException.getCause().getCause().getCause().getMessage()).isEqualTo("msg");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSshWingsException() {
    Exception exception = WingsException.builder()
                              .message("msg")
                              .code(ErrorCode.DEFAULT_ERROR_CODE) // is not handled by SshWinRmExceptionHandler
                              .build();

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, true);
    assertThat(taskNGDataException.getCause()).isInstanceOf(WingsException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo("msg");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testWinRmInvalidCredentials() {
    Exception exception = new IllegalStateException("Invalid credentials or incompatible authentication schemes");

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, false);
    assertThat(taskNGDataException.getCause()).isInstanceOf(HintException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo(format(SSH_INVALID_CREDENTIALS_HINT, "WinRm"));
    assertThat(taskNGDataException.getCause().getCause().getMessage())
        .isEqualTo(format(SSH_INVALID_CREDENTIALS_EXPLANATION, "WinRm"));
    assertThat(taskNGDataException.getCause().getCause().getCause().getMessage())
        .isEqualTo("Invalid credentials or incompatible authentication schemes");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testWinRmIllegalStateException() {
    Exception exception = new IllegalStateException("Message not handled");

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, false);
    assertThat(taskNGDataException.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo("Message not handled");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSshWinRmWithHintException() {
    Exception exception =
        NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT,
            DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION,
            new WinRmCommandExecutionException(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT));

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, true);
    assertThat(taskNGDataException.getCause()).isInstanceOf(HintException.class);
    assertThat(taskNGDataException.getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT);
    assertThat(taskNGDataException.getCause().getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION);
    assertThat(taskNGDataException.getCause().getCause().getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT);

    taskNGDataException = SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, false);
    assertThat(taskNGDataException.getCause()).isInstanceOf(HintException.class);
    assertThat(taskNGDataException.getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT);
    assertThat(taskNGDataException.getCause().getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION);
    assertThat(taskNGDataException.getCause().getCause().getCause().getMessage())
        .isEqualTo(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testSshWinRmWithAnotherException() {
    Exception exception = new IllegalArgumentException("IllegalArgumentException");

    TaskNGDataException taskNGDataException =
        SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, true);
    assertThat(taskNGDataException.getCause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo("IllegalArgumentException");

    taskNGDataException = SshWinRmExceptionHandler.handle(exception, log, commandUnitsProgress, false);
    assertThat(taskNGDataException.getCause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(taskNGDataException.getCause().getMessage()).isEqualTo("IllegalArgumentException");
  }
}