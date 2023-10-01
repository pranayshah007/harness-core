/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.nexus;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.azure.artifact.NexusAzureArtifactRequestDetails;
import io.harness.delegate.task.pcf.artifact.NexusTasArtifactRequestDetails;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusRequest.NexusRequestBuilder;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
public class NexusMapper {
  @Inject private SecretDecryptionService secretDecryptionService;

  public NexusRequest toNexusRequest(NexusConnectorDTO nexusConnectorDTO) {
    final NexusRequestBuilder nexusRequestBuilder =
        NexusRequest.builder()
            .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
            .version(nexusConnectorDTO.getVersion())
            // Setting as false since we are not taking this in yaml as of now.
            .isCertValidationRequired(false);
    final NexusAuthType authType = nexusConnectorDTO.getAuth().getAuthType();

    if (authType == NexusAuthType.ANONYMOUS) {
      return nexusRequestBuilder.hasCredentials(false).build();
    }
    final NexusUsernamePasswordAuthDTO credentials =
        (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();
    final SecretRefData passwordRef = credentials.getPasswordRef();
    final String username =
        getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef());
    return nexusRequestBuilder.hasCredentials(true)
        .username(username)
        .password(passwordRef.getDecryptedValue())
        .build();
  }

  public NexusRequest toNexusRequest(NexusArtifactDelegateConfig nexusArtifactDelegateConfig) {
    NexusConnectorDTO nexusConnectorDTO =
        (NexusConnectorDTO) nexusArtifactDelegateConfig.getConnectorDTO().getConnectorConfig();
    NexusAuthType authType = nexusConnectorDTO.getAuth().getAuthType();

    if (NexusAuthType.USER_PASSWORD == authType) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();
      secretDecryptionService.decrypt(credentials, nexusArtifactDelegateConfig.getEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          nexusConnectorDTO, nexusArtifactDelegateConfig.getEncryptedDataDetails());

      String username =
          getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef());
      char[] decryptedValue = credentials.getPasswordRef().getDecryptedValue();

      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .username(username)
          .password(decryptedValue)
          .hasCredentials(true)
          .isCertValidationRequired(nexusArtifactDelegateConfig.isCertValidationRequired())
          .artifactRepositoryUrl(nexusArtifactDelegateConfig.getArtifactUrl())
          .build();
    } else if (NexusAuthType.ANONYMOUS == authType) {
      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .hasCredentials(false)
          .isCertValidationRequired(nexusArtifactDelegateConfig.isCertValidationRequired())
          .artifactRepositoryUrl(nexusArtifactDelegateConfig.getArtifactUrl())
          .build();
    }

    throw new InvalidRequestException(format("Unsupported Nexus auth type: %s", authType));
  }

  public NexusRequest toNexusRequest(
      NexusConnectorDTO nexusConnectorDTO, NexusAzureArtifactRequestDetails requestDetails) {
    NexusAuthType authType = nexusConnectorDTO.getAuth().getAuthType();
    if (NexusAuthType.USER_PASSWORD == authType) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();

      String username =
          getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef());
      char[] decryptedValue = credentials.getPasswordRef().getDecryptedValue();

      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .username(username)
          .password(decryptedValue)
          .hasCredentials(true)
          .isCertValidationRequired(requestDetails.isCertValidationRequired())
          .artifactRepositoryUrl(requestDetails.getArtifactUrl())
          .build();
    } else if (NexusAuthType.ANONYMOUS == authType) {
      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .hasCredentials(false)
          .isCertValidationRequired(requestDetails.isCertValidationRequired())
          .artifactRepositoryUrl(requestDetails.getArtifactUrl())
          .build();
    }

    throw new InvalidRequestException(format("Unsupported Nexus auth type: %s", authType));
  }

  public NexusRequest toNexusRequest(
      NexusConnectorDTO nexusConnectorDTO, NexusTasArtifactRequestDetails requestDetails) {
    NexusAuthType authType = nexusConnectorDTO.getAuth().getAuthType();
    if (NexusAuthType.USER_PASSWORD == authType) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();

      String username =
          getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef());
      char[] decryptedValue = credentials.getPasswordRef().getDecryptedValue();

      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .username(username)
          .password(decryptedValue)
          .hasCredentials(true)
          .isCertValidationRequired(requestDetails.isCertValidationRequired())
          .artifactRepositoryUrl(requestDetails.getArtifactUrl())
          .build();
    } else if (NexusAuthType.ANONYMOUS == authType) {
      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .hasCredentials(false)
          .isCertValidationRequired(requestDetails.isCertValidationRequired())
          .artifactRepositoryUrl(requestDetails.getArtifactUrl())
          .build();
    }

    throw new InvalidRequestException(format("Unsupported Nexus auth type: %s", authType));
  }

  public NexusRequest toNexusRequest(
      NexusConnectorDTO nexusConnectorDTO, boolean isCertValidationRequired, String artifactRepositoryUrl) {
    NexusAuthType authType = nexusConnectorDTO.getAuth().getAuthType();
    if (NexusAuthType.USER_PASSWORD == authType) {
      NexusUsernamePasswordAuthDTO credentials =
          (NexusUsernamePasswordAuthDTO) nexusConnectorDTO.getAuth().getCredentials();

      String username =
          getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef());
      char[] decryptedValue = credentials.getPasswordRef().getDecryptedValue();

      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .username(username)
          .password(decryptedValue)
          .hasCredentials(true)
          .isCertValidationRequired(isCertValidationRequired)
          .artifactRepositoryUrl(artifactRepositoryUrl)
          .build();
    } else if (NexusAuthType.ANONYMOUS == authType) {
      return NexusRequest.builder()
          .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
          .version(nexusConnectorDTO.getVersion())
          .hasCredentials(false)
          .isCertValidationRequired(isCertValidationRequired)
          .artifactRepositoryUrl(artifactRepositoryUrl)
          .build();
    }

    throw new InvalidRequestException(format("Unsupported Nexus auth type: %s", authType));
  }
}
