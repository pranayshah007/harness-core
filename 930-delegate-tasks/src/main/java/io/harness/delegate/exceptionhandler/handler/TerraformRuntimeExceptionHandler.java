/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.CONFIG_FILE_PATH_NOT_EXIST;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CONFIG_FILE_PATH_NOT_EXIST;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.TerraformCliRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Singleton
@OwnedBy(CDP)
public class TerraformRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(TerraformCliRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    TerraformCliRuntimeException cliRuntimeException = (TerraformCliRuntimeException) exception;
    Set<String> explanations = new HashSet<>();
    Set<String> hints = new HashSet<>();
    Set<String> structuredErrors = new HashSet<>();

    handleConfigDirectoryNotExistError(cliRuntimeException, structuredErrors, hints);

    TerraformExceptionHelper.handleTerraformCliErrorOutput(
        explanations, hints, structuredErrors, cliRuntimeException.getCliError());

    if (hints.isEmpty() && explanations.isEmpty()) {
      return ExceptionMessageSanitizer.sanitizeException(new TerraformCommandExecutionException(
          TerraformExceptionHelper.cleanError(cliRuntimeException.getCliError()), WingsException.USER_SRE));
    }

    return ExceptionMessageSanitizer.sanitizeException(
        getFinalException(explanations, hints, structuredErrors, cliRuntimeException));
  }

  private void handleConfigDirectoryNotExistError(
      TerraformCliRuntimeException cliRuntimeException, Set<String> structuredErrors, Set<String> hints) {
    if (cliRuntimeException.getCliError().contains(CONFIG_FILE_PATH_NOT_EXIST)) {
      hints.add(HINT_CONFIG_FILE_PATH_NOT_EXIST);
      structuredErrors.add(cliRuntimeException.getCliError());
    }
  }

  private WingsException getFinalException(Set<String> explanations, Set<String> hints, Set<String> structuredErrors,
      TerraformCliRuntimeException cliRuntimeException) {
    TerraformCommandExecutionException terraformCommandException;
    if (structuredErrors.size() == 1) {
      terraformCommandException = new TerraformCommandExecutionException(
          format("%s failed with: %s", cliRuntimeException.getCommand(), structuredErrors.iterator().next()),
          WingsException.USER_SRE);
    } else if (isNotEmpty(structuredErrors)) {
      terraformCommandException = new TerraformCommandExecutionException(
          format("%s failed with: '%s' and (%d) more errors", cliRuntimeException.getCommand(),
              structuredErrors.iterator().next(), structuredErrors.size() - 1),
          WingsException.USER_SRE);
    } else {
      terraformCommandException = new TerraformCommandExecutionException(
          format("%s failed", cliRuntimeException.getCommand()), WingsException.USER_SRE);
    }

    Iterator<String> hintsIterator = hints.iterator();
    Iterator<String> explanationsIterator = explanations.iterator();

    WingsException latestException = terraformCommandException;
    while (hintsIterator.hasNext()) {
      WingsException hintCause = explanationsIterator.hasNext()
          ? new ExplanationException(explanationsIterator.next(), latestException)
          : latestException;
      latestException = new HintException(hintsIterator.next(), hintCause);
    }

    while (explanationsIterator.hasNext()) {
      latestException = new ExplanationException(explanationsIterator.next(), latestException);
    }

    return latestException;
  }
}
