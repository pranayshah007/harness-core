/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_HELM_HIST;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_HELM_INSTALL;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_HELM_UPGRADE;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_LIST_RELEASE;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_RENDER_CHART;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_REPO_ADD;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.DEFAULT_EXPLAIN_ROLLBACK;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_401_UNAUTHORIZED;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_403_FORBIDDEN;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_404_HELM_REPO;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_CHART_VERSION_IMPROPER_CONSTRAINT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_INVALID_YAML;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_MALFORMED_URL;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_MISSING_PROTOCOL_HANDLER;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_CHART_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_CHART_VERSION_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_RELEASES_ERROR;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_RESOURCE_CONFLICT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_TIMEOUT_EXCEPTION;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_UNKNOWN_COMMAND_FLAG;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_VALIDATE_ERROR;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.CHART_VERSION_IMPROPER_CONSTRAINT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.EXISTING_RESOURCE_CONFLICT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.FORBIDDEN_403;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.INVALID_VALUE_TYPE;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.NOT_FOUND_404;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.NO_CHART_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.NO_CHART_VERSION_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.NO_DEPLOYED_RELEASES;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.NO_SUCH_HOST;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.PROTOCOL_HANDLER_MISSING;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.TIMEOUT_EXCEPTION;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.UNAUTHORIZED_401;
import static io.harness.delegate.task.helm.HelmExceptionConstants.HelmCliErrorMessages.UNKNOWN_COMMAND_FLAG;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_HIST;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_INSTALL;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_LIST_RELEASE;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_RENDER_CHART;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_ROLLBACK;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_HELM_UPGRADE;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.DEFAULT_HINT_REPO_ADD;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_401_UNAUTHORIZED;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_403_FORBIDDEN;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_404_HELM_REPO;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_CHART_VERSION_IMPROPER_CONSTRAINT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_INVALID_YAML;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_MALFORMED_URL;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_MISSING_PROTOCOL_HANDLER;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_CHART_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_CHART_VERSION_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_RELEASES_ERROR;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_RESOURCE_CONFLICT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_TIMEOUT_ERROR;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_UNKNOWN_COMMAND_FLAG;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_VALIDATE_ERROR;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Singleton;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@Singleton
public class HelmClientRuntimeExceptionHandler implements ExceptionHandler {
  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(HelmClientRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    final HelmClientRuntimeException helmClientRuntimeException = (HelmClientRuntimeException) exception;
    final HelmClientException cause = helmClientRuntimeException.getHelmClientException();

    // handling common exceptions:
    final String lowerCaseMessage = lowerCase(cause.getMessage());
    if (lowerCaseMessage.contains(UNKNOWN_COMMAND_FLAG)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_UNKNOWN_COMMAND_FLAG, EXPLAIN_UNKNOWN_COMMAND_FLAG, cause);
    }

    // specific exceptions:
    switch (cause.getHelmCliCommandType()) {
      case REPO_ADD:
        return handleRepoAddException(cause);
      case FETCH:
        return handleFetchException(cause);
      case INSTALL:
        return handleInstallException(cause);
      case RELEASE_HISTORY:
        return handleReleaseHistException(cause);
      case LIST_RELEASE:
        return handleListReleaseException(cause);
      case RENDER_CHART:
        return handleRenderChartException(cause);
      case ROLLBACK:
        return handleRollbackException(cause);
      case UPGRADE:
        return handleUpgradeException(cause);
      default:
    }

    return new InvalidRequestException(defaultIfEmpty(cause.getMessage(), ""));
  }

  private WingsException handleRollbackException(HelmClientException helmClientException) {
    final String lowerCaseMessage = lowerCase(StringUtils.defaultString(helmClientException.getMessage()));
    if (lowerCaseMessage.contains(NO_DEPLOYED_RELEASES)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_NO_RELEASES_ERROR, EXPLAIN_NO_RELEASES_ERROR, helmClientException);
    }
    String commandType = helmClientException.getHelmCliCommandType().toString();
    if (lowerCaseMessage.contains(TIMEOUT_EXCEPTION)) {
      return NestedExceptionUtils.hintWithExplanationException(
          String.format(HINT_TIMEOUT_ERROR, commandType), EXPLAIN_TIMEOUT_EXCEPTION + commandType, helmClientException);
    }

    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_ROLLBACK, DEFAULT_EXPLAIN_ROLLBACK, helmClientException);
  }

  private WingsException handleRenderChartException(HelmClientException helmClientException) {
    final String message = StringUtils.defaultString(helmClientException.getMessage());
    final String lowerCaseMessage = lowerCase(message);
    if (lowerCaseMessage.contains("invalid kubernetes yaml")) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_INVALID_YAML, EXPLAIN_INVALID_YAML, new InvalidRequestException(message));
    } else if (lowerCaseMessage.contains("chart.yaml file is missing") || lowerCaseMessage.contains("not found")) {
      return NestedExceptionUtils.hintWithExplanationException(
          "Working directory may be incorrect. Check if the chart name, sub-chart name (if applicable) are correct/valid",
          "Unable to render the chart as Chart.yaml file is missing", new InvalidRequestException(message));
    }
    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_RENDER_CHART, DEFAULT_EXPLAIN_RENDER_CHART, helmClientException);
  }

  private WingsException handleListReleaseException(HelmClientException helmClientException) {
    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_LIST_RELEASE, DEFAULT_EXPLAIN_LIST_RELEASE, helmClientException);
  }

  private WingsException handleReleaseHistException(HelmClientException helmClientException) {
    // TODO: handle few more cases

    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_HIST, DEFAULT_EXPLAIN_HELM_HIST, helmClientException);
  }

  private WingsException handleRepoAddException(HelmClientException helmClientException) {
    final String message = StringUtils.defaultString(helmClientException.getMessage());
    final String lowerCaseMessage = lowerCase(message);
    if (lowerCaseMessage.contains(NOT_FOUND_404)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_404_HELM_REPO, EXPLAIN_404_HELM_REPO, new InvalidRequestException(message));
    } else if (lowerCaseMessage.contains(UNAUTHORIZED_401)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_401_UNAUTHORIZED, EXPLAIN_401_UNAUTHORIZED, new InvalidRequestException(message));
    } else if (lowerCaseMessage.contains(FORBIDDEN_403)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_403_FORBIDDEN, EXPLAIN_403_FORBIDDEN, new InvalidRequestException(message));
    } else if (lowerCaseMessage.contains(NO_SUCH_HOST)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_MALFORMED_URL, EXPLAIN_MALFORMED_URL, new InvalidRequestException(message));
    } else if (lowerCaseMessage.contains(PROTOCOL_HANDLER_MISSING)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_MISSING_PROTOCOL_HANDLER, EXPLAIN_MISSING_PROTOCOL_HANDLER, new InvalidRequestException(message));
    }
    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_REPO_ADD, DEFAULT_EXPLAIN_REPO_ADD, new InvalidRequestException(message));
  }

  private WingsException handleFetchException(HelmClientException helmClientException) {
    final String lowerCaseMessage = lowerCase(StringUtils.defaultString(helmClientException.getMessage()));

    if (lowerCaseMessage.contains(NO_CHART_FOUND)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_NO_CHART_FOUND, EXPLAIN_NO_CHART_FOUND, helmClientException);
    } else if (lowerCaseMessage.contains(NO_CHART_VERSION_FOUND)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_NO_CHART_VERSION_FOUND, EXPLAIN_NO_CHART_VERSION_FOUND, helmClientException);
    } else if (lowerCaseMessage.contains(CHART_VERSION_IMPROPER_CONSTRAINT)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_CHART_VERSION_IMPROPER_CONSTRAINT, EXPLAIN_CHART_VERSION_IMPROPER_CONSTRAINT, helmClientException);
    }

    return helmClientException;
  }

  private WingsException handleInstallException(HelmClientException helmClientException) {
    final String lowerCaseMessage = lowerCase(StringUtils.defaultString(helmClientException.getMessage()));
    if (lowerCaseMessage.contains(INVALID_VALUE_TYPE)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_VALIDATE_ERROR, EXPLAIN_VALIDATE_ERROR, helmClientException);
    }

    if (lowerCaseMessage.contains(EXISTING_RESOURCE_CONFLICT)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_RESOURCE_CONFLICT, EXPLAIN_RESOURCE_CONFLICT, helmClientException);
    }
    String commandType = helmClientException.getHelmCliCommandType().toString();
    if (lowerCaseMessage.contains(TIMEOUT_EXCEPTION)) {
      return NestedExceptionUtils.hintWithExplanationException(
          String.format(HINT_TIMEOUT_ERROR, commandType), EXPLAIN_TIMEOUT_EXCEPTION + commandType, helmClientException);
    }

    // TODO : Handle some more negative scenarios here

    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_INSTALL, DEFAULT_EXPLAIN_HELM_INSTALL, helmClientException);
  }

  private WingsException handleUpgradeException(HelmClientException helmClientException) {
    final String lowerCaseMessage = lowerCase(StringUtils.defaultString(helmClientException.getMessage()));
    if (lowerCaseMessage.contains(NO_DEPLOYED_RELEASES)) {
      return NestedExceptionUtils.hintWithExplanationException(
          HINT_NO_RELEASES_ERROR, EXPLAIN_NO_RELEASES_ERROR, helmClientException);
    }
    String commandType = helmClientException.getHelmCliCommandType().toString();
    if (lowerCaseMessage.contains(TIMEOUT_EXCEPTION)) {
      return NestedExceptionUtils.hintWithExplanationException(
          String.format(HINT_TIMEOUT_ERROR, commandType), EXPLAIN_TIMEOUT_EXCEPTION + commandType, helmClientException);
    }

    return NestedExceptionUtils.hintWithExplanationException(
        DEFAULT_HINT_HELM_UPGRADE, DEFAULT_EXPLAIN_HELM_UPGRADE, helmClientException);
  }
}