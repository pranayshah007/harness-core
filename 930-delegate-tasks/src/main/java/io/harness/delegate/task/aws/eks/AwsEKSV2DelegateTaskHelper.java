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
import static io.harness.k8s.eks.EksConstants.AWS_STS_REGIONAL;
import static io.harness.k8s.eks.EksConstants.AWS_STS_REGIONAL_ENDPOINTS;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_EXTERNAL_ID;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_I;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_ROLE;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ARGS_TOKEN;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY;
import static io.harness.k8s.eks.EksConstants.REGION_DELIMITER;
import static io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.beans.AwsInternalConfig.AwsInternalConfigBuilder;
import io.harness.aws.v2.eks.EksV2Client;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsEKSV2DelegateTaskHelper {
  static final String EMPTY_CLUSTER_NAME_ERROR_MESSAGE =
      "The cluster name provided in the EKS K8s infrastructure mapping is empty.";
  private static final String CLUSTER_NAME_SPLIT_ERROR_MESSAGE =
      "Failed to extract AWS region and cluster name from [%s].";
  private static final String CLUSTER_FETCH_ERROR_MESSAGE =
      "Failed to perform AWS EKS:DescribeClusterRequest on cluster: [%s] ,region: [%s]";
  static final String CLUSTER_NAME_SPLIT_ERROR_MESSAGE_WITH_EXPLANATION = CLUSTER_NAME_SPLIT_ERROR_MESSAGE
      + " The expected format for an EKS cluster name is `<region>/<clusterName>`. Prefixing the AWS region with the cluster name is not needed if `region` parameter in Infrastructure definition is specified";
  private static final String AWS_IAM_AUTHENTICATOR_MISSING_MESSAGE =
      "aws-iam-authenticator is required to be installed for using AWS EKS Infrastructure for k8s deployment";
  @Inject private EksV2Client eksV2Client;

  public KubernetesConfig getKubeConfig(EksK8sInfraDelegateConfig eksK8sInfraDelegateConfig, LogCallback logCallback) {
    if (!isExecAuthPluginBinaryAvailable(EKS_AUTH_PLUGIN_BINARY, logCallback)) {
      throw new InvalidRequestException(AWS_IAM_AUTHENTICATOR_MISSING_MESSAGE);
    }

    AwsConnectorDTO awsConnectorDTO = eksK8sInfraDelegateConfig.getAwsConnectorDTO();
    String namespace = eksK8sInfraDelegateConfig.getNamespace();
    String region = eksK8sInfraDelegateConfig.getRegion();

    Pair<String, String> regionClusterName = getRegionAndClusterName(eksK8sInfraDelegateConfig.getCluster(), region);
    String regionName = regionClusterName.getLeft();
    String clusterName = regionClusterName.getRight();
    AwsInternalConfig awsInternalConfig = createAwsInternalConfig(awsConnectorDTO, regionName);

    DescribeClusterResponse describeClusterResponse =
        eksV2Client.describeClusters(awsInternalConfig, regionName, clusterName);
    Cluster cluster = describeClusterResponse.cluster();

    if (cluster == null) {
      throw new InvalidRequestException(format(CLUSTER_FETCH_ERROR_MESSAGE, clusterName, regionName));
    }

    return KubernetesConfig.builder()
        .masterUrl(cluster.endpoint() + "/")
        .namespace(isNotBlank(namespace) ? namespace : "default")
        .caCert((cluster.certificateAuthority().data()).toCharArray())
        .exec(getEksExecConfig(clusterName, awsInternalConfig, eksK8sInfraDelegateConfig.isAddRegionalParam()))
        .authType(KubernetesClusterAuthType.EXEC_OAUTH)
        .useKubeconfigAuthentication(true)
        .build();
  }

  static Pair<String, String> getRegionAndClusterName(String regionAndClusterName, String region) {
    if (isEmpty(regionAndClusterName)) {
      throw new InvalidRequestException(EMPTY_CLUSTER_NAME_ERROR_MESSAGE);
    }

    if (isEmpty(region)) {
      return splitRegionAndClusterName(regionAndClusterName);
    }

    String[] result = regionAndClusterName.split(REGION_DELIMITER, 2);
    if (!regionAndClusterName.startsWith(region) || result.length == 1) {
      return Pair.of(region, regionAndClusterName);
    }

    return Pair.of(region, result[1]);
  }

  private static Pair<String, String> splitRegionAndClusterName(String regionAndClusterName) {
    String[] result = regionAndClusterName.split(REGION_DELIMITER, 2);
    if (result.length != 2) {
      log.error(format(CLUSTER_NAME_SPLIT_ERROR_MESSAGE, regionAndClusterName));
      throw new InvalidRequestException(
          format(CLUSTER_NAME_SPLIT_ERROR_MESSAGE_WITH_EXPLANATION, regionAndClusterName));
    }
    return Pair.of(result[0], result[1]);
  }

  private Exec getEksExecConfig(String clusterName, AwsInternalConfig awsInternalConfig, boolean isAddRegionalParam) {
    ExecBuilder execBuilder = Exec.builder()
                                  .apiVersion(API_VERSION)
                                  .command(EKS_AUTH_PLUGIN_BINARY)
                                  .interactiveMode(InteractiveMode.NEVER)
                                  .provideClusterInfo(true)
                                  .installHint(EKS_AUTH_PLUGIN_INSTALL_HINT);
    List<EnvVariable> envVariables = new ArrayList<>();
    if (awsInternalConfig.getAccessKey() != null) {
      EnvVariable awsAccessKey = EnvVariable.builder()
                                     .name(EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID)
                                     .value(String.valueOf(awsInternalConfig.getAccessKey()))
                                     .build();

      EnvVariable awsSecretAccessKey = EnvVariable.builder()
                                           .name(EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY)
                                           .value(String.valueOf(awsInternalConfig.getSecretKey()))
                                           .build();
      envVariables.add(awsAccessKey);
      envVariables.add(awsSecretAccessKey);
    }

    if (isAddRegionalParam) {
      EnvVariable awsRegionalEndpoint =
          EnvVariable.builder().name(AWS_STS_REGIONAL_ENDPOINTS).value(AWS_STS_REGIONAL).build();
      envVariables.add(awsRegionalEndpoint);
    }

    if (!isEmpty(envVariables)) {
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
