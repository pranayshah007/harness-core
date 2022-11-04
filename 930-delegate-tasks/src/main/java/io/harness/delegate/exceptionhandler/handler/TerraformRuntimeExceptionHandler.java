/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.CONFIG_FILE_PATH_NOT_EXIST;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.ERROR_CONFIGURING_S3_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.ERROR_INSPECTING_STATE_IN_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.ERROR_VALIDATING_PROVIDER_CRED;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.FAILED_TO_GET_EXISTING_WORKSPACES;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.FAILED_TO_READ_MODULE_DIRECTORY;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.FAIL_TO_INSTALL_PROVIDER;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.INVALID_CREDENTIALS_AWS;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.NO_VALID_CRED_FOUND_FOR_AWS;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.CliErrorMessages.NO_VALID_CRED_FOUND_FOR_S3_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_FAIL_TO_INSTALL_PROVIDER;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_INVALID_CREDENTIALS_AWS;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG_FIE;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG_LOCATION;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CHECK_TERRAFORM_CONFIG_LOCATION_ARGUMENT;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_CONFIG_FILE_PATH_NOT_EXIST;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_ERROR_INSPECTING_STATE_IN_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FAILED_TO_GET_EXISTING_WORKSPACES;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FAIL_TO_INSTALL_PROVIDER;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_INVALID_CREDENTIALS_AWS;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_INVALID_CRED_FOR_S3_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.GENERIC_NO_TERRAFORM_ERROR;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_ERROR_INSPECTING_STATE_IN_BACKEND;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_ERROR_TOO_LONG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_FAILED_TO_GET_EXISTING_WORKSPACES;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Message.MESSAGE_INVALID_CREDENTIALS_AWS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.runtime.TerraformCliRuntimeException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(CDP)
public class TerraformRuntimeExceptionHandler implements ExceptionHandler {
  private static final Pattern ERROR_PATTERN =
      Pattern.compile("\\[31m.+?\\[1m.+?\\[31mError:", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  private static final Pattern ERROR_LOCATION_LINE_PATTERN = Pattern.compile("on\\s(.+?)\\sline\\s(\\d+):");
  private static final Pattern ERROR_LOCATION_LINE_BLOCK_PATTERN =
      Pattern.compile("on\\s(.+?)\\sline\\s(\\d+),\\sin\\s(.+?):\\s+\\d+:(.*)");
  private static final Pattern ERROR_ARGUMENT_PATTERN = Pattern.compile(".+?\\s\".+?\"");
  private static final int ERROR_MAX_CHARACTERS_LIMIT = 512;

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(TerraformCliRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    TerraformCliRuntimeException cliRuntimeException = (TerraformCliRuntimeException) exception;
    Set<String> allErrors = getAllErrors(cliRuntimeException.getCliError());
    Set<String> explanations = new HashSet<>();
    Set<String> hints = new HashSet<>();
    Set<String> structuredErrors = new HashSet<>();

    handleConfigDirectoryNotExistError(cliRuntimeException, structuredErrors, hints);

    for (String error : allErrors) {
      if (error.contains(FAILED_TO_READ_MODULE_DIRECTORY)) {
        continue;
      }
      if (error.toLowerCase().contains(ERROR_CONFIGURING_S3_BACKEND.toLowerCase())) {
        explanations.add(cleanError(error));
        hints.add(HINT_ERROR_INSPECTING_STATE_IN_BACKEND);
        if (error.toLowerCase().contains(NO_VALID_CRED_FOUND_FOR_S3_BACKEND.toLowerCase())
            || error.toLowerCase().contains(ERROR_VALIDATING_PROVIDER_CRED.toLowerCase())) {
          hints.add(HINT_INVALID_CRED_FOR_S3_BACKEND);
        }
      }

      if (StringUtils.indexOfAny(error.toLowerCase(),
              new String[] {INVALID_CREDENTIALS_AWS.toLowerCase(), ERROR_VALIDATING_PROVIDER_CRED.toLowerCase(),
                  NO_VALID_CRED_FOUND_FOR_AWS.toLowerCase()})
          != -1) {
        structuredErrors.add(MESSAGE_INVALID_CREDENTIALS_AWS);
        if (error.indexOf('\t') != -1) {
          explanations.add(EXPLANATION_INVALID_CREDENTIALS_AWS
              + ". Response: " + cleanError(error.substring(error.indexOf('\t') + 1)));
        } else {
          explanations.add(EXPLANATION_INVALID_CREDENTIALS_AWS);
        }

        hints.add(HINT_INVALID_CREDENTIALS_AWS);
        continue;
      }

      if (error.toLowerCase().contains(ERROR_INSPECTING_STATE_IN_BACKEND.toLowerCase())) {
        structuredErrors.add(MESSAGE_ERROR_INSPECTING_STATE_IN_BACKEND);
        explanations.add(cleanError(error));
        hints.add(HINT_ERROR_INSPECTING_STATE_IN_BACKEND);
      }

      if (error.toLowerCase().contains(FAILED_TO_GET_EXISTING_WORKSPACES.toLowerCase())) {
        structuredErrors.add(MESSAGE_FAILED_TO_GET_EXISTING_WORKSPACES);
        explanations.add(cleanError(error));
        hints.add(HINT_FAILED_TO_GET_EXISTING_WORKSPACES);
        continue;
      }

      if (error.toLowerCase().contains(FAIL_TO_INSTALL_PROVIDER.toLowerCase())) {
        explanations.add(EXPLANATION_FAIL_TO_INSTALL_PROVIDER);
        hints.add(HINT_FAIL_TO_INSTALL_PROVIDER);
      }

      handleUnknownError(error, hints, explanations, structuredErrors);
    }

    if (hints.isEmpty() && explanations.isEmpty()) {
      return new TerraformCommandExecutionException(
          cleanError(cliRuntimeException.getCliError()), WingsException.USER_SRE);
    }

    return getFinalException(explanations, hints, structuredErrors, cliRuntimeException);
  }

  private void handleConfigDirectoryNotExistError(
      TerraformCliRuntimeException cliRuntimeException, Set<String> structuredErrors, Set<String> hints) {
    if (cliRuntimeException.getCliError().contains(CONFIG_FILE_PATH_NOT_EXIST)) {
      hints.add(HINT_CONFIG_FILE_PATH_NOT_EXIST);
      structuredErrors.add(cliRuntimeException.getCliError());
    }
  }

  private void handleUnknownError(String err, Set<String> hints, Set<String> explanations, Set<String> errorMessages) {
    Iterator<String> errorLines = Arrays.stream(cleanError(err).split("\\u001B"))
                                      .map(String::trim)
                                      .filter(EmptyPredicate::isNotEmpty)
                                      .collect(Collectors.toList())
                                      .iterator();

    if (!errorLines.hasNext()) {
      return;
    }

    errorMessages.add(errorLines.next());
    if (errorLines.hasNext()) {
      String nextError = errorLines.next();
      // check if next error line is in format "on config.tf line 1:"
      Matcher errorLocationLineMatcher = ERROR_LOCATION_LINE_PATTERN.matcher(nextError);
      if (errorLocationLineMatcher.find()) {
        String fileName = errorLocationLineMatcher.group(1);
        String line = errorLocationLineMatcher.group(2);

        // if error line matches "on config.tf line 1:" then we expect next line to be the terraform block,
        // i.e. variable "access_key" otherwise it muse be some kind of explanation
        if (errorLines.hasNext()) {
          String nextLine = errorLines.next();
          Matcher argumentMatcher = ERROR_ARGUMENT_PATTERN.matcher(nextLine);
          if (argumentMatcher.find()) {
            hints.add(format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, fileName, line, nextLine));
          } else {
            explanations.add(nextLine);
            hints.add(format(HINT_CHECK_TERRAFORM_CONFIG, fileName, line));
          }
        }
      } else {
        // otherwise check if the next line is in format "on main.tf line 1, in variable "test": argument "test""
        Matcher errorLocationLineBlockMatcher = ERROR_LOCATION_LINE_BLOCK_PATTERN.matcher(nextError);
        if (errorLocationLineBlockMatcher.find()) {
          String fileName = errorLocationLineBlockMatcher.group(1);
          String line = errorLocationLineBlockMatcher.group(2);
          String terraformBlock = errorLocationLineBlockMatcher.group(3);
          String argument = errorLocationLineBlockMatcher.group(4).trim();

          if (!terraformBlock.equalsIgnoreCase(argument) && isNotEmpty(argument)) {
            hints.add(format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION_ARGUMENT, fileName, line, terraformBlock, argument));
          } else {
            hints.add(format(HINT_CHECK_TERRAFORM_CONFIG_LOCATION, fileName, line, terraformBlock));
          }
        } else {
          // if none matches then we can't identify the error location and we will expect that remaining lines will meet
          // our explanation expectation
          StringBuilder explanation = new StringBuilder(nextError);
          while (errorLines.hasNext()) {
            explanation.append(' ').append(errorLines.next());
          }
          explanations.add(explanation.toString());
        }
      }

      if (errorLines.hasNext()) {
        nextError = errorLines.next();
        // in some cases the next error line will be '{', so just ignore it
        if (nextError.charAt(0) == '{') {
          if (errorLines.hasNext()) {
            nextError = errorLines.next();
          }
        }

        StringBuilder explanation = new StringBuilder(nextError);
        while (errorLines.hasNext()) {
          explanation.append(' ').append(errorLines.next());
        }
        if (!explanation.toString().equals("{")) {
          explanations.add(explanation.toString());
        }
      }
    }

    if (isEmpty(hints)) {
      hints.add(HINT_CHECK_TERRAFORM_CONFIG_FIE);
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

  private String cleanError(String error) {
    if (isEmpty(error.trim())) {
      return GENERIC_NO_TERRAFORM_ERROR;
    }

    return getMessageWithLimit(error.replaceAll("\\[\\d{1,3}m", ""));
  }

  private String getMessageWithLimit(String message) {
    if (message.length() > ERROR_MAX_CHARACTERS_LIMIT) {
      return message.substring(0, ERROR_MAX_CHARACTERS_LIMIT) + "... (" + MESSAGE_ERROR_TOO_LONG + ")";
    }

    return message;
  }

  @VisibleForTesting
  static Set<String> getAllErrors(String unstructuredError) {
    Matcher errorMatcher = ERROR_PATTERN.matcher(unstructuredError);
    Set<String> allErrors = new HashSet<>();
    int errorMessageStartAt;
    int errorMessageEndAt;

    if (!errorMatcher.find()) {
      return allErrors;
    }

    errorMessageStartAt = errorMatcher.end();
    while (errorMatcher.find()) {
      errorMessageEndAt = errorMatcher.start();
      allErrors.add(unstructuredError.substring(errorMessageStartAt, errorMessageEndAt).trim());
      errorMessageStartAt = errorMatcher.end();
    }

    allErrors.add(unstructuredError.substring(errorMessageStartAt).trim());

    return allErrors;
  }
}
