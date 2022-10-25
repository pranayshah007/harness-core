/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.validation;

import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.persistence.UuidAccess;

import com.mongodb.DuplicateKeyException;
import java.util.concurrent.Callable;
import lombok.experimental.UtilityClass;

/**
 * The Class Validator.
 */
@UtilityClass
public class PersistenceValidator {
  public static void validateUuid(UuidAccess base, String fieldName, String fieldValue) {
    notNullCheck(fieldValue, fieldName);
    if (!fieldValue.equals(base.getUuid())) {
      throw new InvalidRequestException(fieldName + " mismatch with object uuid");
    }
  }

  public static void duplicateCheck(Runnable runnable, String field, String value) {
    try {
      runnable.run();
    } catch (DuplicateKeyException e) {
      throw new GeneralException(calculateMessage(field, value), e);
    }
  }

  public static <V> V duplicateCheck(Callable<V> runnable, String field, String value) {
    try {
      return runnable.call();
    } catch (DuplicateKeyException e) {
      throw new GeneralException(calculateMessage(field, value), e, USER);
    } catch (Exception e) {
      if (e.getCause() instanceof DuplicateKeyException) {
        throw new GeneralException(calculateMessage(field, value), e, USER);
      }
      throw new GeneralException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private static String calculateMessage(String field, String value) {
    return "Duplicate " + field + " " + value;
  }
}
