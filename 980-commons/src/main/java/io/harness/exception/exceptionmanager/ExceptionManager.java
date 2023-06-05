/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.exceptionmanager;

import static io.harness.exception.WingsException.ReportTarget.REST_API;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExplanationException;
import io.harness.exception.GeneralException;
import io.harness.exception.HintException;
import io.harness.exception.KryoHandlerNotFoundException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.reflection.ReflectionUtils;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class ExceptionManager {
  @Inject private Map<Class<? extends Exception>, ExceptionHandler> exceptionHandler;
  @Inject private KryoSerializer kryoSerializer;

  public final String DEFAULT_ERROR_MESSAGE = "NULL EXCEPTION";

  public WingsException processException(Exception exception) {
    return processException(exception, null, null);
  }

  public WingsException processException(
      Exception exception, WingsException.ExecutionContext logExecutionContext, Logger log) {
    try {
      WingsException processedException = handleException(exception);
      processedException = ensureExceptionIsKryoSerializable(processedException);
      if (log != null) {
        // skip logging in case log object is null
        ExceptionLogger.logProcessedMessages(processedException, logExecutionContext, log);
      }
      return processedException;
    } catch (Exception ex) {
      ExceptionManager.log.error("Exception occurred while handling error in exception manager", ex);
      String errorMessage = ex.getMessage() == null ? "Unexpected error" : ex.getMessage();
      return NestedExceptionUtils.hintWithExplanationException(HintException.HINT_UNEXPECTED_ERROR,
          ExplanationException.EXPLANATION_UNEXPECTED_ERROR, new GeneralException(errorMessage));
    }
  }

  public List<ResponseMessage> buildResponseFromException(Exception exception) {
    return buildResponseFromException(exception, null, null);
  }

  public List<ResponseMessage> buildResponseFromException(
      Exception exception, WingsException.ExecutionContext logExecutionContext, Logger log) {
    WingsException processedException = processException(exception, logExecutionContext, log);
    return ExceptionLogger.getResponseMessageList(processedException, REST_API);
  }

  // ---------- PRIVATE METHODS -------------

  private ExceptionHandler getExceptionHandler(Exception exception) {
    return exceptionHandler.entrySet()
        .stream()
        .filter(e -> e.getKey().isInstance(exception))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private WingsException handleException(Exception exception) {
    if (exception == null) {
      return new GeneralException(DEFAULT_ERROR_MESSAGE);
    }

    try {
      WingsException handledException;
      if (exception instanceof WingsException) {
        handledException = (WingsException) exception;
        if (handledException.getCause() != null) {
          setExceptionCause(handledException, handleException((Exception) handledException.getCause()));
        }
      } else {
        ExceptionHandler exceptionHandler = getExceptionHandler(exception);
        if (exceptionHandler != null) {
          handledException = exceptionHandler.handleException(exception);
        } else {
          log.info("Exception handler not registered for exception : ", exception);
          handledException = prepareUnhandledExceptionResponse(exception);
        }
        WingsException cascadedException = handledException;
        while (cascadedException.getCause() != null) {
          // 3rd party exception can't be allowed as cause in already handled exception
          cascadedException = (WingsException) cascadedException.getCause();
        }
        setExceptionStacktrace(cascadedException, exception.getStackTrace());
        if (exception.getCause() != null) {
          setExceptionCause(cascadedException, handleException((Exception) exception.getCause()));
        }
      }
      return handledException;
    } catch (Exception e) {
      log.error("An error occurred while handling the exception : {}", exception, e);
      return prepareUnhandledExceptionResponse(exception);
    }
  }

  private WingsException prepareUnhandledExceptionResponse(Exception exception) {
    // default is to wrap unknown exception into wings exception using its message
    String errorMessage = exception.getMessage() == null ? "Unexpected error" : exception.getMessage();
    return new GeneralException(errorMessage);
  }

  private void setExceptionCause(WingsException exception, Exception cause) throws IllegalAccessException {
    ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(exception.getClass(), "cause"), exception, cause);
  }

  private void setExceptionStacktrace(WingsException exception, StackTraceElement[] stackTraceElements)
      throws IllegalAccessException {
    ReflectionUtils.setObjectField(
        ReflectionUtils.getFieldByName(exception.getClass(), "stackTrace"), exception, stackTraceElements);
  }

  private boolean isExceptionKryoRegistered(WingsException wingsException) {
    return kryoSerializer.isRegistered(wingsException.getClass());
  }

  private WingsException handleExceptionIfNotKryoRegistered(Exception exception) {
    if (!(exception instanceof WingsException) || !isExceptionKryoRegistered((WingsException) exception)) {
      log.error("Kryo handler not found for exception {}", exception.getClass());
      return new KryoHandlerNotFoundException(exception.getMessage());
    }
    return (WingsException) exception;
  }

  private WingsException ensureExceptionIsKryoSerializable(Exception exception) {
    if (exception == null) {
      return null;
    }

    if (!(exception instanceof WingsException)) {
      log.error("Unknown runtime exception found in processed exception : {}", exception.getClass());
    }

    WingsException kryoSerializedException = handleExceptionIfNotKryoRegistered(exception);
    try {
      setExceptionCause(kryoSerializedException, ensureExceptionIsKryoSerializable((Exception) exception.getCause()));
    } catch (IllegalAccessException ignored) {
    }

    return kryoSerializedException;
  }
}
