/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.CONNECTION_TIMEOUT;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.INVALID_KEY;
import static io.harness.eraro.ErrorCode.INVALID_KEYPATH;
import static io.harness.eraro.ErrorCode.SOCKET_CONNECTION_ERROR;
import static io.harness.eraro.ErrorCode.SOCKET_CONNECTION_TIMEOUT;
import static io.harness.eraro.ErrorCode.SSH_CONNECTION_ERROR;
import static io.harness.eraro.ErrorCode.SSH_SESSION_TIMEOUT;
import static io.harness.eraro.ErrorCode.UNKNOWN_ERROR;
import static io.harness.eraro.ErrorCode.UNKNOWN_HOST;
import static io.harness.eraro.ErrorCode.UNREACHABLE_HOST;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.jcraft.jsch.JSchException;
import com.sun.mail.iap.ConnectionException;
import io.grpc.netty.shaded.io.netty.channel.ConnectTimeoutException;
import java.io.FileNotFoundException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(CDP)
public class SshHelperUtils {
  /**
   * Normalize error.
   *
   * @param jschexception the jschexception
   * @return the string
   */
  public static ErrorCode normalizeError(JSchException jschexception) {
    String message = jschexception.getMessage();
    Throwable cause = jschexception.getCause();

    ErrorCode errorConst = UNKNOWN_ERROR;

    if (cause != null) { // TODO: Refactor use enums, maybe ?
      if (cause instanceof NoRouteToHostException) {
        errorConst = UNREACHABLE_HOST;
      } else if (cause instanceof UnknownHostException) {
        errorConst = UNKNOWN_HOST;
      } else if (cause instanceof SocketTimeoutException) {
        errorConst = SOCKET_CONNECTION_TIMEOUT;
      } else if (cause instanceof ConnectTimeoutException) {
        errorConst = CONNECTION_TIMEOUT;
      } else if (cause instanceof ConnectionException) {
        errorConst = SSH_CONNECTION_ERROR;
      } else if (cause instanceof SocketException) {
        errorConst = SOCKET_CONNECTION_ERROR;
      } else if (cause instanceof FileNotFoundException) {
        errorConst = INVALID_KEYPATH;
      }
    } else {
      if (message.startsWith("invalid privatekey")) {
        errorConst = INVALID_KEY;
      } else if (message.contains("Auth fail") || message.contains("Auth cancel") || message.contains("USERAUTH fail")
          || message.contains("authentication failure")) {
        errorConst = INVALID_CREDENTIAL;
      } else if (message.startsWith("timeout: socket is not established")
          || message.contains("SocketTimeoutException")) {
        errorConst = SOCKET_CONNECTION_TIMEOUT;
      } else if (message.equals("session is down")) {
        errorConst = SSH_SESSION_TIMEOUT;
      }
    }
    return errorConst;
  }

  public static void checkAndSaveExecutionLog(String line, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    checkAndSaveExecutionLog(line, RUNNING, INFO, logCallback, shouldSaveExecutionLogs);
  }

  public static void checkAndSaveExecutionLogWarn(
      String line, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    checkAndSaveExecutionLog(line, RUNNING, WARN, logCallback, shouldSaveExecutionLogs);
  }

  public static void checkAndSaveExecutionLogError(
      String line, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    checkAndSaveExecutionLog(line, RUNNING, ERROR, logCallback, shouldSaveExecutionLogs);
  }

  public static void checkAndSaveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus,
      LogLevel logLevel, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    if (shouldSaveExecutionLogs) {
      logCallback.saveExecutionLog(line, logLevel, commandExecutionStatus);
    }
  }

  public static Consumer<String> checkAndSaveExecutionLogFunction(
      LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    return line -> checkAndSaveExecutionLog(line, logCallback, shouldSaveExecutionLogs);
  }

  public static Consumer<String> checkAndSaveExecutionLogErrorFunction(
      LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    return line -> checkAndSaveExecutionLogError(line, logCallback, shouldSaveExecutionLogs);
  }
}
