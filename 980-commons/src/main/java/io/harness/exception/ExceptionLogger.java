/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.Level.ERROR;
import static io.harness.exception.WingsException.ReportTarget.DELEGATE_LOG_SYSTEM;
import static io.harness.exception.WingsException.ReportTarget.LOG_SYSTEM;
import static io.harness.exception.WingsException.ReportTarget.UNIVERSAL;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.util.stream.Collectors.joining;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCodeName;
import io.harness.eraro.MessageManager;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException.ExecutionContext;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.logging.AutoLogContext;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class ExceptionLogger {
  protected static String calculateResponseMessage(List<ResponseMessage> responseMessages) {
    return "Response message: "
        + responseMessages.stream().map(ResponseMessage::getMessage).collect(joining("\n                  "));
  }

  protected static String calculateErrorMessage(WingsException exception, List<ResponseMessage> responseMessages) {
    return Stream.of(calculateResponseMessage(responseMessages), "Exception occurred: " + exception.getMessage())
        .filter(EmptyPredicate::isNotEmpty)
        .collect(joining("\n"));
  }

  protected static String calculateInfoMessage(List<ResponseMessage> responseMessages) {
    return calculateResponseMessage(responseMessages);
  }

  public static List<ResponseMessage> getResponseMessageList(WingsException wingsException, ReportTarget reportTarget) {
    List<ResponseMessage> list = new ArrayList<>();
    for (Throwable ex = wingsException; ex != null; ex = ex.getCause()) {
      if (!(ex instanceof WingsException) || ex instanceof FrameworkBaseException) {
        continue;
      }
      final WingsException exception = (WingsException) ex;
      if (reportTarget != UNIVERSAL && !exception.getReportTargets().contains(reportTarget)) {
        continue;
      }
      final String message =
          MessageManager.getInstance().prepareMessage(ErrorCodeName.builder().value(exception.getCode().name()).build(),
              exception.getMessage(), exception.getParams());
      ResponseMessage responseMessage = ResponseMessage.builder()
                                            .code(exception.getCode())
                                            .message(message)
                                            .level(exception.getLevel())
                                            .failureTypes(exception.getFailureTypes())
                                            .build();

      if (list.stream().noneMatch(msg -> StringUtils.equals(responseMessage.getMessage(), msg.getMessage()))) {
        list.add(responseMessage);
      }
      if (exception.getResponseMessages() != null) {
        list.addAll(exception.getResponseMessages());
      }
    }
    return list;
  }
  // the following method is added to log the exceptions that are coming from wingsExceptionMapperV2
  // logging them as warn for now and will remove logging after seeing the impact that it has on debugging
  public static void logProcessedMessages_asWarn(WingsException exception, ExecutionContext context, Logger log) {
    Exception processedException = null;
    try (AutoLogContext ignore = new AutoLogContext(exception.calcRecursiveContextObjects(), OVERRIDE_ERROR)) {
      ReportTarget target = UNIVERSAL;

      switch (context) {
        case MANAGER:
          target = LOG_SYSTEM;
          break;
        case DELEGATE:
          target = DELEGATE_LOG_SYSTEM;
          break;
        default:
          unhandled(context);
      }

      List<ResponseMessage> responseMessages = getResponseMessageList(exception, target);
      if (responseMessages.stream().anyMatch(responseMessage -> responseMessage.getLevel() == ERROR)) {
        if (log.isWarnEnabled()) {
          log.warn(calculateErrorMessage(exception, responseMessages), exception);
        }
      } else {
        if (log.isInfoEnabled()) {
          responseMessages = getResponseMessageList(exception, UNIVERSAL);
          log.info(calculateInfoMessage(responseMessages));
        }
      }
    } catch (Exception e) {
      processedException = e;
      log.error("Original exception:", exception);
    }

    if (processedException != null) {
      log.error("Error processing messages.", processedException);
    }
  }

  public static void logProcessedMessages(WingsException exception, ExecutionContext context, Logger log) {
    Exception processedException = null;
    try (AutoLogContext ignore = new AutoLogContext(exception.calcRecursiveContextObjects(), OVERRIDE_ERROR)) {
      ReportTarget target = UNIVERSAL;

      switch (context) {
        case MANAGER:
          target = LOG_SYSTEM;
          break;
        case DELEGATE:
          target = DELEGATE_LOG_SYSTEM;
          break;
        default:
          unhandled(context);
      }

      List<ResponseMessage> responseMessages = getResponseMessageList(exception, target);
      if (responseMessages.stream().anyMatch(responseMessage -> responseMessage.getLevel() == ERROR)) {
        if (log.isErrorEnabled()) {
          log.error(calculateErrorMessage(exception, responseMessages), exception);
        }
      } else {
        if (log.isInfoEnabled()) {
          responseMessages = getResponseMessageList(exception, UNIVERSAL);
          log.info(calculateInfoMessage(responseMessages));
        }
      }
    } catch (Exception e) {
      processedException = e;
      log.error("Original exception:", exception);
    }

    if (processedException != null) {
      log.error("Error processing messages.", processedException);
    }
  }
}
