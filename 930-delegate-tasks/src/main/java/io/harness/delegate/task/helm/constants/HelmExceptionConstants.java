/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class HelmExceptionConstants {
  public HelmExceptionConstants() {
    throw new UnsupportedOperationException("not supported");
  }

  public static final class Hints {
    public static final String HINT_401_UNAUTHORIZED = "Provide valid username/password for the helm repo";
    public static final String HINT_403_FORBIDDEN =
        "Make sure that the provided credentials have permissions to access the index.yaml in the helm repo. If this is a public repo, make sure it is not deprecated";
    public static final String HINT_404_HELM_REPO =
        "The given URL does not point to a valid Helm Chart Repo. Make sure to generate index.yaml using the \"helm repo index\" and upload it to the repo.";
    public static final String HINT_MALFORMED_URL =
        "Could not resolve the helm repo server URL. Please provide a reachable URL";
    public static final String HINT_MISSING_PROTOCOL_HANDLER =
        "Install protocol handler for the helm repo. For eg. If using gs://, make sure to install the Google Storage protocol support for Helm";
    public static final String DEFAULT_HINT_REPO_ADD =
        "Make sure that the repo can be added using the helm cli \"repo add\" command";

    public static final String HELM_CUSTOM_EXCEPTION_HINT =
        "Provided helm chart path point to a file inside the helm chart. Path to the helm chart directory should be provided instead";

    public static final String HELM_CHART_EXCEPTION = "Exception in deploying helm chart:";

    public static final String HELM_CHART_REGEX = "Source.*and destination.*are the same";
    public static final String HELM_CHART_ERROR_EXPLANATION =
        "This error can be possible if provided helm chart path is a file path instead of folder path";
    public static final String HINT_NO_CHART_FOUND = "Provide an existing helm chart and/or valid chart path";
    public static final String HINT_NO_CHART_VERSION_FOUND = "Provide existing helm chart version";
    public static final String HINT_CHART_VERSION_IMPROPER_CONSTRAINT = "Provide existing and valid helm chart version";
    public static final String HINT_VALIDATE_ERROR = "Could not validate the .yaml file";
    public static final String HINT_NO_RELEASES_ERROR =
        "Try deleting the release or change the status of the helm-managed release secret manually";
    public static final String HINT_INVALID_YAML = "K8s yaml is invalid/incorrect.";
    public static final String HINT_RESOURCE_CONFLICT = "There is conflict due to existing resource(s)";
    public static final String HINT_TIMEOUT_ERROR =
        "--timeout flag can be configured inside the service manifest section, under Advanced section of Manifest Details. Select the command type %s and pass the timeout flag with an appropriate value.";
    public static final String DEFAULT_HINT_HELM_INSTALL =
        "Make sure that the helm chart can be installed successfully using the helm cli.";
    public static final String DEFAULT_HINT_HELM_UPGRADE =
        "Make sure that the helm chart can be upgraded successfully using the helm cli.";
    public static final String DEFAULT_HINT_HELM_HIST = "Helm history command has failed";
    public static final String DEFAULT_HINT_HELM_LIST_RELEASE = "Helm List releases command has failed";
    public static final String DEFAULT_HINT_HELM_RENDER_CHART = "Failed to render helm chart";
    public static final String DEFAULT_HINT_HELM_ROLLBACK = "Failed to rollback helm chart";
    public static final String HINT_UNKNOWN_COMMAND_FLAG = "Helm incorrect command flag";
    public static final String HINT_RESOURCE_ALREADY_EXISTS =
        "Try to reinstall the resource under the new release or change the resource name";

    public Hints() {
      throw new UnsupportedOperationException("not supported");
    }
  }
  public static final class Explanations {
    public static final String EXPLAIN_401_UNAUTHORIZED =
        "Given credentials are not authorized to access the helm chart repo server";
    public static final String EXPLAIN_403_FORBIDDEN = "The Helm chart repo server denied access.";
    public static final String EXPLAIN_404_HELM_REPO = "No Index.yaml file found";
    public static final String EXPLAIN_MALFORMED_URL = "The Helm chart repo server is not reachable";
    public static final String EXPLAIN_MISSING_PROTOCOL_HANDLER =
        "Protocol is not http/https. Could not find protocol handler.";
    public static final String DEFAULT_EXPLAIN_REPO_ADD = "Unable to add helm repo using the \"helm repo add command\"";

    public static final String EXPLAIN_NO_CHART_FOUND =
        "Provided chart name doesn't exist in in the chart repository or chart path is incorrect";
    public static final String EXPLAIN_NO_CHART_VERSION_FOUND =
        "Provided chart version doesn't exist in in the chart repository";
    public static final String EXPLAIN_CHART_VERSION_IMPROPER_CONSTRAINT =
        "Provided chart version doesn't match helm expected version format (e.x. https://semver.org/)";
    public static final String EXPLAIN_VALIDATE_ERROR =
        "Given helm chart contains .yaml file with invalid/missing fields. Please check the .yaml file.";
    public static final String EXPLAIN_NO_RELEASES_ERROR =
        "There are no releases in the release history with 'Deployed' status";
    public static final String EXPLAIN_UNKNOWN_COMMAND_FLAG =
        "Provided Command flag is incorrect or invalid. Please check.";
    public static final String EXPLAIN_TIMEOUT_EXCEPTION = "Time limit exceeded for the Helm command: ";
    public static final String EXPLAIN_INVALID_YAML =
        "Error processing yaml manifest. Please check the indentation, any invalid/missing fields etc. For more details, please check logs.";
    public static final String DEFAULT_EXPLAIN_HELM_INSTALL = "Unable to install helm chart.";
    public static final String DEFAULT_EXPLAIN_HELM_UPGRADE = "Unable to upgrade helm chart.";
    public static final String EXPLAIN_RESOURCE_CONFLICT =
        "Rendered manifests contain a resource which already exists. Try deleting these resources and re-run";
    public static final String DEFAULT_EXPLAIN_HELM_HIST = "Unable to execute release history command";
    public static final String DEFAULT_EXPLAIN_LIST_RELEASE = "Unable to execute list release command";
    public static final String DEFAULT_EXPLAIN_RENDER_CHART =
        "Unable to render helm chart, please check logs for further details";
    public static final String DEFAULT_EXPLAIN_ROLLBACK = "Unable to rollback helm chart. ";
    public static final String EXPLAIN_RESOURCE_ALREADY_EXISTS =
        "The resource is already part of different helm release";
    public Explanations() {
      throw new UnsupportedOperationException("not supported");
    }
  }

  public static class HelmCliErrorMessages {
    public static final String NOT_FOUND_404 = "404 not found";
    public static final String UNAUTHORIZED_401 = "401 unauthorized";
    public static final String FORBIDDEN_403 = "403 forbidden";
    public static final String NO_SUCH_HOST = "no such host";
    public static final String PROTOCOL_HANDLER_MISSING = "could not find protocol handler";
    public static final String NO_CHART_FOUND = "no chart name found";
    public static final String NO_CHART_VERSION_FOUND = "no chart version found";
    public static final String CHART_VERSION_IMPROPER_CONSTRAINT = "improper constraint";
    public static final String INVALID_VALUE_TYPE = "error validating data";
    public static final String EXISTING_RESOURCE_CONFLICT = "existing resource conflict";
    public static final String UNKNOWN_COMMAND_FLAG = "unknown flag";
    public static final String NO_DEPLOYED_RELEASES = "no deployed releases";
    public static final String TIMEOUT_EXCEPTION = "timed out";
    public static final String RESOURCE_ALREADY_EXISTS = "exists and cannot be imported into the current release";

    public HelmCliErrorMessages() {
      throw new UnsupportedOperationException("not supported");
    }
  }
}