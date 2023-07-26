/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public interface ManifestType {
  Set<String> K8S_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(
      ManifestType.K8Manifest, ManifestType.HelmChart, ManifestType.Kustomize, ManifestType.OpenshiftTemplate);
  Set<String> HELM_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(ManifestType.HelmChart);
  Set<String> ECS_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.EcsTaskDefinition, ManifestType.EcsServiceDefinition,
          ManifestType.EcsScalableTargetDefinition, ManifestType.EcsScalingPolicyDefinition);
  Set<String> SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.VALUES, ManifestType.KustomizePatches, ManifestType.OpenshiftParam,
          ManifestType.TAS_MANIFEST, ManifestType.TAS_VARS, ManifestType.TAS_AUTOSCALER, ManifestType.HelmRepoOverride,
          ManifestType.EcsTaskDefinition, ManifestType.EcsServiceDefinition, ManifestType.EcsScalingPolicyDefinition,
          ManifestType.EcsScalableTargetDefinition);
  Set<String> ASG_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(ManifestType.AsgLaunchTemplate,
      ManifestType.AsgConfiguration, ManifestType.AsgScalingPolicy, ManifestType.AsgScheduledUpdateGroupAction);

  Set<String> GOOGLE_FUNCTIONS_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(ManifestType.GoogleCloudFunctionDefinition);
  Set<String> GOOGLE_FUNCTIONS_GEN_ONE_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.GoogleCloudFunctionGenOneDefinition);
  Set<String> AWS_LAMBDA_SUPPORTED_MANIFEST_TYPES =
      ImmutableSet.of(ManifestType.AwsLambdaFunctionDefinition, ManifestType.AwsLambdaFunctionAliasDefinition);
  Set<String> AWS_SAM_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(ManifestType.AwsSamDirectory);
  Set<String> MULTIPLE_SUPPORTED_MANIFEST_TYPES = ImmutableSet.of(ManifestType.HelmChart);

  String K8Manifest = "K8sManifest";
  String VALUES = "Values";
  String CONFIG_FILE = "configFiles";
  String HelmChart = "HelmChart";
  String Kustomize = "Kustomize";
  String OpenshiftTemplate = "OpenshiftTemplate";
  String OpenshiftParam = "OpenshiftParam";
  String KustomizePatches = "KustomizePatches";
  String ServerlessAwsLambda = "ServerlessAwsLambda";
  String ReleaseRepo = "ReleaseRepo";
  String DeploymentRepo = "DeploymentRepo";
  String EcsTaskDefinition = "EcsTaskDefinition";
  String EcsServiceDefinition = "EcsServiceDefinition";
  String EcsScalingPolicyDefinition = "EcsScalingPolicyDefinition";
  String EcsScalableTargetDefinition = "EcsScalableTargetDefinition";
  String EcsRunTaskRequestDefinition = "EcsRunTaskRequestDefinition";
  String TAS_MANIFEST = "TasManifest";
  String TAS_VARS = "TasVars";
  String TAS_AUTOSCALER = "TasAutoScaler";
  String AsgLaunchTemplate = "AsgLaunchTemplate";
  String AsgConfiguration = "AsgConfiguration";
  String AsgScalingPolicy = "AsgScalingPolicy";
  String AsgScheduledUpdateGroupAction = "AsgScheduledUpdateGroupAction";
  String GoogleCloudFunctionDefinition = "GoogleCloudFunctionDefinition";
  String GoogleCloudFunctionGenOneDefinition = "GoogleCloudFunctionGenOneDefinition";
  String HelmRepoOverride = "HelmRepoOverride";
  String AwsLambdaFunctionDefinition = "AwsLambdaFunctionDefinition";
  String AwsLambdaFunctionAliasDefinition = "AwsLambdaFunctionAliasDefinition";
  String AwsSamDirectory = "AwsSamDirectory";

  static HashSet<String> getAllManifestTypes() {
    return new HashSet<>(Arrays.asList(ManifestType.K8Manifest, ManifestType.VALUES, ManifestType.OpenshiftTemplate,
        ManifestType.KustomizePatches, ManifestType.Kustomize, ManifestType.HelmChart, ManifestType.CONFIG_FILE,
        ManifestType.OpenshiftParam, ManifestType.ServerlessAwsLambda, ManifestType.ReleaseRepo,
        ManifestType.DeploymentRepo, ManifestType.EcsTaskDefinition, ManifestType.EcsServiceDefinition,
        ManifestType.EcsScalableTargetDefinition, ManifestType.EcsScalingPolicyDefinition, ManifestType.TAS_MANIFEST,
        ManifestType.TAS_VARS, ManifestType.TAS_AUTOSCALER, AsgLaunchTemplate, AsgConfiguration, AsgScalingPolicy,
        AsgScheduledUpdateGroupAction, ManifestType.GoogleCloudFunctionDefinition,
        ManifestType.AwsLambdaFunctionDefinition, ManifestType.AwsLambdaFunctionAliasDefinition,
        ManifestType.AwsSamDirectory, ManifestType.GoogleCloudFunctionGenOneDefinition));
  }
}
