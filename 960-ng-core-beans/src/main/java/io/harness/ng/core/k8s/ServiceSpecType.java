/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.k8s;

public interface ServiceSpecType {
  String GITOPS = "GitOps";
  String KUBERNETES = "Kubernetes";
  String SSH = "Ssh";
  String ECS = "ECS";
  String NATIVE_HELM = "NativeHelm";
  String TAS = "TAS";
  String SERVERLESS_AWS_LAMBDA = "ServerlessAwsLambda";
  String WINRM = "WinRm";
  String AZURE_WEBAPP = "AzureWebApp";
  String CUSTOM_DEPLOYMENT = "CustomDeployment";
  String ELASTIGROUP = "Elastigroup";
  String ASG = "Asg";
  String GOOGLE_CLOUD_FUNCTIONS = "GoogleCloudFunctions";
  String AWS_LAMBDA = "AwsLambda";
  String AWS_SAM = "AWS_SAM";
}
