/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.connector;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.aws.AwsClient;
import io.harness.category.element.UnitTests;
import io.harness.connector.ImageSecretBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.ImageDetails;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;

import com.amazonaws.services.ecr.model.AmazonECRException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ImageSecretBuilderTest extends CategoryTest {
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private AwsClient awsClient;
  @InjectMocks private ImageSecretBuilder imageSecretBuilder;

  private static final String imageName = "IMAGE";
  private static final String tag = "TAG";
  private static final String registryUrl = "https://index.docker.io/v1/";
  private static final String userName = "usr";
  private static final String password = "pwd";
  private static final String gcpImageName = "us.gcr.io/ci/addon";
  private static final String ecrAccountID = "foo";
  private static final String ecrRegion = "us-east-1";
  private static final String ecrImageName = "foo.dkr.ecr.us-east-1.amazonaws.com/foo";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldConvertCustomSecretTextVariables() {}

  private ConnectorDetails getConnectorDetails(ConnectorConfigDTO config, ConnectorType type) {
    return ConnectorDetails.builder()
        .connectorType(type)
        .connectorConfig(config)
        .encryptedDataDetails(new ArrayList<>())
        .build();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getDockerJSONEncodedImageCredentialsWithEmptyCreds() {
    ImageDetails imageDetails1 = ImageDetails.builder().name(imageName).tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector1 =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails1).build();
    assertNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector1));

    ImageDetails imageDetails2 = ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).build();
    ImageDetailsWithConnector imageDetailsWithConnector2 =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails2).build();
    assertNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector2));

    ImageDetails imageDetails3 =
        ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).username(userName).build();
    ImageDetailsWithConnector imageDetailsWithConnector3 =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails3).build();
    assertNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector3));

    ImageDetails imageDetails4 =
        ImageDetails.builder().name(imageName).tag(tag).registryUrl(registryUrl).password(password).build();
    ImageDetailsWithConnector imageDetailsWithConnector4 =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails4).build();
    assertNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector4));
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getDockerJSONEncodedImageCredentialsWithCreds() {
    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        DockerUserNamePasswordDTO.builder()
            .username("username")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();

    DockerConnectorDTO dockerConnectorDTO =
        DockerConnectorDTO.builder()
            .dockerRegistryUrl("https://index.docker.io/v1/")
            .auth(DockerAuthenticationDTO.builder()
                      .authType(DockerAuthType.USER_PASSWORD)
                      .credentials(DockerUserNamePasswordDTO.builder().username("username").build())
                      .build())
            .build();
    ConnectorDetails connectorDetails = getConnectorDetails(dockerConnectorDTO, ConnectorType.DOCKER);

    ImageDetails imageDetails = ImageDetails.builder()
                                    .name(imageName)
                                    .tag(tag)
                                    .registryUrl(registryUrl)
                                    .username(userName)
                                    .password(password)
                                    .build();

    when(secretDecryptor.decrypt(eq(DockerUserNamePasswordDTO.builder().username("username").build()),
             eq(connectorDetails.getEncryptedDataDetails())))
        .thenReturn(dockerUserNamePasswordDTO);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    assertNotNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getGCRJSONEncodedImageCredentialsWithCredError() {
    GcpManualDetailsDTO gcpManualDetailsDTO =
        GcpManualDetailsDTO.builder()
            .secretKeyRef(SecretRefData.builder().decryptedValue("key".toCharArray()).build())
            .build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                          .config(gcpManualDetailsDTO)
                                                          .build())
                                          .build();

    ConnectorDetails connectorDetails = getConnectorDetails(gcpConnectorDTO, ConnectorType.GCP);
    ImageDetails imageDetails = ImageDetails.builder()
                                    .name(imageName)
                                    .tag(tag)
                                    .registryUrl(registryUrl)
                                    .username(userName)
                                    .password(password)
                                    .build();

    when(secretDecryptor.decrypt(any(), eq(connectorDetails.getEncryptedDataDetails())))
        .thenReturn(gcpManualDetailsDTO);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getGCRJSONEncodedImageCredentials() {
    GcpManualDetailsDTO gcpManualDetailsDTO =
        GcpManualDetailsDTO.builder()
            .secretKeyRef(SecretRefData.builder().decryptedValue("key".toCharArray()).build())
            .build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                                          .config(gcpManualDetailsDTO)
                                                          .build())
                                          .build();

    ConnectorDetails connectorDetails = getConnectorDetails(gcpConnectorDTO, ConnectorType.GCP);
    ImageDetails imageDetails = ImageDetails.builder().name(gcpImageName).tag(tag).build();

    when(secretDecryptor.decrypt(any(), eq(connectorDetails.getEncryptedDataDetails())))
        .thenReturn(gcpManualDetailsDTO);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    assertNotNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void getGCRJSONEncodedImageCredentialsInheritFromDelegate() {
    GcpDelegateDetailsDTO gcpDelegateDetailsDTO = GcpDelegateDetailsDTO.builder().build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder()
                                          .credential(GcpConnectorCredentialDTO.builder()
                                                          .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                                                          .config(gcpDelegateDetailsDTO)
                                                          .build())
                                          .build();

    ConnectorDetails connectorDetails = getConnectorDetails(gcpConnectorDTO, ConnectorType.GCP);
    ImageDetails imageDetails = ImageDetails.builder().name(gcpImageName).tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();
    assertNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector));
  }

  @Test()
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getECRJSONEncodedImageCredentials() {
    String token = "bar";
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    AwsManualConfigSpecDTO manualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder()
            .accessKey("test")
            .secretKeyRef(SecretRefData.builder().decryptedValue("test".toCharArray()).build())
            .build();

    ConnectorDetails connectorDetails = getConnectorDetails(awsConnectorDTO, ConnectorType.AWS);
    ImageDetails imageDetails = ImageDetails.builder().name(ecrImageName).tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(manualConfigSpecDTO);
    when(awsClient.getAmazonEcrAuthToken(any(), any(), any())).thenReturn(token);

    assertNotNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getECRJSONEncodedImageCredentialsWithTokenErr() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    AwsManualConfigSpecDTO manualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder()
            .accessKey("test")
            .secretKeyRef(SecretRefData.builder().decryptedValue("test".toCharArray()).build())
            .build();
    ConnectorDetails connectorDetails = getConnectorDetails(awsConnectorDTO, ConnectorType.AWS);
    ImageDetails imageDetails = ImageDetails.builder().name(ecrImageName).tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(manualConfigSpecDTO);
    when(awsClient.getAmazonEcrAuthToken(any(), any(), any())).thenThrow(AmazonECRException.class);
    assertNotNull(imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector));
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getECRJSONEncodedImageCredentialsWithImageErr() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    ConnectorDetails connectorDetails = getConnectorDetails(awsConnectorDTO, ConnectorType.AWS);
    ImageDetails imageDetails = ImageDetails.builder().name("foo").tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getECRJSONEncodedImageCredentialsWithNoAccountInImageErr() {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();

    ConnectorDetails connectorDetails = getConnectorDetails(awsConnectorDTO, ConnectorType.AWS);
    ImageDetails imageDetails = ImageDetails.builder().name("foo.amazonaws.com/test").tag(tag).build();
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageDetails(imageDetails).imageConnectorDetails(connectorDetails).build();

    imageSecretBuilder.getJSONEncodedImageCredentials(imageDetailsWithConnector);
  }
}
