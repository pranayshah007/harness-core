/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.eks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;
import static io.harness.k8s.K8sConstants.API_VERSION;
import static io.harness.k8s.K8sConstants.EKS_AUTH_PLUGIN_BINARY;
import static io.harness.k8s.K8sConstants.EKS_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_EXTERNAL_ID;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_I;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_ROLE;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_TOKEN;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY;
import static io.harness.k8s.eks.EksConstants.REGION_DELIMITER;
import static io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.beans.AwsInternalConfig.AwsInternalConfigBuilder;
import io.harness.aws.v2.eks.EksV2Client;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesConfig.KubernetesConfigBuilder;
import io.harness.k8s.model.kubeconfig.EnvVariable;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.Exec.ExecBuilder;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.logging.LogCallback;

import software.wings.beans.AwsCrossAccountAttributes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.eks.model.Cluster;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsEKSV2DelegateTaskHelper {
  @Inject private EksV2Client eksV2Client;

  public KubernetesConfig getKubeConfig(
      AwsConnectorDTO awsConnectorDTO, String regionAndClusterName, String namespace, LogCallback logCallback) {
    if (EmptyPredicate.isEmpty(regionAndClusterName)) {
      throw new InvalidRequestException("Cluster name is empty in Inframapping");
    }
    String[] regionClusterName = regionAndClusterName.split(REGION_DELIMITER, 2);
    if (regionClusterName.length != 2) {
      throw new InvalidRequestException(String.format("Cluster name is not in proper format. "
              + "Expected format is <Region/ClusterName> i.e us-east-1/test-cluster. Provided cluster name: [%s]",
          regionAndClusterName));
    }
    String regionName = regionClusterName[0];
    String clusterName = regionClusterName[1];
    AwsInternalConfig awsInternalConfig = createAwsInternalConfig(awsConnectorDTO, regionName);

    DescribeClusterResponse describeClusterResponse =
        eksV2Client.describeClusters(awsInternalConfig, regionName, clusterName);
    Cluster cluster = describeClusterResponse.cluster();
    if (cluster != null) {
      KubernetesConfigBuilder kubernetesConfigBuilder =
          KubernetesConfig.builder()
              .masterUrl(cluster.endpoint() + "/")
              .namespace(isNotBlank(namespace) ? namespace : "default")
              .caCert((cluster.certificateAuthority().data()).toCharArray())
              .useKubeconfigAuthentication(true);
      if (isExecAuthPluginBinaryAvailable(EKS_AUTH_PLUGIN_BINARY, logCallback)) {
        kubernetesConfigBuilder.authType(KubernetesClusterAuthType.EXEC_OAUTH);
        kubernetesConfigBuilder.exec(getEksExecConfig(clusterName, awsInternalConfig));
      } else {
        throw new InvalidRequestException(
            "aws-iam-authenticator is required to be installed for using AWS EKS Infrastructure for k8s deployment");
      }
      return kubernetesConfigBuilder.build();
    }
    throw new InvalidRequestException(String.format(
        "Cannot get details of required cluster: [%s] via AWS EKS DescribeClusterRequest", regionAndClusterName));
  }
  private Exec getEksExecConfig(String clusterName, AwsInternalConfig awsInternalConfig) {
    ExecBuilder execBuilder = Exec.builder()
                                  .apiVersion(API_VERSION)
                                  .command(EKS_AUTH_PLUGIN_BINARY)
                                  .interactiveMode(InteractiveMode.NEVER)
                                  .provideClusterInfo(true)
                                  .installHint(EKS_AUTH_PLUGIN_INSTALL_HINT);

    if (awsInternalConfig.getAccessKey() != null) {
      EnvVariable awsAccessKey = EnvVariable.builder()
                                     .name(EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID)
                                     .value(String.valueOf(awsInternalConfig.getAccessKey()))
                                     .build();

      EnvVariable awsSecretAccessKey = EnvVariable.builder()
                                           .name(EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY)
                                           .value(String.valueOf(awsInternalConfig.getSecretKey()))
                                           .build();

      List<EnvVariable> envVariables = new ArrayList<>();
      envVariables.add(awsAccessKey);
      envVariables.add(awsSecretAccessKey);
      execBuilder.env(envVariables);
    }

    List<String> args = new ArrayList<>();
    args.add(EKS_KUBECFG_ARGS_TOKEN);
    args.add(EKS_KUBECFG_ARGS_I);
    args.add(clusterName);

    if (awsInternalConfig.isAssumeCrossAccountRole()) {
      args.add(EKS_KUBECFG_ARGS_ROLE);
      args.add(awsInternalConfig.getCrossAccountAttributes().getCrossAccountRoleArn());
      String externalId = awsInternalConfig.getCrossAccountAttributes().getExternalId();
      if (isNotEmpty(externalId)) {
        args.add(EKS_KUBECFG_ARGS_EXTERNAL_ID);
        args.add(externalId);
      }
    }

    execBuilder.args(args);

    return execBuilder.build();
  }

  public AwsInternalConfig createAwsInternalConfig(AwsConnectorDTO awsConnectorDTO, String region) {
    if (awsConnectorDTO == null) {
      throw new InvalidArgumentsException("Aws Connector DTO cannot be null");
    }
    AwsInternalConfigBuilder awsInternalConfigBuilder = AwsInternalConfig.builder();

    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    if (MANUAL_CREDENTIALS == credential.getAwsCredentialType()) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) credential.getConfig();

      String accessKey = "";

      if (awsManualConfigSpecDTO != null) {
        accessKey = getSecretAsStringFromPlainTextOrSecretRef(
            awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef());
      }

      if (EmptyPredicate.isEmpty(accessKey)) {
        throw new InvalidArgumentsException(Pair.of("accessKey", "Missing or empty"));
      }

      char[] secretKey = null;

      if (awsManualConfigSpecDTO != null && awsManualConfigSpecDTO.getSecretKeyRef() != null) {
        secretKey = awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue();
      }

      awsInternalConfigBuilder.accessKey(accessKey.toCharArray()).secretKey(secretKey).build();

    } else if (INHERIT_FROM_DELEGATE == credential.getAwsCredentialType()) {
      awsInternalConfigBuilder.useEc2IamCredentials(true);
    } else if (IRSA == credential.getAwsCredentialType()) {
      awsInternalConfigBuilder.useIRSA(true);
    } else {
      throw new InvalidArgumentsException("Aws Credential type is undefined");
    }

    CrossAccountAccessDTO crossAccountAccess = credential.getCrossAccountAccess();
    if (crossAccountAccess != null) {
      awsInternalConfigBuilder.assumeCrossAccountRole(true);
      String crossAccountRoleArn = crossAccountAccess.getCrossAccountRoleArn();
      if (isEmpty(crossAccountRoleArn)) {
        throw new InvalidArgumentsException("Aws crossAccountRoleArn cannot be empty");
      }
      awsInternalConfigBuilder.crossAccountAttributes(AwsCrossAccountAttributes.builder()
                                                          .crossAccountRoleArn(crossAccountRoleArn)
                                                          .externalId(crossAccountAccess.getExternalId())
                                                          .build());
    }

    awsInternalConfigBuilder.defaultRegion(region);
    return awsInternalConfigBuilder.build();
  }
}
