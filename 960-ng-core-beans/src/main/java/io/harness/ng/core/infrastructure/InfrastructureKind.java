/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public interface InfrastructureKind {
  String KUBERNETES_DIRECT = "KubernetesDirect";
  String KUBERNETES_GCP = "KubernetesGcp";
  String SERVERLESS_AWS_LAMBDA = "ServerlessAwsLambda";
  String PDC = "Pdc";
  String KUBERNETES_AZURE = "KubernetesAzure";
  String SSH_WINRM_AZURE = "SshWinRmAzure";
  String SSH_WINRM_AWS = "SshWinRmAws";
  String AZURE_WEB_APP = "AzureWebApp";
  String ECS = "ECS";
  String GITOPS = "GitOps";
  String CUSTOM_DEPLOYMENT = "CustomDeployment";
  String ELASTIGROUP = "Elastigroup";
  String TAS = "TAS";
  String ASG = "Asg";
  String GOOGLE_CLOUD_FUNCTIONS = "GoogleCloudFunctions";
  String AWS_LAMBDA = "AwsLambda";
  String AWS_SAM = "AWS_SAM";
  String KUBERNETES_AWS = "KubernetesAws";
  String KUBERNETES_RANCHER = "KubernetesRancher";
}
