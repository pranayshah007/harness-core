/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public enum ManifestConfigType {
  @JsonProperty(ManifestType.HelmChart) HELM_CHART(ManifestType.HelmChart),
  @JsonProperty(ManifestType.HelmRepoOverride) HELM_REPO_OVERRIDE(ManifestType.HelmRepoOverride),
  @JsonProperty(ManifestType.K8Manifest) K8_MANIFEST(ManifestType.K8Manifest),
  @JsonProperty(ManifestType.Kustomize) KUSTOMIZE(ManifestType.Kustomize),
  @JsonProperty(ManifestType.KustomizePatches) KUSTOMIZE_PATCHES(ManifestType.KustomizePatches),
  @JsonProperty(ManifestType.OpenshiftParam) OPEN_SHIFT_PARAM(ManifestType.OpenshiftParam),
  @JsonProperty(ManifestType.OpenshiftTemplate) OPEN_SHIFT_TEMPLATE(ManifestType.OpenshiftTemplate),
  @JsonProperty(ManifestType.VALUES) VALUES(ManifestType.VALUES),
  @JsonProperty(ManifestType.ServerlessAwsLambda) SERVERLESS_AWS_LAMBDA(ManifestType.ServerlessAwsLambda),
  @JsonProperty(ManifestType.ReleaseRepo) RELEASE_REPO(ManifestType.ReleaseRepo),
  @JsonProperty(ManifestType.DeploymentRepo) DEPLOYMENT_REPO(ManifestType.DeploymentRepo),
  @JsonProperty(ManifestType.EcsTaskDefinition) ECS_TASK_DEFINITION(ManifestType.EcsTaskDefinition),
  @JsonProperty(ManifestType.EcsServiceDefinition) ECS_SERVICE_DEFINITION(ManifestType.EcsServiceDefinition),
  @JsonProperty(ManifestType.EcsScalableTargetDefinition)
  ECS_SCALABLE_TARGET_DEFINITION(ManifestType.EcsScalableTargetDefinition),
  @JsonProperty(ManifestType.EcsScalingPolicyDefinition)
  ECS_SCALING_POLICY_DEFINITION(ManifestType.EcsScalingPolicyDefinition),
  @JsonProperty(ManifestType.TAS_MANIFEST) TAS_MANIFEST(ManifestType.TAS_MANIFEST),
  @JsonProperty(ManifestType.TAS_VARS) TAS_VARS(ManifestType.TAS_VARS),
  @JsonProperty(ManifestType.TAS_AUTOSCALER) TAS_AUTOSCALER(ManifestType.TAS_AUTOSCALER),
  @JsonProperty(ManifestType.AsgLaunchTemplate) ASG_LAUNCH_TEMPLATE(ManifestType.AsgLaunchTemplate),
  @JsonProperty(ManifestType.AsgConfiguration) ASG_CONFIGURATION(ManifestType.AsgConfiguration),
  @JsonProperty(ManifestType.AsgScalingPolicy) ASG_SCALING_POLICY(ManifestType.AsgScalingPolicy),
  @JsonProperty(ManifestType.AsgScheduledUpdateGroupAction)
  ASG_SCHEDULED_UPDATE_GROUP_ACTION(ManifestType.AsgScheduledUpdateGroupAction),
  @JsonProperty(ManifestType.GoogleCloudFunctionDefinition)
  CLOUD_FUNCTION_DEFINITION(ManifestType.GoogleCloudFunctionDefinition),
  @JsonProperty(ManifestType.AwsLambdaFunctionDefinition) AWS_LAMBDA(ManifestType.AwsLambdaFunctionDefinition),
  @JsonProperty(ManifestType.AwsLambdaFunctionAliasDefinition)
  AWS_LAMBDA_ALIAS(ManifestType.AwsLambdaFunctionAliasDefinition),
  @JsonProperty(ManifestType.AwsSamDirectory) AWS_SAM_DIRECTORY(ManifestType.AwsSamDirectory),
  @JsonProperty(ManifestType.GoogleCloudFunctionGenOneDefinition)
  CLOUD_FUNCTION_GEN_ONE_DEFINITION(ManifestType.GoogleCloudFunctionGenOneDefinition);

  private final String displayName;

  ManifestConfigType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ManifestConfigType getManifestConfigType(@JsonProperty("type") String displayName) {
    for (ManifestConfigType manifestConfigType : ManifestConfigType.values()) {
      if (manifestConfigType.displayName.equalsIgnoreCase(displayName)) {
        return manifestConfigType;
      }
    }

    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(ManifestConfigType.values())));
  }
}
