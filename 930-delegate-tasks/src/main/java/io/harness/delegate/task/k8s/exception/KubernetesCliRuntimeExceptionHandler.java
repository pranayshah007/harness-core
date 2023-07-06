/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.FailureType;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
public class KubernetesCliRuntimeExceptionHandler implements ExceptionHandler {
  private static final String CLIENT_TOOLS_DIRECTORY_NAME = "client-tools";
  private static final String STEADY_STATE_FAILURE_DEADLINE_ERROR = "exceeded its progress deadline";
  private static final String STEADY_STATE_FAILURE_MISSING_OBJECT_ERROR = "object has been deleted";
  private static final String IMMUTABLE_FIELD_MESSAGE = "field is immutable";
  private static final String NOT_FOUND_MESSAGE = "(NotFound)";
  private static final String UNSUPPORTED_VALUE_MESSAGE = "Unsupported value";
  private static final String FORBIDDEN_MESSAGE = "(Forbidden)";
  private static final String REQUIRED_FIELD_MISSING_MESSAGE = "missing required field";
  private static final String UNRESOLVED_VALUE = "<no value>";
  private static final String UNKNOWN_FIELD_MESSAGE = "unknown field";
  private static final String TIMEOUT_MESSAGE = "i/o timeout";
  private static final String NO_OBJECT_PASSED = "no objects passed";
  private static final String KUBECTL_APPLY_CONSOLE_ERROR = "Apply manifest failed with error:\n%s";
  private static final String KUBECTL_DRY_RUN_CONSOLE_ERROR = "Dry run manifest failed with error:\n%s";
  private static final String KUBECTL_STEADY_STATE_CONSOLE_ERROR = "Steady state check failed with error:\n%s";
  private static final String KUBECTL_SCALE_CONSOLE_ERROR = "Failed to scale resource(s) with error:\n%s";
  private static final String KUBECTL_HASH_CONSOLE_ERROR = "Failed to calculate hash of resource(s) with error:\n%s";

  private static final String INVALID_RESOURCE_REGEX = "((\\S+) \"([^\"]*)\" is invalid:)";
  private static final String RESOURCE_NOT_FOUND_REGEX = ".* \"(.*?)\" not found.*";
  private static final String INVALID_TYPE_VALUE_REGEX = ".* ValidationError\\((.*?)\\).*invalid type.*";
  private static final String UNKNOWN_FIELD_REGEX = "(ValidationError\\((.*?)\\): unknown field \"(.*?)\")";

  private static final Pattern INVALID_RESOURCES_PATTERN = Pattern.compile(INVALID_RESOURCE_REGEX, Pattern.MULTILINE);
  private static final Pattern RESOURCE_NOT_FOUND_ERROR_PATTERN =
      Pattern.compile(RESOURCE_NOT_FOUND_REGEX, Pattern.MULTILINE);
  private static final Pattern INVALID_TYPE_ERROR_PATTERN =
      Pattern.compile(INVALID_TYPE_VALUE_REGEX, Pattern.MULTILINE);
  private static final Pattern UNKNOWN_FIELD_ERROR_PATTERN = Pattern.compile(UNKNOWN_FIELD_REGEX, Pattern.MULTILINE);

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.of(KubernetesCliTaskRuntimeException.class);
  }

  @Override
  public WingsException handleException(Exception exception) {
    KubernetesCliTaskRuntimeException kubernetesTaskException = (KubernetesCliTaskRuntimeException) exception;
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();

    // handle some common errors
    if (cliErrorMessage.contains(UNRESOLVED_VALUE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.UNRESOLVED_MANIFEST_FIELD,
          KubernetesExceptionExplanation.UNRESOLVED_MANIFEST_FIELD,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), cliErrorMessage);
    }

    switch (kubernetesTaskException.getCommandType()) {
      case APPLY:
        return handleApplyManifestException(kubernetesTaskException);
      case SCALE:
        return handleScalingException(kubernetesTaskException);
      case DRY_RUN:
        return handleDryRunException(kubernetesTaskException);
      case STEADY_STATE_CHECK:
        return handleSteadyStateCheckFailure(kubernetesTaskException);
      case GENERATE_HASH:
        return handleHashCalculationException(kubernetesTaskException);
      default:
        return getExplanationException(KubernetesExceptionHints.GENERIC_CLI_FAILURE,
            getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), cliErrorMessage);
    }
  }

  private WingsException handleApplyManifestException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_APPLY_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(KubernetesExceptionMessages.CHARACTER_LIMIT_ERROR)
        || cliErrorMessage.matches(KubernetesExceptionMessages.INVALID_CHARACTERS_ERROR)) {
      List<String> invalidResourceNames = getAllResourceNames(cliErrorMessage, INVALID_RESOURCES_PATTERN, "/");
      String explanation =
          format(KubernetesExceptionExplanation.K8S_CHARACTER_ERROR, String.join("\n", invalidResourceNames));
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.K8S_CHARACTER_ERROR, explanation,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }

    if (cliErrorMessage.contains(IMMUTABLE_FIELD_MESSAGE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.IMMUTABLE_FIELD,
          KubernetesExceptionExplanation.IMMUTABLE_FIELD,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(NOT_FOUND_MESSAGE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.MISSING_RESOURCE,
          KubernetesExceptionExplanation.MISSING_RESOURCE,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(UNSUPPORTED_VALUE_MESSAGE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.UNSUPPORTED_VALUE,
          KubernetesExceptionExplanation.UNSUPPORTED_VALUE,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(FORBIDDEN_MESSAGE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.K8S_API_FORBIDDEN_EXCEPTION,
          KubernetesExceptionExplanation.FORBIDDEN_MESSAGE,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(TIMEOUT_MESSAGE)) {
      return getExplanationException(KubernetesExceptionHints.APPLY_MANIFEST_FAILED,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError,
          FailureType.CONNECTIVITY);
    }
    return getExplanationException(KubernetesExceptionHints.APPLY_MANIFEST_FAILED,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleScalingException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_SCALE_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(RESOURCE_NOT_FOUND_REGEX)) {
      List<String> extractedValues = extractValuesFromFirstMatch(cliErrorMessage, RESOURCE_NOT_FOUND_ERROR_PATTERN, 1);
      String resourceName = extractedValues.isEmpty() ? "" : extractedValues.get(0);
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.SCALE_CLI_FAILED,
          format(KubernetesExceptionExplanation.SCALE_CLI_FAILED, resourceName),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(TIMEOUT_MESSAGE)) {
      return getExplanationException(KubernetesExceptionHints.SCALE_CLI_FAILED_GENERIC,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError,
          FailureType.CONNECTIVITY);
    }
    return getExplanationException(KubernetesExceptionHints.SCALE_CLI_FAILED_GENERIC,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleDryRunException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_DRY_RUN_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(INVALID_TYPE_VALUE_REGEX)) {
      List<String> extractedValues = extractValuesFromFirstMatch(cliErrorMessage, INVALID_TYPE_ERROR_PATTERN, 1);
      String invalidFieldName = extractedValues.isEmpty() ? "" : extractedValues.get(0);
      String resourcesNotApplied = isEmpty(kubernetesTaskException.getResourcesNotApplied())
          ? EMPTY
          : kubernetesTaskException.getResourcesNotApplied();
      String kubectlVersion = EMPTY;
      if (isNotEmpty(kubernetesTaskException.getKubectlVersion())) {
        try {
          JSONObject jsonObject = new JSONObject(kubernetesTaskException.getKubectlVersion());
          String clientVersion = (String) ((JSONObject) jsonObject.get("clientVersion")).get("gitVersion");
          String serverVersion = (String) ((JSONObject) jsonObject.get("serverVersion")).get("gitVersion");
          kubectlVersion = format("clientVersion: [%s] %nserverVersion: [%s]", clientVersion, serverVersion);
        } catch (Exception ex) {
          kubectlVersion = EMPTY;
        }
      }
      return getExplanationExceptionWithCommandAndExtraInformation(
          KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE,
          format(KubernetesExceptionExplanation.VALIDATION_FAILED_INVALID_TYPE, invalidFieldName),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException),
          format(KubernetesExceptionExplanation.FAILED_COMMAND_RESOURCES_NOT_APPLIED, resourcesNotApplied),
          format(KubernetesExceptionExplanation.FAILED_COMMAND_KUBECTL_VERSION, kubectlVersion), consolidatedError);
    }
    if (cliErrorMessage.contains(UNKNOWN_FIELD_MESSAGE)) {
      String unknownFields = String.join("\n", getAllResourceNames(cliErrorMessage, UNKNOWN_FIELD_ERROR_PATTERN, ":"));
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.VALIDATION_FAILED_UNKNOWN_FIELD,
          format(KubernetesExceptionExplanation.VALIDATION_FAILED_UNKNOWN_FIELD, unknownFields),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(REQUIRED_FIELD_MISSING_MESSAGE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.MISSING_REQUIRED_FIELD,
          KubernetesExceptionExplanation.MISSING_REQUIRED_FIELD,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.contains(TIMEOUT_MESSAGE)) {
      return getExplanationException(KubernetesExceptionHints.DRY_RUN_MANIFEST_FAILED,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError,
          FailureType.CONNECTIVITY);
    }
    if (cliErrorMessage.contains(NO_OBJECT_PASSED)) {
      return getExplanationException(KubernetesExceptionHints.NO_OBJECT_PASSED,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    return getExplanationException(KubernetesExceptionHints.DRY_RUN_MANIFEST_FAILED,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleSteadyStateCheckFailure(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_STEADY_STATE_CONSOLE_ERROR, cliErrorMessage);
    String commandSummary = getExecutedCommandWithOutputWithExitCode(kubernetesTaskException);

    if (cliErrorMessage.contains(STEADY_STATE_FAILURE_MISSING_OBJECT_ERROR)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.MISSING_OBJECT_ERROR,
          KubernetesExceptionExplanation.MISSING_OBJECT_ERROR, commandSummary, consolidatedError);
    }

    if (cliErrorMessage.contains(STEADY_STATE_FAILURE_DEADLINE_ERROR)) {
      String hint = KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED
          + KubernetesExceptionHints.DEPLOYMENT_PROGRESS_DEADLINE_DOC_REFERENCE;
      return getExplanationExceptionWithCommand(
          hint, KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED, commandSummary, consolidatedError);
    }

    if (cliErrorMessage.contains(TIMEOUT_MESSAGE)) {
      return getExplanationException(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError,
          FailureType.CONNECTIVITY);
    }

    if (EmptyPredicate.isEmpty(commandSummary)) {
      commandSummary = KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED;
    }
    return getExplanationException(
        KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED, commandSummary, consolidatedError);
  }

  private WingsException handleHashCalculationException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_HASH_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.contains(TIMEOUT_MESSAGE)) {
      return getExplanationException(KubernetesExceptionHints.HASH_CALCULATION_FAILED_ERROR,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError,
          FailureType.CONNECTIVITY);
    }

    return getExplanationException(KubernetesExceptionHints.HASH_CALCULATION_FAILED_ERROR,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private String getExecutedCommandWithOutputWithExitCode(KubernetesCliTaskRuntimeException exception) {
    ProcessResponse processResponse = exception.getProcessResponse();
    ProcessResult processResult = processResponse.getProcessResult();
    if (processResult != null) {
      String kubectlPath = getRelativeKubectlClientPath(processResponse.getKubectlPath());
      if (isNotEmpty(processResult.outputUTF8())) {
        return format(KubernetesExceptionExplanation.FAILED_COMMAND_WITH_EXITCODE_AND_OUTPUT,
            processResponse.getPrintableCommand(), processResult.getExitValue(), processResult.outputUTF8(),
            kubectlPath);
      }
      return format(KubernetesExceptionExplanation.FAILED_COMMAND_WITH_EXITCODE, processResponse.getPrintableCommand(),
          processResult.getExitValue(), kubectlPath);
    }
    return "";
  }

  private List<String> extractValuesFromFirstMatch(String errorMessage, Pattern pattern, int valuesToExtract) {
    Matcher matcher = pattern.matcher(errorMessage);
    if (matcher.find()) {
      List<String> values = new ArrayList<>(matcher.groupCount());
      for (int i = 1; i <= matcher.groupCount() && i <= valuesToExtract; i++) {
        values.add(matcher.group(i));
      }
      return values;
    }
    return Collections.emptyList();
  }

  private List<String> getAllResourceNames(String errorMessage, Pattern pattern, String delimiter) {
    Matcher matcher = pattern.matcher(errorMessage);
    List<String> values = new ArrayList<>();
    while (matcher.find()) {
      values.add(matcher.group(2) + delimiter + matcher.group(3));
    }
    return values;
  }

  private WingsException getExplanationException(String hint, String explanation, String errorMessage) {
    return NestedExceptionUtils.hintWithExplanationException(
        hint, explanation, new KubernetesTaskException(errorMessage));
  }

  private WingsException getExplanationException(
      String hint, String explanation, String errorMessage, FailureType failureType) {
    return NestedExceptionUtils.hintWithExplanationException(
        hint, explanation, new KubernetesTaskException(errorMessage, failureType));
  }

  private WingsException getExplanationExceptionWithCommand(
      String hint, String explanation, String command, String errorMessage) {
    if (EmptyPredicate.isNotEmpty(command)) {
      return NestedExceptionUtils.hintWithExplanationAndCommandException(
          hint, explanation, command, new KubernetesTaskException(errorMessage));
    }
    return getExplanationException(hint, explanation, errorMessage);
  }

  private WingsException getExplanationExceptionWithCommandAndExtraInformation(String hint, String explanation,
      String command, String resourcesNotApplied, String version, String errorMessage) {
    if (EmptyPredicate.isNotEmpty(command)) {
      return NestedExceptionUtils.hintWithExplanationsException(
          hint, new KubernetesTaskException(errorMessage), explanation, command, resourcesNotApplied, version);
    }
    return getExplanationException(hint, explanation, errorMessage);
  }

  private String getRelativeKubectlClientPath(String kubectlPath) {
    if (!StringUtils.isEmpty(kubectlPath) && kubectlPath.contains(CLIENT_TOOLS_DIRECTORY_NAME)) {
      return kubectlPath.substring(kubectlPath.indexOf(CLIENT_TOOLS_DIRECTORY_NAME));
    }
    return kubectlPath;
  }
}
