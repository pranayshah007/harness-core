/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_CHART_VERSION_IMPROPER_CONSTRAINT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_CHART_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_CHART_VERSION_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Explanations.EXPLAIN_NO_RELEASES_ERROR;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_CHART_VERSION_IMPROPER_CONSTRAINT;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_CHART_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_CHART_VERSION_FOUND;
import static io.harness.delegate.task.helm.HelmExceptionConstants.Hints.HINT_NO_RELEASES_ERROR;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExplanationException;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.helm.HelmCliCommandType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class HelmClientRuntimeExceptionHandlerTest extends CategoryTest {
  private HelmClientRuntimeExceptionHandler handler = new HelmClientRuntimeExceptionHandler();

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void exceptions() {
    assertThat(HelmClientRuntimeExceptionHandler.exceptions()).containsExactly(HelmClientRuntimeException.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handle404() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Error: Looks like \"https://helmrepo.com\" is not a valid chart repository or cannot be reached: Failed to fetch https://helmrepo.com/index.yaml : 404 Not Found",
        HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .contains(
            "The given URL does not point to a valid Helm Chart Repo. Make sure to generate index.yaml using the \"helm repo index\" and upload it to the repo");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("No Index.yaml file found");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handle403() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Error: Looks like \"https://kubernetes-charts.storage.googleapis.com/\" is not a valid chart repository or cannot be reached: Failed to fetch https://kubernetes-charts.storage.googleapis.com/index.yaml : 403 Forbidden  ",
        HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .contains(
            "Make sure that the provided credentials have permissions to access the index.yaml in the helm repo. If this is a public repo, make sure it is not deprecated");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("The Helm chart repo server denied access");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handle401() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Error: looks like \"http://localhost:8082/\" is not a valid chart repository or cannot be reached: failed to fetch http://localhost:8082/index.yaml : 401 Unauthorized",
        HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains("Provide valid username/password for the helm repo");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage())
        .contains("Given credentials are not authorized to access the helm chart repo server");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleMissingProtocolHandler() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(
        new HelmClientException("Error: Could not find protocol handler for: gs", HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .contains(
            "Install protocol handler for the helm repo. For eg. If using gs://, make sure to install the Google Storage protocol support for Helm");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage())
        .contains("Protocol is not http/https. Could not find protocol handler.");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleNoSuchHost() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Error: Looks like \"http://localhost:8083/\" is not a valid chart repository or cannot be reached: Get \"http://localhost:8083/index.yaml\": dial tcp: lookup localhosdt on 8.8.8.8:53: no such host",
        HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .contains("Could not resolve the helm repo server URL. Please provide a reachable URL");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("The Helm chart repo server is not reachable");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleRandomErrorMessageFromHelmClient() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(
        new HelmClientException("Error: Some Error I have not seen before", HelmCliCommandType.REPO_ADD));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage())
        .contains("Make sure that the repo can be added using the helm cli \"repo add\" command");
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage())
        .contains("Unable to add helm repo using the \"helm repo add command\"");
    assertThat(handledException.getCause().getCause()).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleNoChartFound() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        ": Error: chart \"invalid\" matching latest not found in Htttp_Charts_Stable index. (try 'helm repo update'): no chart name found",
        HelmCliCommandType.FETCH));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains(HINT_NO_CHART_FOUND);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains(EXPLAIN_NO_CHART_FOUND);
    assertThat(handledException.getCause().getCause()).isInstanceOf(HelmClientException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleNoChartVersionFound() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Error: chart \"invalid\" matching invalid-version not found in Htttp_Charts_Stable index. (try 'helm repo update'): no chart version found for invalid-version",
        HelmCliCommandType.FETCH));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains(HINT_NO_CHART_VERSION_FOUND);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains(EXPLAIN_NO_CHART_VERSION_FOUND);
    assertThat(handledException.getCause().getCause()).isInstanceOf(HelmClientException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleInvalidChartVersionFormat() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(new HelmClientException(
        "Failed to fetch chart \"some-chart\"  from repo \"Playground\". Please check if the chart is present in the repo. Details: Error: chart \"some-chart\" matching \"invalid-semantic\" not found in AWS_Playground index. (try 'helm repo update'). improper constraint: invalid-semantic",
        HelmCliCommandType.FETCH));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains(HINT_CHART_VERSION_IMPROPER_CONSTRAINT);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains(EXPLAIN_CHART_VERSION_IMPROPER_CONSTRAINT);
    assertThat(handledException.getCause().getCause()).isInstanceOf(HelmClientException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void handleUnhandledHelmCommandType() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(
        new HelmClientException("Error: Some Error I have not seen before", HelmCliCommandType.INIT));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(InvalidRequestException.class);
    assertThat(handledException.getMessage()).contains("Error: Some Error I have not seen before");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void handleNoDeployedReleasesError() {
    HelmClientRuntimeException runtimeException = new HelmClientRuntimeException(
        new HelmClientException("Release rel-051222 has no deployed releases", HelmCliCommandType.UPGRADE));
    final WingsException handledException = handler.handleException(runtimeException);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).contains(HINT_NO_RELEASES_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains(EXPLAIN_NO_RELEASES_ERROR);
    assertThat(handledException.getCause().getCause()).isInstanceOf(HelmClientException.class);
    assertThat(handledException.getCause().getCause().getMessage()).isNotEmpty();
  }
}
