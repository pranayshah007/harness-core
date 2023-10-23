/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.aws.AwsExceptionHandler.handleAmazonClientException;
import static io.harness.aws.AwsExceptionHandler.handleAmazonServiceException;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsAccessKeyCredential;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.aws.CrossAccountAccess;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.govern.Switch;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(CI)
@Singleton
@Slf4j
public class ImageSecretBuilder {
  private static final String BASE_GCR_HOSTNAME = "gcr.io";
  private static final String GCR_USERNAME = "_json_key";
  private static final String BASE_ECR_HOSTNAME = "amazonaws.com";
  private static final String HTTPS_URL = "https://";
  private static final String PATH_SEPARATOR = "/";
  private static final String DOT_SEPARATOR = "\\.";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String AUTH = "auth";

  private static final String DOCKER_REGISTRY_V2 = "https://index.docker.io/v2/";
  private static final String DOCKER_REGISTRY_V1 = "https://index.docker.io/v1/";

  @Inject private SecretDecryptor secretDecryptor;
  @Inject private AwsClient awsClient;

  public String getJSONEncodedImageCredentials(ImageDetailsWithConnector imageDetailsWithConnector) {
    ConnectorDetails connectorDetails = imageDetailsWithConnector.getImageConnectorDetails();
    if (connectorDetails == null) {
      return null;
    }

    String imageName = imageDetailsWithConnector.getImageDetails().getName();
    if (connectorDetails.getConnectorType() == ConnectorType.DOCKER) {
      return jsonEncodeCredentials(getDockerCredentials(connectorDetails));
    } else if (connectorDetails.getConnectorType() == ConnectorType.GCP) {
      return jsonEncodeCredentials(getGCRCredentials(imageName, connectorDetails));
    } else if (connectorDetails.getConnectorType() == ConnectorType.AWS) {
      return jsonEncodeAWSCredentials(getECRCredentials(imageName, connectorDetails));
    }
    return null;
  }

  public String getJSONEncodedAzureCredentials(ImageCredentials imageCredentials) {
    return new JSONObject()
        .put(imageCredentials.getRegistryUrl(),
            new JSONObject()
                .put(USERNAME, imageCredentials.getUserName())
                .put(PASSWORD, imageCredentials.getPassword()))
        .toString();
  }

  public ImageCredentials getImageCredentials(ImageDetailsWithConnector imageDetailsWithConnector) {
    ConnectorDetails connectorDetails = imageDetailsWithConnector.getImageConnectorDetails();
    if (connectorDetails == null) {
      return null;
    }

    String imageName = imageDetailsWithConnector.getImageDetails().getName();
    if (connectorDetails.getConnectorType() == ConnectorType.DOCKER) {
      ImageCredentials dockerCredentials = getDockerCredentials(connectorDetails);
      return convertV2ConnectorsToV1(dockerCredentials);
    } else if (connectorDetails.getConnectorType() == ConnectorType.GCP) {
      return getGCRCredentials(imageName, connectorDetails);
    } else if (connectorDetails.getConnectorType() == ConnectorType.AWS) {
      ImageCredentials ecrCredentials = getECRCredentials(imageName, connectorDetails);
      return decodeAWSTokenToGetUserAndPassword(ecrCredentials);
    }
    return null;
  }

  private ImageCredentials convertV2ConnectorsToV1(ImageCredentials dockerCredentials) {
    if (dockerCredentials != null && DOCKER_REGISTRY_V2.equals(dockerCredentials.getRegistryUrl())) {
      return ImageCredentials.builder()
          .registryUrl(DOCKER_REGISTRY_V1)
          .userName(dockerCredentials.getUserName())
          .password(dockerCredentials.getPassword())
          .build();
    } else {
      return dockerCredentials;
    }
  }

  private ImageCredentials decodeAWSTokenToGetUserAndPassword(ImageCredentials imageCredentials) {
    if (imageCredentials == null) {
      return null;
    }

    String token = imageCredentials.getPassword();
    byte[] decodedBytes = Base64.getDecoder().decode(token);
    String decodedString = new String(decodedBytes);
    String[] split = decodedString.split(":");
    if (split.length != 2) {
      throw new InvalidArgumentsException(format("ecr token format is invalid"));
    }
    return ImageCredentials.builder()
        .userName(split[0])
        .password(split[1])
        .registryUrl(imageCredentials.getRegistryUrl())
        .build();
  }

  private String jsonEncodeAWSCredentials(ImageCredentials ecrCredentials) {
    if (null == ecrCredentials) {
      return null;
    }
    return new JSONObject()
        .put(ecrCredentials.getRegistryUrl(), new JSONObject().put(AUTH, ecrCredentials.getPassword()))
        .toString();
  }

  private ImageCredentials getDockerCredentials(ConnectorDetails connectorDetails) {
    DockerConnectorDTO dockerConfig = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    if (dockerConfig.getAuth().getAuthType() == DockerAuthType.USER_PASSWORD) {
      log.info("Decrypting docker username and password for  ConnectorDisconnectHandler id:[{}], type:[{}]",
          connectorDetails.getIdentifier(), connectorDetails.getConnectorType());
      DockerUserNamePasswordDTO dockerUserNamePasswordDTO = (DockerUserNamePasswordDTO) secretDecryptor.decrypt(
          dockerConfig.getAuth().getCredentials(), connectorDetails.getEncryptedDataDetails());
      log.info("Decrypted docker username and password for id:[{}], type:[{}]", connectorDetails.getIdentifier(),
          connectorDetails.getConnectorType());
      String registryUrl = dockerConfig.getDockerRegistryUrl();
      if (dockerUserNamePasswordDTO == null || dockerUserNamePasswordDTO.getPasswordRef() == null
          || isEmpty(dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue())) {
        throw new InvalidArgumentsException(
            format("Password should not be empty for docker ConnectorDisconnectHandler: %s", connectorDetails.getIdentifier()),
            WingsException.USER);
      }
      String username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          dockerUserNamePasswordDTO.getUsername(), dockerUserNamePasswordDTO.getUsernameRef());

      String password = String.valueOf(dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue());

      validateDecodedDockerCredentials(username, password, connectorDetails.getIdentifier());
      validateDecodedDockerRegistryUrl(registryUrl, connectorDetails.getIdentifier());
      return ImageCredentials.builder().registryUrl(registryUrl).userName(username).password(password).build();
    } else if (dockerConfig.getAuth().getAuthType() == DockerAuthType.ANONYMOUS) {
      String registryUrl = dockerConfig.getDockerRegistryUrl();
      validateDecodedDockerRegistryUrl(registryUrl, connectorDetails.getIdentifier());
      return null;
    } else {
      throw new InvalidArgumentsException(
          format("Invalid auth type: %s for docker ConnectorDisconnectHandler: %s", dockerConfig.getAuth().getAuthType().toString(),
              connectorDetails.getIdentifier()),
          WingsException.USER);
    }
  }

  private ImageCredentials getGCRCredentials(String imageName, ConnectorDetails connectorDetails) {
    // Image name is of format: HOST-NAME/PROJECT-ID/IMAGE. HOST-NAME is registry url.
    String[] imageParts = imageName.split(PATH_SEPARATOR);
    if (imageParts.length == 0 || !imageParts[0].endsWith(BASE_GCR_HOSTNAME)) {
      throw new InvalidArgumentsException(
          format("Invalid image: %s for GCR ConnectorDisconnectHandler. Please provide a fully qualified name in the format "
                  + "HOSTNAME/PROJECT-ID/IMAGE:TAG or HOSTNAME/PROJECT-ID/IMAGE@IMAGE_DIGEST",
              imageName),
          WingsException.USER);
    }

    String registryUrl = imageParts[0];
    GcpConnectorDTO gcpConnectorConfig = (GcpConnectorDTO) connectorDetails.getConnectorConfig();
    if (gcpConnectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.INHERIT_FROM_DELEGATE) {
      return null;
    }
    log.info("Decrypting GCP config for ConnectorDisconnectHandler id:[{}], type:[{}]", connectorDetails.getIdentifier(),
        connectorDetails.getConnectorType());
    GcpManualDetailsDTO credentialConfig = (GcpManualDetailsDTO) secretDecryptor.decrypt(
        (GcpManualDetailsDTO) gcpConnectorConfig.getCredential().getConfig(),
        connectorDetails.getEncryptedDataDetails());
    log.info("Decrypted GCP config for ConnectorDisconnectHandler id:[{}], type:[{}]", connectorDetails.getIdentifier(),
        connectorDetails.getConnectorType());
    if (credentialConfig == null || credentialConfig.getSecretKeyRef() == null
        || credentialConfig.getSecretKeyRef().getDecryptedValue() == null) {
      throw new InvalidArgumentsException(
          format("Credentials should not be empty for GCR ConnectorDisconnectHandler: %s", connectorDetails.getIdentifier()),
          WingsException.USER);
    }

    String password = String.valueOf(credentialConfig.getSecretKeyRef().getDecryptedValue());
    String username = GCR_USERNAME;
    if (isEmpty(password)) {
      throw new InvalidArgumentsException(
          format("Password should not be empty for gcp ConnectorDisconnectHandler %s", connectorDetails.getIdentifier()),
          WingsException.USER);
    }
    return ImageCredentials.builder().registryUrl(registryUrl).userName(username).password(password).build();
  }

  private ImageCredentials getECRCredentials(String imageName, ConnectorDetails connectorDetails) {
    // Image name is of format: <account-ID>.dkr.ecr.<region>.amazonaws.com/image
    if (imageName.startsWith(HTTPS_URL)) {
      imageName = imageName.substring(HTTPS_URL.length());
    }

    String[] imageParts = imageName.split(PATH_SEPARATOR);
    if (imageParts.length == 0 || !imageParts[0].endsWith(BASE_ECR_HOSTNAME)) {
      throw new InvalidArgumentsException(
          format("Invalid image: %s for ECR ConnectorDisconnectHandler", imageName), WingsException.USER);
    }

    String registry = imageParts[0];
    String[] registryParts = registry.split(DOT_SEPARATOR);
    if (registryParts.length != 6) {
      throw new InvalidArgumentsException(
          format("Invalid image: %s for ECR ConnectorDisconnectHandler", imageName), WingsException.USER);
    }
    String account = registryParts[0];
    String region = registryParts[3];
    String registryUrl = format("%s.dkr.ecr.%s.amazonaws.com", account, region);

    AwsConnectorDTO awsConnectorConfig = (AwsConnectorDTO) connectorDetails.getConnectorConfig();
    final AwsConfig awsConfig = mapAwsConfigWithDecryption(awsConnectorConfig.getCredential(),
        awsConnectorConfig.getCredential().getAwsCredentialType(), connectorDetails.getEncryptedDataDetails());
    try {
      String token = awsClient.getAmazonEcrAuthToken(awsConfig, account, region);
      // Token is base64 encoded username:password
      return ImageCredentials.builder().registryUrl(registryUrl).password(token).build();
    } catch (AmazonEC2Exception amazonEC2Exception) {
      handleAmazonServiceException(amazonEC2Exception);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  private String jsonEncodeCredentials(ImageCredentials imageCredentials) {
    if (null == imageCredentials) {
      return null;
    }
    return new JSONObject()
        .put(imageCredentials.getRegistryUrl(),
            new JSONObject()
                .put(USERNAME, imageCredentials.getUserName())
                .put(PASSWORD, imageCredentials.getPassword()))
        .toString();
  }

  private void validateDecodedDockerCredentials(String username, String password, String connectorId) {
    if (isEmpty(username)) {
      throw new InvalidArgumentsException(
          format("Username should not be empty for docker ConnectorDisconnectHandler %s", connectorId), WingsException.USER);
    }

    if (isEmpty(password)) {
      throw new InvalidArgumentsException(
          format("Password should not be empty for docker ConnectorDisconnectHandler %s", connectorId), WingsException.USER);
    }
  }

  private void validateDecodedDockerRegistryUrl(String registryUrl, String connectorId) {
    if (isEmpty(registryUrl)) {
      throw new InvalidArgumentsException(
          format("Registry url should not be empty for docker ConnectorDisconnectHandler %s", connectorId), WingsException.USER);
    }
  }

  private AwsConfig mapAwsConfigWithDecryption(
      AwsCredentialDTO credential, AwsCredentialType awsCredentialType, List<EncryptedDataDetail> encryptionDetails) {
    AwsConfig awsConfig = null;
    switch (awsCredentialType) {
      case INHERIT_FROM_DELEGATE:
        awsConfig = AwsConfig.builder()
                        .isEc2IamCredentials(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      case MANUAL_CREDENTIALS:
        AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) secretDecryptor.decrypt(
            (AwsManualConfigSpecDTO) credential.getConfig(), encryptionDetails);
        final SecretRefData secretKeyRef = config.getSecretKeyRef();
        awsConfig = AwsConfig.builder()
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .awsAccessKeyCredential(AwsAccessKeyCredential.builder()
                                                    .accessKey(getSecretAsStringFromPlainTextOrSecretRef(
                                                        config.getAccessKey(), config.getAccessKeyRef()))
                                                    .secretKey(getDecryptedValueWithNullCheck(secretKeyRef))
                                                    .build())
                        .build();
        break;
      case IRSA:
        awsConfig = AwsConfig.builder()
                        .isIRSA(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      default:
        Switch.unhandled(awsCredentialType);
    }
    return awsConfig;
  }

  private CrossAccountAccess mapCrossAccountAccess(CrossAccountAccessDTO crossAccountAccess) {
    if (crossAccountAccess == null) {
      return null;
    }
    return CrossAccountAccess.builder()
        .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
        .externalId(crossAccountAccess.getExternalId())
        .build();
  }

  @VisibleForTesting
  char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
}
