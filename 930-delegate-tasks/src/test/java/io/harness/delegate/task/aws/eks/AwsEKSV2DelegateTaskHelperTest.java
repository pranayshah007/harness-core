/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.eks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.EKS_AUTH_PLUGIN_INSTALL_HINT;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID;
import static io.harness.k8s.eks.EksConstants.EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.eks.EksV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.kubeconfig.EnvVariable;
import io.harness.k8s.model.kubeconfig.Exec;
import io.harness.k8s.model.kubeconfig.InteractiveMode;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.beans.AwsCrossAccountAttributes;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import software.amazon.awssdk.services.eks.model.Certificate;
import software.amazon.awssdk.services.eks.model.Cluster;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;

@OwnedBy(CDP)
public class AwsEKSV2DelegateTaskHelperTest extends CategoryTest {
  @Mock private EksV2Client eksV2Client;
  @Spy @InjectMocks private AwsEKSV2DelegateTaskHelper awsEKSV2DelegateTaskHelper;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetKubeConfig() {
    MockitoAnnotations.openMocks(this);
    AwsConnectorDTO awsConnectorDTO = mock(AwsConnectorDTO.class);
    Cluster cluster = mock(Cluster.class);
    software.amazon.awssdk.services.eks.model.Certificate certificate = mock(Certificate.class);
    LogCallback logCallback = mock(LogCallback.class);

    DescribeClusterResponse describeClusterResponse = DescribeClusterResponse.builder().cluster(cluster).build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder()
                                              .accessKey("access-key".toCharArray())
                                              .secretKey("secret-key".toCharArray())
                                              .assumeCrossAccountRole(false)
                                              .build();

    doReturn(awsInternalConfig).when(awsEKSV2DelegateTaskHelper).createAwsInternalConfig(any(), anyString());
    doReturn(describeClusterResponse).when(eksV2Client).describeClusters(any(), anyString(), anyString());

    doReturn("endpoint").when(cluster).endpoint();
    doReturn(certificate).when(cluster).certificateAuthority();
    doReturn("data").when(certificate).data();

    MockedStatic kubeConfigAuthPluginHelper = mockStatic(KubeConfigAuthPluginHelper.class);
    Mockito.when(KubeConfigAuthPluginHelper.isExecAuthPluginBinaryAvailable(any(), any())).thenReturn(true);

    KubernetesConfig result = awsEKSV2DelegateTaskHelper.getKubeConfig(
        awsConnectorDTO, "us-west-2/test-cluster", "test-namespace", logCallback);
    kubeConfigAuthPluginHelper.close();
    assertNotNull(result);
    assertEquals("endpoint/", result.getMasterUrl());
    assertEquals("test-namespace", result.getNamespace());
    assertThat(result.getCaCert()).isEqualTo("data".toCharArray());
    assertEquals(KubernetesClusterAuthType.EXEC_OAUTH, result.getAuthType());

    Exec exec = result.getExec();
    assertNotNull(exec);
    assertEquals("client.authentication.k8s.io/v1beta1", exec.getApiVersion());
    assertEquals("aws-iam-authenticator", exec.getCommand());
    assertEquals(InteractiveMode.NEVER, exec.getInteractiveMode());
    assertTrue(exec.isProvideClusterInfo());
    assertEquals(EKS_AUTH_PLUGIN_INSTALL_HINT, exec.getInstallHint());

    List<String> args = exec.getArgs();
    assertNotNull(args);
    assertEquals(3, args.size());
    assertEquals("token", args.get(0));
    assertEquals("-i", args.get(1));
    assertEquals("test-cluster", args.get(2));

    List<EnvVariable> env = exec.getEnv();
    assertNotNull(env);
    assertEquals(2, env.size());
    assertEquals(EKS_KUBECFG_ENV_VARS_AWS_ACCESS_KEY_ID, env.get(0).getName());
    assertEquals("access-key", env.get(0).getValue());
    assertEquals(EKS_KUBECFG_ENV_VARS_AWS_SECRET_ACCESS_KEY, env.get(1).getName());
    assertEquals("secret-key", env.get(1).getValue());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetKubeConfigInvalidClusterNameformat() {
    MockitoAnnotations.openMocks(this);
    AwsConnectorDTO awsConnectorDTO = mock(AwsConnectorDTO.class);
    LogCallback logCallback = mock(LogCallback.class);

    assertThatThrownBy(
        () -> awsEKSV2DelegateTaskHelper.getKubeConfig(awsConnectorDTO, "test-cluster", "test-namespace", logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("Cluster name is not in proper format. "
                + "Expected format is <Region/ClusterName> i.e us-east-1/test-cluster. Provided cluster name: [%s]",
            "test-cluster"));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCreateAwsInternalConfig() {
    MockitoAnnotations.openMocks(this);
    final String passwordRefIdentifier = "passwordRefIdentifier";
    final String accessKey = "accessKey";
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier(passwordRefIdentifier).scope(Scope.ACCOUNT).build();
    final AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder().secretKeyRef(passwordSecretRef).accessKey(accessKey).build();
    final AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder()
            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
            .crossAccountAccess(CrossAccountAccessDTO.builder().crossAccountRoleArn("arn").externalId("id").build())
            .config(awsManualConfigSpecDTO)
            .build();
    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    final AwsInternalConfig awsInternalConfig =
        awsEKSV2DelegateTaskHelper.createAwsInternalConfig(awsConnectorDTO, null);
    assertThat(awsInternalConfig).isNotNull();
    assertThat(awsInternalConfig)
        .isEqualTo(AwsInternalConfig.builder()
                       .accessKey(accessKey.toCharArray())
                       .secretKey(null)
                       .assumeCrossAccountRole(true)
                       .crossAccountAttributes(
                           AwsCrossAccountAttributes.builder().crossAccountRoleArn("arn").externalId("id").build())
                       .build());
  }
}
