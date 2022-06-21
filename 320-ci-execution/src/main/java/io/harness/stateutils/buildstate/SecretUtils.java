/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveSecretRefWithDefaultValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class SecretUtils {
  private final SecretNGManagerClient secretNGManagerClient;
  private final SecretManagerClientService secretManagerClientService;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 6;

  @Inject
  public SecretUtils(@Named("PRIVILEGED") SecretNGManagerClient secretNGManagerClient,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService) {
    this.secretNGManagerClient = secretNGManagerClient;
    this.secretManagerClientService = secretManagerClientService;
  }

  public SecretVariableDetails getSecretVariableDetails(NGAccess ngAccess, SecretNGVariable secretVariable) {
    SecretRefData secretRefData =
        resolveSecretRefWithDefaultValue("Variables", "stage", "stageIdentifier", secretVariable.getValue(), false);
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretRefData == null && secretVariable.getDefaultValue() != null) {
      secretIdentifier = secretVariable.getDefaultValue().getIdentifier();
    }

    if (secretIdentifier == null || secretRefData == null) {
      log.warn("Failed to resolve secret variable " + secretVariable.getName());
      return null;
    }

    log.info("Getting secret variable details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretVariableDTO.Type secretType = getSecretType(getSecret(identifierRef).getType());
    SecretVariableDTO secret =
        SecretVariableDTO.builder().name(secretVariable.getName()).secret(secretRefData).type(secretType).build();
    log.info("Getting secret variable encryption details for secret type:[{}] ref:[{}]", secretType, secretIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(ngAccess, secret);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SecretVariableDetails.builder().encryptedDataDetailList(encryptionDetails).secretVariableDTO(secret).build();
  }

  public SecretVariableDetails getSecretVariableDetailsWithScope(NGAccess ngAccess, SecretNGVariable secretVariable) {
    SecretRefData secretRefData =
        resolveSecretRefWithDefaultValue("Variables", "stage", "stageIdentifier", secretVariable.getValue(), false);
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretRefData == null && secretVariable.getDefaultValue() != null) {
      secretIdentifier = secretVariable.getDefaultValue().getIdentifier();
    }

    if (secretIdentifier == null || secretRefData == null) {
      log.warn("Failed to resolve secret variable " + secretVariable.getName());
      return null;
    }

    log.info("Getting secret variable details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretVariableDTO.Type secretType = getSecretType(getSecret(identifierRef).getType());
    SecretVariableDTO secret =
        SecretVariableDTO.builder()
            .name("HARNESS"
                + "_" + identifierRef.getScope().getYamlRepresentation() + "_" + secretVariable.getName())
            .secret(secretRefData)
            .type(secretType)
            .build();

    log.info("Getting secret variable encryption details for secret type:[{}] ref:[{}]", secretType, secretIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(ngAccess, secret);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SecretVariableDetails.builder().encryptedDataDetailList(encryptionDetails).secretVariableDTO(secret).build();
  }

  private SecretVariableDTO.Type getSecretType(SecretType type) {
    switch (type) {
      case SecretFile:
        return SecretVariableDTO.Type.FILE;
      case SecretText:
        return SecretVariableDTO.Type.TEXT;
      default:
        throw new InvalidArgumentsException(format("Unsupported secret type [%s]", type), WingsException.USER);
    }
  }

  public SSHKeyDetails getSshKey(NGAccess ngAccess, SecretRefData secretRefData) {
    String secretIdentifier = null;
    if (secretRefData != null) {
      secretIdentifier = secretRefData.getIdentifier();
    }

    if (secretIdentifier == null) {
      log.warn("Failed to resolve secret variable, secretIdentifier is null");
      return null;
    }

    log.info("Getting ssh key details for secret ref [{}]", secretIdentifier);
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(secretRefData.toSecretRefStringValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    SecretDTOV2 secretDTOV2 = getSecret(identifierRef);
    if (secretDTOV2.getType() != SecretType.SSHKey) {
      throw new CIStageExecutionUserException(format("Secret id:[%s] type should be SSHKey", secretIdentifier));
    }
    SSHKeySpecDTO spec = (SSHKeySpecDTO) secretDTOV2.getSpec();
    if (spec.getAuth().getAuthScheme() != SSHAuthScheme.SSH) {
      throw new CIStageExecutionUserException(
          format("Secret id:[%s] auth scheme type should be SSH", secretIdentifier));
    }
    SSHConfigDTO sshConfigDTO = (SSHConfigDTO) spec.getAuth().getSpec();
    if (sshConfigDTO.getCredentialType() != SSHCredentialType.KeyReference) {
      throw new CIStageExecutionUserException(
          format("Secret id:[%s] credential type should be KeyReference", secretIdentifier));
    }
    SSHKeyReferenceCredentialDTO credentialSpecDTO = (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();

    log.info(
        "Getting secret encryption details for secret type:[{}] ref:[{}]", secretDTOV2.getType(), secretIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(ngAccess, credentialSpecDTO);
    if (isEmpty(encryptionDetails)) {
      throw new InvalidArgumentsException("Secret encrypted details can't be empty or null", WingsException.USER);
    }

    return SSHKeyDetails.builder().encryptedDataDetails(encryptionDetails).sshKeyReference(credentialSpecDTO).build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity consumer) {
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch secret Encryption details attempt: {}"),
            format("Failed to fetch secret encryption details after retrying {} times"));

    Instant startTime = Instant.now();
    List<EncryptedDataDetail> encryptedDataDetails = Failsafe.with(retryPolicy).get(() -> {
      return secretManagerClientService.getEncryptionDetails(ngAccess, consumer);
    });

    long elapsedTimeInSecs = Duration.between(startTime, Instant.now()).toMillis() / 1000;

    log.info(
        "Fetched secret variable encryption details successfully for secret ref {} in {} seconds accountId {}, projectId {}",
        ngAccess.getIdentifier(), elapsedTimeInSecs, ngAccess.getAccountIdentifier(), ngAccess.getProjectIdentifier());
    return encryptedDataDetails;
  }

  private SecretDTOV2 getSecret(IdentifierRef identifierRef) {
    SecretResponseWrapper secretResponseWrapper;
    try {
      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to fetch secret: [%s] with scope: [%s]; attempt: {}",
                             identifierRef.getIdentifier(), identifierRef.getScope()),
              format("Failed to fetch secret: [%s] with scope: [%s] after retrying {} times",
                  identifierRef.getIdentifier(), identifierRef.getScope()));

      secretResponseWrapper =
          Failsafe.with(retryPolicy)
              .get(()
                       -> SafeHttpCall
                              .execute(secretNGManagerClient.getSecret(identifierRef.getIdentifier(),
                                  identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
                                  identifierRef.getProjectIdentifier()))
                              .getData());

    } catch (Exception e) {
      log.error(format("Unable to get secret information : [%s] with scope: [%s]", identifierRef.getIdentifier(),
                    identifierRef.getScope()),
          e);

      throw new CIStageExecutionException(format("Unable to get secret information : [%s] with scope: [%s]",
          identifierRef.getIdentifier(), identifierRef.getScope()));
    }

    if (secretResponseWrapper == null) {
      throw new CIStageExecutionUserException(format("Secret not found for identifier : [%s] with scope: [%s]",
          identifierRef.getIdentifier(), identifierRef.getScope()));
    }
    return secretResponseWrapper.getSecret();
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
