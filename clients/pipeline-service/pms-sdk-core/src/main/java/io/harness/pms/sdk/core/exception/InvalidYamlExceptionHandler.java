/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.exception;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
// TODO(sahil): Revisit to handle better with error handling on execute
public class InvalidYamlExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(InvalidYamlRuntimeException.class).build();
  }
  @Override
  public WingsException handleException(Exception exception) {
    InvalidYamlRuntimeException ex = (InvalidYamlRuntimeException) exception;
    if (ex.getOriginalException() == null) {
      return new InvalidYamlException(ex.getMessage());
    }
    if (ex.getOriginalException() instanceof IOException) {
      return new InvalidYamlException(format(ex.getMessage(),
          YamlUtils.getErrorNodePartialFQN(ex.getYamlNode(), (IOException) ex.getOriginalException())));
    }
    return new InvalidYamlException(format(ex.getMessage(), YamlUtils.getFullyQualifiedName(ex.getYamlNode()),
        ExceptionUtils.getMessage(ex.getOriginalException())));
  }
}
