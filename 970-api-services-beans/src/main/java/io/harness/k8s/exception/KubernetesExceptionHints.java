/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.exception;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.k8s.model.HarnessAnnotations;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@UtilityClass
public class KubernetesExceptionHints {
  public final String K8S_API_GENERIC_NETWORK_EXCEPTION =
      "Please connect remotely to Harness delegate and verify network connection between Kubernetes cluster and Harness delegate.";
  public final String K8S_API_SOCKET_TIMEOUT_EXCEPTION =
      "Please connect remotely to Harness delegate and verify if Harness delegate is whitelisted to access Kubernetes API.";
  public final String K8S_API_FORBIDDEN_EXCEPTION =
      "Check configured Kubernetes user permissions and authorization policies. \nTo know more about Roles, ClusterRoles and ClusterRoleBindings, refer: https://kubernetes.io/docs/reference/access-authn-authz/rbac/";
  public final String K8S_API_UNAUTHORIZED_EXCEPTION = "Check Kubernetes connector credentials.";
  public final String K8S_API_VALIDATION_ERROR = "Validate Kubernetes infrastructure configuration";
  public final String K8S_API_SSL_VALIDATOR = "Check if provided CA certificate is valid for this kubernetes cluster";

  public final String DRY_RUN_MANIFEST_FAILED =
      "Check manifest output for invalid fields name, types or empty manifest";
  public final String NO_OBJECT_PASSED =
      "Manifest file contains no object to apply. Reason can be:\n1. Actual manifest file missing from configuration\n2. Only values.yaml found \n3. Files configured are empty.";
  public final String READ_MANIFEST_FAILED =
      "Manifest file contains invalid kubernetes yaml. Check invalid fields name, types or empty manifest";

  public final String APPLY_MANIFEST_FAILED =
      "Manifest could contain invalid/missing values, one of resources name may not match kubernetes requirements or missing permissions to create or update specific kubernetes resources";
  public final String APPLY_NO_FILEPATH_SPECIFIED = "Please specify a valid file path to manifests from repository";

  public final String WAIT_FOR_STEADY_STATE_FAILED =
      "Please check for possible issues in:\n1. Execution logs(under \"Wait for Steady State\" section). \n2. Deployment pods probe checks. \n3. Availability of nodes/resources in the target cluster. \n4. Valid image pull secrets.";
  public final String DEPLOYMENT_PROGRESS_DEADLINE_DOC_REFERENCE =
      "\nK8s Documentation (Progress Deadline Error for Deployment): https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#failed-deployment";
  public final String WAIT_FOR_STEADY_STATE_CLI_FAILED =
      "Resource may be deleted in process or may be related to an intermittent connection issue";
  public final String WAIT_FOR_STEADY_STATE_JOB_FAILED = "Verify job logs or output for the failure reason";
  public final String WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONDITION = "The steady check condition [%s] is wrong";
  public final String WAIT_FOR_STEADY_STATE_CRD_FAILED_CHECK_CONTROLLER = "Check if custom controller is running";

  public final String CANARY_NO_WORKLOADS_FOUND =
      "Add Deployment, DeploymentConfig (Openshift) or StatefulSet workload in manifest";

  public final String CANARY_MULTIPLE_WORKLOADS =
      "Mark non-primary workloads with annotation " + HarnessAnnotations.directApply + ": true";
  public final String BG_NO_WORKLOADS_FOUND =
      "Add Deployment, DeploymentConfig (Openshift) or StatefulSet workload in manifest";
  public final String BG_MULTIPLE_WORKLOADS =
      "Mark non-primary workloads with annotation " + HarnessAnnotations.directApply + ": true";
  public final String BG_NO_SERVICE_FOUND =
      "Add at least one service in manifest. Two services [i.e. primary and stage] can be specified with annotations "
      + HarnessAnnotations.primaryService + " and " + HarnessAnnotations.stageService;
  public final String BG_MULTIPLE_PRIMARY_SERVICE = "Mark primary and stage service with "
      + HarnessAnnotations.primaryService + " and " + HarnessAnnotations.stageService + " annotations";
  public final String BG_CONFLICTING_SERVICE = "Delete existing service [%s] to proceed";
  public final String BG_SWAP_SERVICES_SERVICE_NOT_FOUND =
      "Swap services requires primary and stage services to be present.";

  public final String SCALE_CLI_FAILED_GENERIC =
      "Failed to scale resource. \nPlease check the inputs to the scale step: namespace(if provided), resource type or resource name.";
  public final String ROLLBACK_CLI_FAILED = "Check if resource %s exists";

  public final String FAILED_TO_READ_FILE = "Failed to read file at path [%s].";
  public final String CHECK_IF_FILE_EXIST = "Check if file [%s] exists.";
  public final String INVALID_RESOURCE_KIND_NAME_FORMAT =
      "Provide resource name in kind/name or namespace/kind/name format, e.x. 'Deployment/%s' instead of '%s'";
  public final String GENERIC_CLI_FAILURE = "kubectl command has failed to execute successfully.";
  public final String K8S_CHARACTER_ERROR =
      " For more information on kubernetes naming constraints, refer: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/.";
  public final String VALIDATION_FAILED_INVALID_TYPE = "Please check the provided manifest.";
  public final String VALIDATION_FAILED_UNKNOWN_FIELD = "Please check the provided manifest.";
  public final String SCALE_CLI_FAILED =
      "Please check the inputs provided to the scale step: namespace(if provided), resource type or resource name.";
  public final String UNRESOLVED_MANIFEST_FIELD =
      "Please check values.yaml file(s) for any missing/null fields inside manifests or any unresolved expressions referencing harness variables.";

  public final String API_CLIENT_CREATE_FAILED = "Check kubernetes connector for invalid values";
  public final String API_CLIENT_CA_CERT_INVALID =
      "Check if given %s field is a valid X.509 certificate and is from a trusted or verified source.";
  public final String API_CLIENT_CA_CERT_INCOMPLETE =
      "Check if given %s field is properly encoded in base64 and all data, header and footer are in place";
  public final String MANIFEST_RENDER_ERROR_GO_TEMPLATE =
      "Please check if the given manifests are valid and can be rendered by Go template. To know more about Go templating, refer: \nhttps://community.harness.io/t/harness-local-go-templating/460 \nhttps://docs.harness.io/article/mwy6zgz8gu-use-go-templating-in-kubernetes-manifests#notes";
  public final String MANIFEST_RENDER_ERROR_HELM =
      "Please check if the given manifests are valid and can be rendered by the `helm template` command.";
  public final String IMMUTABLE_FIELD = "Please delete the conflicting resource from the cluster and try again.";
  public final String MISSING_RESOURCE =
      "Please check manifest(s) for any references to missing resources and create them.";
  public final String UNSUPPORTED_VALUE = "Please correct any unsupported values in the manifest(s).";
  public final String MISSING_REQUIRED_FIELD = "Please add the required field(s) to the manifest.";
  public final String MISSING_OBJECT_ERROR = "Please verify that the resource exists and try again.";
  public final String INVALID_VALUES_YAML = "Please check if the given values yaml file(s) are valid YAMLs.";
  public final String BASE_64_ENCODED_CHECK =
      "If you have certs/secrets which are referenced in templates or values.yaml file(s), ensure that these secrets are base64 encoded";
  public final String INVALID_TEMPLATE_PATH = "Please check if the given openshift template file are valid";
  public final String INVALID_OPENSHIFT_FILES =
      "Please check if the given openshift template and param files are valid";
  public final String TOO_LARGE_MANIFEST_YAML = "The dry run manifest yaml file might be too large to process.";
  public final String AVAILABLE_FILES_IN_MANIFEST_DIRECTORY_FORMAT = "Available files in manifest directory:\n%s";
  public final String MAYBE_DID_YOU_MEAN_FILE_FORMAT = "Maybe did you mean: %s";
  public final String NO_FILES_FOUND_IN_MANIFEST_DIRECTORY =
      "Check manifest folder/file path in manifest configuration";
  public final String HASH_CALCULATION_FAILED_ERROR =
      "Failed to create manifests file to be used for generating hash. Please check resources";
}
