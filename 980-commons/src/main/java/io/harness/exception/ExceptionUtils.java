/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.exception.WingsException.ReportTarget.REST_API;

import static java.util.stream.Collectors.joining;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.ngexception.ErrorMetadataDTO;
import io.harness.exception.runtime.hashicorp.HashiCorpVaultRuntimeException;

import java.util.EnumSet;
import javax.validation.ConstraintViolationException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ExceptionUtils {
  public static <T> T cause(Class<T> clazz, Throwable exception) {
    while (exception != null) {
      if (exception.getClass().equals(clazz)) {
        return (T) exception;
      }
      exception = exception.getCause();
    }
    return null;
  }

  public static EnumSet<FailureType> getFailureTypes(Throwable throwable) {
    EnumSet<FailureType> failureTypes = EnumSet.noneOf(FailureType.class);

    Throwable t = throwable;
    while (t != null) {
      if (t instanceof WingsException) {
        failureTypes.addAll(((WingsException) t).getFailureTypes());
      }

      t = t.getCause();
    }

    if (failureTypes.isEmpty()) {
      log.error("While determining the failureTypes, none was discovered for the following exception", throwable);
    }

    return failureTypes;
  }

  /**
   * It recurses through exception stack and fetches list of failure types of all exceptions
   * with level = ERROR
   * @param throwable
   * @return Set of failure types of exceptions with ERROR level
   */
  public static EnumSet<FailureType> getFailureTypesOfErrors(Throwable throwable) {
    EnumSet<FailureType> failureTypes = EnumSet.noneOf(FailureType.class);

    Throwable t = throwable;
    while (t != null) {
      if (t instanceof WingsException && Level.ERROR.equals(((WingsException) t).getLevel())) {
        failureTypes.addAll(((WingsException) t).getFailureTypes());
      }

      t = t.getCause();
    }

    if (failureTypes.isEmpty()) {
      log.error("While determining the failureTypes, none was discovered for the following exception", throwable);
    }

    return failureTypes;
  }

  public static String getMessage(Throwable t) {
    if (t instanceof WingsException) {
      WingsException we = (WingsException) t;
      return ExceptionLogger.getResponseMessageList(we, REST_API)
          .stream()
          .map(ResponseMessage::getMessage)
          .collect(joining(". "));
    } else if (t instanceof ConstraintViolationException) {
      ConstraintViolationException constraintViolationException = (ConstraintViolationException) t;
      return constraintViolationException.getConstraintViolations()
          .stream()
          .map(item
              -> String.format("Constraint Violation. Class: %s, Field: %s, InvalidValue: %s, Message: %s",
                  item.getRootBeanClass(), item.getPropertyPath(), item.getInvalidValue(), item.getMessage()))
          .collect(joining(". "));
    } else if (t instanceof HarnessException) {
      Throwable cause = t.getCause();
      if (cause instanceof WingsException) {
        WingsException we = (WingsException) cause;
        return ExceptionLogger.getResponseMessageList(we, REST_API)
            .stream()
            .map(ResponseMessage::getMessage)
            .collect(joining(". "));
      } else {
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
      }
    } else if (t instanceof HashiCorpVaultRuntimeException) {
      return "After 3 tries, encryption for vault secret failed with the error " + t.getMessage();
    } else if (t instanceof CastedFieldException) {
      return t.getMessage();
    } else {
      StringBuilder result = new StringBuilder();
      result.append(t.getClass().getSimpleName());

      if (t.getMessage() != null) {
        result.append(": ");
        result.append(t.getMessage());
      }

      Throwable cause = t.getCause();
      while (cause != null) {
        if (t.getCause().getMessage() != null) {
          result.append("; Cause: ");
          result.append(cause.getClass().getSimpleName());
          result.append(": ");
          result.append(t.getCause().getMessage());
        }
        cause = cause.getCause();
      }
      return result.toString();
    }
  }

  public static WingsException cause(ErrorCode errorCode, Throwable exception) {
    while (exception != null) {
      if (exception instanceof WingsException && ((WingsException) exception).getCode().equals(errorCode)) {
        return (WingsException) exception;
      }
      exception = exception.getCause();
    }
    return null;
  }

  public static WingsException cause(WingsException ex, Throwable exception) {
    while (exception != null) {
      if (exception instanceof WingsException && exception.getCause().getClass().equals(ex)) {
        return (WingsException) exception.getCause();
      }
      exception = exception.getCause();
    }
    return null;
  }

  public static ErrorMetadataDTO getErrorMetadata(Throwable ex, String metadataType) {
    if (metadataType == null) {
      return null;
    }
    while (ex != null) {
      if (ex instanceof WingsException) {
        WingsException exception = (WingsException) ex;
        if (exception.getMetadata() != null && metadataType.equals(exception.getMetadata().getType())) {
          return exception.getMetadata();
        }
      }
      ex = ex.getCause();
    }
    return null;
  }
}
