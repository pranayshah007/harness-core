/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.FILE;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;
import static io.harness.delegate.beans.connector.azureconnector.AzureCredentialType.MANUAL_CREDENTIALS;
import static io.harness.delegate.beans.connector.azureconnector.AzureSecretType.KEY_CERT;
import static io.harness.delegate.beans.connector.azureconnector.AzureSecretType.SECRET_KEY;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.secrets.SecretDecryptor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to create spec for image registry and GIT secrets. Generated spec can be used for creation of secrets on
 * a K8 cluster.
 */

@OwnedBy(CI)
@Slf4j
@Singleton
public class ConnectorEnvVariablesHelper {
  @Inject private SecretDecryptor secretDecryptor;

  public Map<String, SecretParams> getArtifactorySecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDetails.getConnectorConfig();
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = artifactoryConnectorDTO.getAuth();
    ArtifactoryAuthType authType = artifactoryAuthenticationDTO.getAuthType();
    if (authType == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO usernamePasswordAuthDTO =
          (ArtifactoryUsernamePasswordAuthDTO) secretDecryptor.decrypt(
              artifactoryAuthenticationDTO.getCredentials(), connectorDetails.getEncryptedDataDetails());
      String usernameEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_USERNAME);
      String passwordEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_PASSWORD);
      String endpointEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_ENDPOINT);
      String userName = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          usernamePasswordAuthDTO.getUsername(), usernamePasswordAuthDTO.getUsernameRef());

      if (isNotBlank(usernameEnvVarName)) {
        secretData.put(usernameEnvVarName,
            getVariableSecret(usernameEnvVarName + connectorDetails.getIdentifier(), encodeBase64(userName)));
      }

      if (usernamePasswordAuthDTO == null || usernamePasswordAuthDTO.getPasswordRef() == null
          || usernamePasswordAuthDTO.getPasswordRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("Artifactory ConnectorDisconnectHandler password secret does not exist for ConnectorDisconnectHandler with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (isNotBlank(passwordEnvVarName)) {
        secretData.put(passwordEnvVarName,
            getVariableSecret(passwordEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(usernamePasswordAuthDTO.getPasswordRef().getDecryptedValue()))));
      }
      if (isNotBlank(endpointEnvVarName)) {
        secretData.put(endpointEnvVarName,
            getVariableSecret(endpointEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(artifactoryConnectorDTO.getArtifactoryServerUrl())));
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getGcpSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    GcpConnectorDTO gcpConnectorConfig = (GcpConnectorDTO) connectorDetails.getConnectorConfig();
    if (gcpConnectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO credentialConfig = (GcpManualDetailsDTO) secretDecryptor.decrypt(
          (GcpManualDetailsDTO) gcpConnectorConfig.getCredential().getConfig(),
          connectorDetails.getEncryptedDataDetails());

      if (credentialConfig == null || credentialConfig.getSecretKeyRef() == null
          || credentialConfig.getSecretKeyRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("GCP ConnectorDisconnectHandler GCP_KEY secret does not exist for ConnectorDisconnectHandler with identifier  %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (connectorDetails.getEnvToSecretsMap().containsKey(EnvVariableEnum.GCP_KEY)) {
        String gcpKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.GCP_KEY);
        if (isNotBlank(gcpKeyEnvVarName)) {
          secretData.put(gcpKeyEnvVarName,
              getVariableSecret(gcpKeyEnvVarName + connectorDetails.getIdentifier(),
                  encodeBase64(String.valueOf(credentialConfig.getSecretKeyRef().getDecryptedValue()))));
        }
      }
      if (connectorDetails.getEnvToSecretsMap().containsKey(EnvVariableEnum.GCP_KEY_AS_FILE)) {
        String gcpKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.GCP_KEY_AS_FILE);
        if (isNotBlank(gcpKeyEnvVarName)) {
          secretData.put(gcpKeyEnvVarName,
              getFileSecret(gcpKeyEnvVarName + connectorDetails.getIdentifier(),
                  encodeBase64(String.valueOf(credentialConfig.getSecretKeyRef().getDecryptedValue()))));
        }
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getAwsSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    AwsConnectorDTO awsConnectorConfig = (AwsConnectorDTO) connectorDetails.getConnectorConfig();
    if (awsConnectorConfig.getCredential().getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO manualConfig = (AwsManualConfigSpecDTO) secretDecryptor.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorConfig.getCredential().getConfig(),
          connectorDetails.getEncryptedDataDetails());
      String accessKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_ACCESS_KEY);
      String secretKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_SECRET_KEY);

      if (manualConfig == null || manualConfig.getSecretKeyRef() == null
          || manualConfig.getSecretKeyRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("AWS ConnectorDisconnectHandler secret key does not exist for ConnectorDisconnectHandler with identifier  %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (manualConfig == null || (manualConfig.getAccessKey() == null && manualConfig.getAccessKeyRef() == null)) {
        throw new InvalidArgumentsException(
            format("AWS ConnectorDisconnectHandler access key does not exist for ConnectorDisconnectHandler with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      String awsAccessKey = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          manualConfig.getAccessKey(), manualConfig.getAccessKeyRef());

      if (isNotBlank(accessKeyEnvVarName)) {
        secretData.put(accessKeyEnvVarName,
            getVariableSecret(accessKeyEnvVarName + connectorDetails.getIdentifier(), encodeBase64(awsAccessKey)));
      }
      if (isNotBlank(secretKeyEnvVarName)) {
        secretData.put(secretKeyEnvVarName,
            getVariableSecret(secretKeyEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(manualConfig.getSecretKeyRef().getDecryptedValue()))));
      }
    }

    if (awsConnectorConfig.getCredential() != null
        && awsConnectorConfig.getCredential().getCrossAccountAccess() != null) {
      String crossAccountRoleArn = awsConnectorConfig.getCredential().getCrossAccountAccess().getCrossAccountRoleArn();
      String crossAccountArnEnvVarName =
          connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN);
      if (isNotEmpty(crossAccountArnEnvVarName) && isNotEmpty(crossAccountRoleArn)) {
        secretData.put(crossAccountArnEnvVarName,
            getVariableSecret(
                crossAccountArnEnvVarName + connectorDetails.getIdentifier(), encodeBase64(crossAccountRoleArn)));
      }

      String externalId = awsConnectorConfig.getCredential().getCrossAccountAccess().getExternalId();
      String externalIdEnvVarName =
          connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_CROSS_ACCOUNT_EXTERNAL_ID);
      if (isNotEmpty(externalId) && isNotEmpty(externalIdEnvVarName)) {
        secretData.put(externalIdEnvVarName,
            getVariableSecret(externalIdEnvVarName + connectorDetails.getIdentifier(), encodeBase64(externalId)));
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getAzureSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    AzureConnectorDTO connectorConfig = (AzureConnectorDTO) connectorDetails.getConnectorConfig();
    AzureCredentialDTO credentialDTO = connectorConfig.getCredential();
    if (MANUAL_CREDENTIALS == connectorConfig.getCredential().getAzureCredentialType()) {
      AzureManualDetailsDTO config = (AzureManualDetailsDTO) credentialDTO.getConfig();
      String clientIdEnvName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AZURE_APP_ID);
      String tenantIdEnvName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AZURE_TENANT_ID);
      secretData.put(clientIdEnvName,
          getVariableSecret(clientIdEnvName + connectorDetails.getIdentifier(), encodeBase64(config.getClientId())));
      secretData.put(tenantIdEnvName,
          getVariableSecret(tenantIdEnvName + connectorDetails.getIdentifier(), encodeBase64(config.getTenantId())));

      if (config.getAuthDTO().getAzureSecretType() == SECRET_KEY) {
        String clientSecretEnvName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AZURE_APP_SECRET);
        AzureClientSecretKeyDTO decryptedConfig = (AzureClientSecretKeyDTO) secretDecryptor.decrypt(
            config.getAuthDTO().getCredentials(), connectorDetails.getEncryptedDataDetails());
        String clientSecret = String.valueOf(decryptedConfig.getSecretKey().getDecryptedValue());
        secretData.put(clientSecretEnvName,
            getVariableSecret(clientSecretEnvName + connectorDetails.getIdentifier(), encodeBase64(clientSecret)));
      } else if (config.getAuthDTO().getAzureSecretType() == KEY_CERT) {
        String certPathEnvName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AZURE_CERT);
        AzureClientKeyCertDTO decryptedConfig = (AzureClientKeyCertDTO) secretDecryptor.decrypt(
            config.getAuthDTO().getCredentials(), connectorDetails.getEncryptedDataDetails());
        String clientSecret = String.valueOf(decryptedConfig.getClientCertRef().getDecryptedValue());
        secretData.put(certPathEnvName,
            getFileSecret(
                certPathEnvName + connectorDetails.getIdentifier(), encodeBase64(encodeBase64(clientSecret))));
      } else {
        throw new InvalidArgumentsException(
            format("Unsupported type for azure manual credentials %s", config.getAuthDTO().getAzureSecretType()),
            WingsException.USER);
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getDockerSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    DockerConnectorDTO dockerConnectorConfig = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    String registryUrl = dockerConnectorConfig.getDockerRegistryUrl();
    String registryEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_REGISTRY);

    if (dockerConnectorConfig.getAuth().getAuthType() == DockerAuthType.USER_PASSWORD) {
      DockerUserNamePasswordDTO dockerUserNamePasswordDTO = (DockerUserNamePasswordDTO) secretDecryptor.decrypt(
          dockerConnectorConfig.getAuth().getCredentials(), connectorDetails.getEncryptedDataDetails());

      String usernameEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_USERNAME);
      String passwordEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_PASSWORD);
      if (isNotBlank(usernameEnvVarName)) {
        String userName = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
            dockerUserNamePasswordDTO.getUsername(), dockerUserNamePasswordDTO.getUsernameRef());
        secretData.put(usernameEnvVarName,
            getVariableSecret(usernameEnvVarName + connectorDetails.getIdentifier(), encodeBase64(userName)));
      }

      if (dockerUserNamePasswordDTO == null || dockerUserNamePasswordDTO.getPasswordRef() == null
          || dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("Docker ConnectorDisconnectHandler password secret does not exist for ConnectorDisconnectHandler with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (isNotBlank(passwordEnvVarName)) {
        secretData.put(passwordEnvVarName,
            getVariableSecret(passwordEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue()))));
      }
    }
    if (isNotBlank(registryEnvVarName)) {
      secretData.put(registryEnvVarName,
          getVariableSecret(registryEnvVarName + connectorDetails.getIdentifier(), encodeBase64(registryUrl)));
    }
    return secretData;
  }

  private SecretParams getVariableSecret(String key, String encodedSecret) {
    return SecretParams.builder().secretKey(key).value(encodedSecret).type(TEXT).build();
  }
  private SecretParams getFileSecret(String key, String encodedSecret) {
    return SecretParams.builder().secretKey(key).value(encodedSecret).type(FILE).build();
  }
}
