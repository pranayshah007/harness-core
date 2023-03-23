/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.HarnessLabels;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class KubernetesExceptionExplanation {
  public final String K8S_API_GENERIC_NETWORK_EXCEPTION = "Harness delegate is not able to reach Kubernetes API";
  public final String K8S_API_SOCKET_TIMEOUT_EXCEPTION = "Connection timed out while trying to reach Kubernetes API";
  public final String K8S_API_IO_EXCEPTION = "Kubernetes API call failed due to I/O error";
  public final String K8S_API_FORBIDDEN_EXCEPTION =
      "Kubernetes user missing role or permission to use specified resource";
  public final String K8S_API_UNAUTHORIZED_EXCEPTION = "Unable to authenticate using provided Kubernetes credentials";
  public final String K8S_API_VALIDATION_ERROR =
      "Some of the provided values in Kubernetes configuration missing or invalid (i.e. namespace, release name)";
  public final String K8S_API_SSL_VALIDATOR = "Failed to establish SSL connection with Kubernetes API server";

  public final String APPLY_NO_FILEPATH_SPECIFIED = "No file specified in the state";
  public final String WAIT_FOR_STEADY_STATE_FAILED = "Resources failed to reach steady state.\n";
  public final String WAIT_FOR_STEADY_STATE_FAILED_OUTPUT = "%s failed with exit code: %d and output: %s";
  public final String WAIT_FOR_STEADY_STATE_JOB_FAILED = "Job execution failed";
  public final String WAIT_FOR_STEADY_STATE_CRD_FAILED = "Steady check condition [%s] never resolved to true";

  public final String CANARY_NO_WORKLOADS_FOUND =
      "No workload found in the Manifests. Can't do Canary Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Canary workflow type.";
  public final String CANARY_MULTIPLE_WORKLOADS =
      "Found %d workloads in manifest: [%s]. Canary deployment supports only one workload";
  public final String BG_NO_WORKLOADS_FOUND =
      "No workload found in the Manifests. Can't do  Blue/Green Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Blue/Green deployment";
  public final String BG_MULTIPLE_WORKLOADS =
      "Found %d workloads in manifest: [%s]. Blue/Green Workflows support a single Deployment, DeploymentConfig (OpenShift) or StatefulSet workload only";
  public final String BG_NO_SERVICE_FOUND = "Service is required for BlueGreen deployments";
  public final String BG_MULTIPLE_PRIMARY_SERVICE =
      "Found %s services in manifest: [%s]. Could not locate primary service";
  public final String BG_CONFLICTING_SERVICE =
      "For blue/green deployment, the label [" + HarnessLabels.color + "] is required in service selector";
  public final String BG_SWAP_SERVICES_SERVICE_NOT_FOUND = "Service [%s] not found.";

  public final String SCALE_CLI_FAILED = "Failed to scale resource [%s]\n";

  public final String ROLLBACK_CR_APPLY_FAILED =
      "Failed while rolling back custom resource by applying previous release manifests";
  public final String ROLLBACK_CLI_FAILED = "%s failed with exit code: %d";
  public final String ROLLBACK_CLI_FAILED_OUTPUT = "%s failed with exit code: %d and output: %s";

  public final String FAILED_TO_READ_FILE = "Could not read file at path [%s].";
  public final String INVALID_RESOURCE_KIND_NAME_FORMAT =
      "Resource name '%s' doesn't match kind/name or namespace/kind/name format";

  public final String K8S_CHARACTER_ERROR = "The following resource(s) are breaching the naming constraints:\n%s";
  public final String FAILED_COMMAND_WITH_EXITCODE_AND_OUTPUT =
      "Command executed: [%s] %nExit Code: [%s] %nOutput: [%s] %nkubectl binary path: [%s]";
  public final String FAILED_COMMAND_RESOURCES_NOT_APPLIED = "Resources not applied: [%s]";
  public final String FAILED_COMMAND_KUBECTL_VERSION = "kubernetes version: [%s]";
  public final String FAILED_COMMAND_WITH_EXITCODE =
      "Command executed: [%s] %nExit Code: [%s] %nkubectl binary path: [%s]";
  public final String VALIDATION_FAILED_UNKNOWN_FIELD = "The manifest contains the following unknown fields: \n%s";
  public final String VALIDATION_FAILED_INVALID_TYPE = "Invalid type value for [%s].\n";
  public final String K8S_API_FORBIDDEN_ERROR =
      "The user [%s] does not have adequate permissions in the namespace [%s]. {Resource: [%s], API Group: [%s]}";
  public final String UNRESOLVED_MANIFEST_FIELD =
      "The provided values.yaml file(s) may contain a field with missing/null value or refer an expression which is not being resolved.";

  public final String API_CLIENT_CREATE_FAILED = "Failed to create Kubernetes API client with given credentials";
  public final String API_CLIENT_CA_CERT_INVALID_FORMAT = "Failed to parse CA certificate";
  public final String API_CLIENT_CA_CERT_INCOMPLETE = "Invalid or corrupted CA certificate";
  public final String MANIFEST_RENDER_ERROR_GO_TEMPLATE = "Failed to render manifests. %n%s";
  public final String MANIFEST_RENDER_ERROR_HELM =
      "Failed to render manifests with error: %n%s %n%nCommand Executed: %n%s";
  public final String IMMUTABLE_FIELD = "An update was attempted to some immutable field(s).";
  public final String MISSING_RESOURCE = "Manifests may contain references to some missing resource(s).";
  public final String UNSUPPORTED_VALUE = "Manifests may contain some unsupported value(s).";
  public final String FORBIDDEN_MESSAGE = "User might be missing roles/permissions to create/update resources.";
  public final String MISSING_REQUIRED_FIELD = "Some required field(s) is/are missing from the manifest.";
  public final String MISSING_OBJECT_ERROR = "The resource may be deleted from the server.";
  public final String INVALID_VALUES_YAML = "Values yaml file(s) are not valid.";
  public final String EXPECTED_BLOCK_END = "Expected a 'block end' but found: scalar";
  public final String INVALID_TEMPLATE_PATH = "The openshift template file path is empty";
  public final String MANIFEST_SIZE_LIMIT = "The max limit of manifest file size is 5MB.";
  public final String OPENSHIFT_RENDER_ERROR = "Failed to render template with error: %n%s %n%nCommand Executed: %n%s";
  public final String FILE_PATH_NOT_PART_OF_MANIFEST_FORMAT = "File path: %s is not part of manifest directory";
  public final String NO_FILES_EXISTS_IN_MANIFEST_DIRECTORY = "No files found in manifest directory";
}
