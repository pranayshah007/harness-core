/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.GENERIC_NO_TERRAFORM_ERROR;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.CANT_READ_REMOTE_REPOSITORY;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.DOWNLOADING_ERROR;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.FUNCTION_CALL_ERROR;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.MISSING_REQUIRED_ARGUMENT;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.NO_TERRAGRUNT_HCL_FILE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.TERRAFORM_NOT_FOUND;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.TERRAGRUNT_NOT_FOUND;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.UNKNOWN_FUNCTION;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.UNSUITABLE_TYPE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.UNSUPORTED_BLOCK_TYPE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.UNSUPPORTED_ARGUMENT;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.CliErrorMessages.UNSUPPORTED_ATTRIBUTE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Hints.HINT_CLONE_REMOTE_REPOSITORY;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Hints.HINT_NO_TERRAGRUNT_HCL_FILE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Hints.HINT_TERRAFORM_TERRAGRUNT_NOT_FOUND;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Hints.HINT_UNSUPPORTED_ARGUMENT;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Message.MESSAGE_DOWNLOAD_REMOTE_REPO;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Message.MESSAGE_NO_TERRAGRUNT_HCL_FILE;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Message.MESSAGE_NO_TERRAGRUNT_TERRAFORM_FOUND;
import static io.harness.delegate.task.terragrunt.TerragruntExceptionConstants.Message.MESSAGE_UNSUPPORTED_ARGUMENT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.TerragruntCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.TerragruntCliRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Singleton
@OwnedBy(CDP)
public class TerragruntRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(TerragruntCliRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    TerragruntCliRuntimeException cliRuntimeException = (TerragruntCliRuntimeException) exception;
    String error = EmptyPredicate.isNotEmpty(cliRuntimeException.getCliError()) ? cliRuntimeException.getCliError()
                                                                                : cliRuntimeException.getMessage();
    if (EmptyPredicate.isEmpty(error)) {
      error = GENERIC_NO_TERRAFORM_ERROR;
    }
    Set<String> explanations = new HashSet<>();
    Set<String> hints = new HashSet<>();
    Set<String> structuredErrors = new HashSet<>();

    if (error.toLowerCase().contains(UNSUPPORTED_ARGUMENT.toLowerCase())
        || error.toLowerCase().contains(UNSUPPORTED_ATTRIBUTE.toLowerCase())
        || error.toLowerCase().contains(UNKNOWN_FUNCTION.toLowerCase())
        || error.toLowerCase().contains(FUNCTION_CALL_ERROR.toLowerCase())
        || error.toLowerCase().contains(UNSUPORTED_BLOCK_TYPE.toLowerCase())
        || error.toLowerCase().contains(MISSING_REQUIRED_ARGUMENT.toLowerCase())
        || error.toLowerCase().contains(UNSUITABLE_TYPE.toLowerCase())) {
      structuredErrors.add(MESSAGE_UNSUPPORTED_ARGUMENT);
      explanations.add(TerraformExceptionHelper.cleanError(error));
      hints.add(HINT_UNSUPPORTED_ARGUMENT);
    }

    if (error.toLowerCase().contains(TERRAGRUNT_NOT_FOUND.toLowerCase())
        || error.toLowerCase().contains(TERRAFORM_NOT_FOUND.toLowerCase())) {
      structuredErrors.add(MESSAGE_NO_TERRAGRUNT_TERRAFORM_FOUND);
      explanations.add(TerraformExceptionHelper.cleanError(error));
      hints.add(HINT_TERRAFORM_TERRAGRUNT_NOT_FOUND);
    }

    if (error.toLowerCase().contains(DOWNLOADING_ERROR.toLowerCase())
        || error.toLowerCase().contains(CANT_READ_REMOTE_REPOSITORY.toLowerCase())) {
      structuredErrors.add(MESSAGE_DOWNLOAD_REMOTE_REPO);
      explanations.add(TerraformExceptionHelper.cleanError(error));
      hints.add(HINT_CLONE_REMOTE_REPOSITORY);
    }

    if (error.toLowerCase().contains(NO_TERRAGRUNT_HCL_FILE.toLowerCase())) {
      structuredErrors.add(MESSAGE_NO_TERRAGRUNT_HCL_FILE);
      explanations.add(TerraformExceptionHelper.cleanError(error));
      hints.add(HINT_NO_TERRAGRUNT_HCL_FILE);
    }

    TerraformExceptionHelper.handleTerraformCliErrorOutput(explanations, hints, structuredErrors, error);

    if (hints.isEmpty() && explanations.isEmpty()) {
      return ExceptionMessageSanitizer.sanitizeException(
          new TerragruntCommandExecutionException(TerraformExceptionHelper.cleanError(error), WingsException.USER_SRE));
    }

    return ExceptionMessageSanitizer.sanitizeException(
        getFinalException(explanations, hints, structuredErrors, cliRuntimeException));
  }

  private WingsException getFinalException(Set<String> explanations, Set<String> hints, Set<String> structuredErrors,
      TerragruntCliRuntimeException cliRuntimeException) {
    TerragruntCommandExecutionException terragruntCommandException;
    if (structuredErrors.size() == 1) {
      terragruntCommandException = new TerragruntCommandExecutionException(
          format("%s failed with: %s", cliRuntimeException.getCommand(), structuredErrors.iterator().next()),
          WingsException.USER_SRE);
    } else if (isNotEmpty(structuredErrors)) {
      terragruntCommandException = new TerragruntCommandExecutionException(
          format("%s failed with: '%s' and (%d) more errors", cliRuntimeException.getCommand(),
              structuredErrors.iterator().next(), structuredErrors.size() - 1),
          WingsException.USER_SRE);
    } else {
      terragruntCommandException = new TerragruntCommandExecutionException(
          format("%s failed", cliRuntimeException.getCommand()), WingsException.USER_SRE);
    }

    Iterator<String> hintsIterator = hints.iterator();
    Iterator<String> explanationsIterator = explanations.iterator();

    WingsException latestException = terragruntCommandException;
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
