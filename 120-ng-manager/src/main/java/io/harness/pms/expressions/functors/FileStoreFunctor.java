/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.utils.FilePathUtils.FILE_PATH_PATTERN;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;

import com.google.inject.Inject;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public class FileStoreFunctor implements SdkFunctor {
  private static final long MAX_FILE_SIZE = 4 * ExpressionEvaluatorUtils.EXPANSION_LIMIT;
  private static final int NUMBER_OF_EXPECTED_ARGS = 2;
  private static final int METHOD_NAME_ARG = 0;
  private static final int SCOPED_FILE_PATH_ARG = 1;

  @Inject private CDStepHelper cdStepHelper;

  @Override
  public Object get(Ambiance ambiance, String... args) {
    if (args.length != NUMBER_OF_EXPECTED_ARGS) {
      throw new InvalidArgumentsException(format("Invalid fileStore functor arguments: %s", Arrays.asList(args)));
    }

    String methodName = args[METHOD_NAME_ARG];
    String scopedFilePath = args[SCOPED_FILE_PATH_ARG];

    if (!FILE_PATH_PATTERN.matcher(scopedFilePath).find()) {
      throw new InvalidArgumentsException(format("File path not valid: %s", scopedFilePath));
    }

    return getFileContent(ambiance, methodName, scopedFilePath);
  }

  private String getFileContent(Ambiance ambiance, final String methodName, final String scopedFilePath) {
    if (FUNCTOR_STRING_METHOD_NAME.equals(methodName)) {
      return cdStepHelper.getFileContentAsString(ambiance, scopedFilePath, MAX_FILE_SIZE);
    } else if (FUNCTOR_BASE64_METHOD_NAME.equals(methodName)) {
      return cdStepHelper.getFileContentAsBase64(ambiance, scopedFilePath, MAX_FILE_SIZE);
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported fileStore functor method: %s, scopedFilePath: %s", methodName, scopedFilePath));
    }
  }
}
