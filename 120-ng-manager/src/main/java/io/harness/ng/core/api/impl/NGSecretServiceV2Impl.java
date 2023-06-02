/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.secrets.SecretPermissions.SECRET_VIEW_PERMISSION;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.SSHTaskParams;
import io.harness.delegate.beans.WinRmTaskParams;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.beans.secrets.WinRmConfigValidationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.api.NGSecretActivityService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.events.SecretCreateEvent;
import io.harness.ng.core.events.SecretDeleteEvent;
import io.harness.ng.core.events.SecretForceDeleteEvent;
import io.harness.ng.core.events.SecretUpdateEvent;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.Secret.SecretKeys;
import io.harness.ng.core.remote.SSHKeyValidationMetadata;
import io.harness.ng.core.remote.SecretValidationMetaData;
import io.harness.ng.core.remote.SecretValidationResultDTO;
import io.harness.ng.core.remote.WinRmCredentialsValidationMetadata;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.SecretRepository;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGSecretServiceV2Impl implements NGSecretServiceV2 {
  private static final Duration VALIDATION_TASK_EXECUTION_TIMEOUT = Duration.ofSeconds(45);
  private static final String CREDENTIALS_VALIDATION_FAILED = "Credentials validation failed. Please try again.";
  private static final String DELEGATES_NOT_AVAILABLE_FOR_CREDENTIALS_VALIDATION =
      "Delegates are not available for performing SSH/WinRm credentials validation.";
  private final SecretRepository secretRepository;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final SshKeySpecDTOHelper sshKeySpecDTOHelper;
  private final WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  private final NGSecretActivityService ngSecretActivityService;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private final AccessControlClient accessControlClient;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final ExceptionManager exceptionManager;

  @Inject
  public NGSecretServiceV2Impl(SecretRepository secretRepository, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      SshKeySpecDTOHelper sshKeySpecDTOHelper, NGSecretActivityService ngSecretActivityService,
      OutboxService outboxService, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      TaskSetupAbstractionHelper taskSetupAbstractionHelper,
      WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper, AccessControlClient accessControlClient,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, ExceptionManager exceptionManager) {
    this.secretRepository = secretRepository;
    this.outboxService = outboxService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.sshKeySpecDTOHelper = sshKeySpecDTOHelper;
    this.ngSecretActivityService = ngSecretActivityService;
    this.transactionTemplate = transactionTemplate;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
    this.winRmCredentialsSpecDTOHelper = winRmCredentialsSpecDTOHelper;
    this.accessControlClient = accessControlClient;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.exceptionManager = exceptionManager;
  }

  @Override
  public boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return !secretRepository.existsByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public Optional<Secret> get(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    return secretRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public Optional<Secret> get(@NotNull IdentifierRef identifierRef) {
    return secretRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
        identifierRef.getIdentifier());
  }

  @Override
  public boolean delete(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      @NotNull String identifier, boolean forceDelete) {
    Optional<Secret> secretV2Optional = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    if (!secretV2Optional.isPresent()) {
      return false;
    }

    Secret secret = secretV2Optional.get();

    return Failsafe.with(transactionRetryPolicy)
        .get(()
                 -> transactionTemplate.execute(status
                     -> deleteInternal(
                         accountIdentifier, orgIdentifier, projectIdentifier, identifier, secret, forceDelete)));
  }

  @VisibleForTesting
  protected boolean deleteInternal(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, Secret secret, boolean forceDelete) {
    secretRepository.delete(secret);
    deleteSecretActivities(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (forceDelete) {
      outboxService.save(new SecretForceDeleteEvent(accountIdentifier, secret.toDTO()));
    } else {
      outboxService.save(new SecretDeleteEvent(accountIdentifier, secret.toDTO()));
    }
    return true;
  }

  private void deleteSecretActivities(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    try {
      String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      ngSecretActivityService.deleteAllActivities(accountIdentifier, fullyQualifiedIdentifier);
    } catch (Exception ex) {
      log.info("Error while deleting secret activity", ex);
    }
  }

  @Override
  public Secret create(String accountIdentifier, @Valid SecretDTOV2 secretDTO, boolean draft) {
    Secret secret = Secret.fromDTO(secretDTO);
    secret.setDraft(draft);
    secret.setAccountIdentifier(accountIdentifier);
    try {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        Secret savedSecret = secretRepository.save(secret);
        createSecretCreationActivity(accountIdentifier, secretDTO);
        outboxService.save(new SecretCreateEvent(accountIdentifier, savedSecret.toDTO()));
        return savedSecret;
      }));
    } catch (DuplicateKeyException duplicateKeyException) {
      throw new DuplicateFieldException(
          "Duplicate identifier, please try again with a new identifier", USER, duplicateKeyException);
    }
  }

  private void createSecretCreationActivity(String accountIdentifier, SecretDTOV2 dto) {
    try {
      ngSecretActivityService.create(accountIdentifier, dto, NGActivityType.ENTITY_CREATION);
    } catch (Exception ex) {
      log.info("Error while creating secret creation activity", ex);
    }
  }

  @Override
  public Secret update(String accountIdentifier, @Valid SecretDTOV2 secretDTO, boolean draft) {
    Optional<Secret> secretOptional = get(
        accountIdentifier, secretDTO.getOrgIdentifier(), secretDTO.getProjectIdentifier(), secretDTO.getIdentifier());
    if (secretOptional.isPresent()) {
      Secret oldSecret = secretOptional.get();
      SecretDTOV2 oldSecretClone = (SecretDTOV2) HObjectMapper.clone(oldSecret.toDTO());

      Secret newSecret = Secret.fromDTO(secretDTO);
      oldSecret.setDescription(newSecret.getDescription());
      oldSecret.setName(newSecret.getName());
      oldSecret.setTags(newSecret.getTags());
      oldSecret.setSecretSpec(newSecret.getSecretSpec());
      oldSecret.setDraft(oldSecret.isDraft() && draft);
      oldSecret.setType(newSecret.getType());
      try {
        return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          secretRepository.save(oldSecret);
          outboxService.save(new SecretUpdateEvent(accountIdentifier, oldSecret.toDTO(), oldSecretClone));
          createSecretUpdateActivity(accountIdentifier, secretDTO);
          return oldSecret;
        }));
      } catch (DuplicateKeyException duplicateKeyException) {
        throw new DuplicateFieldException(
            "Duplicate identifier, please try again with a new identifier", USER, duplicateKeyException);
      }
    }
    throw new InvalidRequestException("No such secret found", USER_SRE);
  }

  private void createSecretUpdateActivity(String accountIdentifier, SecretDTOV2 dto) {
    try {
      ngSecretActivityService.create(accountIdentifier, dto, NGActivityType.ENTITY_UPDATE);
    } catch (Exception ex) {
      log.info("Error while creating secret update activity", ex);
    }
  }

  @Override
  public SecretValidationResultDTO validateSecret(@NotNull String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String identifier, @NotNull SecretValidationMetaData metadata) {
    Optional<Secret> secretV2Optional = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    if (!secretV2Optional.isPresent()) {
      return buildFailedValidationResult();
    }

    Secret secret = secretV2Optional.get();

    switch (secret.getType()) {
      case SSHKey:
        return validateSecretSSHKey(accountIdentifier, orgIdentifier, projectIdentifier, metadata, secret);
      case WinRmCredentials:
        return validateSecretWinRmCredentials(accountIdentifier, orgIdentifier, projectIdentifier, metadata, secret);
      default:
        return buildFailedValidationResult();
    }
  }

  private SecretValidationResultDTO validateSecretSSHKey(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SecretValidationMetaData metadata, Secret secret) {
    SSHKeyValidationMetadata sshKeyValidationMetadata = (SSHKeyValidationMetadata) metadata;
    SSHKeySpecDTO secretSpecDTO = (SSHKeySpecDTO) secret.getSecretSpec().toDTO();
    if (null != secretSpecDTO.getAuth()) {
      secretSpecDTO.getAuth().setUseSshClient(
          ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_SSH_CLIENT));
      secretSpecDTO.getAuth().setUseSshj(
          ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.CDS_SSH_SSHJ));
    }
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();
    List<EncryptedDataDetail> encryptionDetails =
        sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(secretSpecDTO, baseNGAccess);
    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(accountIdentifier)
            .taskType(TaskType.NG_SSH_VALIDATION.name())
            .taskParameters(SSHTaskParams.builder()
                                .host(sshKeyValidationMetadata.getHost())
                                .encryptionDetails(encryptionDetails)
                                .sshKeySpec(secretSpecDTO)
                                .build())
            .taskSetupAbstractions(buildAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .executionTimeout(VALIDATION_TASK_EXECUTION_TIMEOUT);

    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();

    return executeSyncTaskV2(delegateTaskRequest);
  }

  private SecretValidationResultDTO validateSecretWinRmCredentials(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SecretValidationMetaData metadata, Secret secret) {
    WinRmCredentialsValidationMetadata winRmCredentialsValidationMetadata =
        (WinRmCredentialsValidationMetadata) metadata;
    WinRmCredentialsSpecDTO secretSpecDTO = (WinRmCredentialsSpecDTO) secret.getSecretSpec().toDTO();
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    List<EncryptedDataDetail> encryptionDetails =
        winRmCredentialsSpecDTOHelper.getWinRmEncryptionDetails(secretSpecDTO, baseNGAccess);

    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(accountIdentifier)
            .taskType(TaskType.NG_WINRM_VALIDATION.name())
            .taskParameters(WinRmTaskParams.builder()
                                .host(winRmCredentialsValidationMetadata.getHost())
                                .encryptionDetails(encryptionDetails)
                                .spec(secretSpecDTO)
                                .build())
            .taskSetupAbstractions(buildAbstractions(accountIdentifier, orgIdentifier, projectIdentifier))
            .executionTimeout(VALIDATION_TASK_EXECUTION_TIMEOUT);

    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();

    return executeSyncTaskV2(delegateTaskRequest);
  }

  private SecretValidationResultDTO executeSyncTaskV2(DelegateTaskRequest delegateTaskRequest) {
    DelegateResponseData delegateResponseData;
    try {
      delegateResponseData = this.delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);

      if (delegateResponseData instanceof WinRmConfigValidationTaskResponse) {
        WinRmConfigValidationTaskResponse winRmValidationResponse =
            (WinRmConfigValidationTaskResponse) delegateResponseData;
        return buildBaseConfigValidationTaskResponse(
            winRmValidationResponse.isConnectionSuccessful(), winRmValidationResponse.getErrorMessage());
      } else if (delegateResponseData instanceof SSHConfigValidationTaskResponse) {
        SSHConfigValidationTaskResponse sshValidationResponse = (SSHConfigValidationTaskResponse) delegateResponseData;
        return buildBaseConfigValidationTaskResponse(
            sshValidationResponse.isConnectionSuccessful(), sshValidationResponse.getErrorMessage());
      } else if (delegateResponseData instanceof RemoteMethodReturnValueData) {
        RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) delegateResponseData;
        String errorMsg = remoteMethodReturnValueData.getException() != null
                && isNotEmpty(remoteMethodReturnValueData.getException().getMessage())
            ? remoteMethodReturnValueData.getException().getMessage()
            : CREDENTIALS_VALIDATION_FAILED;
        return buildBaseConfigValidationTaskResponse(false, errorMsg);
      } else if (delegateResponseData instanceof ErrorNotifyResponseData) {
        ErrorNotifyResponseData errorResponseData = (ErrorNotifyResponseData) delegateResponseData;
        return buildBaseConfigValidationTaskResponse(false, errorResponseData.getErrorMessage());
      } else {
        return buildBaseConfigValidationTaskResponse(false, CREDENTIALS_VALIDATION_FAILED);
      }
    } catch (DelegateServiceDriverException ex) {
      log.error("Exception while validating SSH/WinRm credentials", ex);
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(DELEGATES_NOT_AVAILABLE_FOR_CREDENTIALS_VALIDATION, WingsException.USER));
    } catch (Exception ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private SecretValidationResultDTO buildBaseConfigValidationTaskResponse(
      boolean connectionSuccessful, String errorMessage) {
    return SecretValidationResultDTO.builder().success(connectionSuccessful).message(errorMessage).build();
  }

  private SecretValidationResultDTO buildFailedValidationResult() {
    return SecretValidationResultDTO.builder().success(false).message("Validation failed.").build();
  }

  @Override
  public Page<Secret> list(Criteria criteria, Pageable pageable) {
    if (!pageable.isPaged() || pageable.getPageSize() == 0) {
      pageable = Pageable.ofSize(50000);
    }
    return secretRepository.findAll(criteria, pageable);
  }

  @Override
  public List<Secret> getPermitted(List<Secret> secrets) {
    Map<SecretResource, List<Secret>> secretsMap = secrets.stream().collect(groupingBy(SecretResource::fromSecret));
    List<PermissionCheckDTO> permissionChecks =
        secrets.stream()
            .map(secret
                -> PermissionCheckDTO.builder()
                       .permission(SECRET_VIEW_PERMISSION)
                       .resourceIdentifier(secret.getIdentifier())
                       .resourceScope(ResourceScope.of(
                           secret.getAccountIdentifier(), secret.getOrgIdentifier(), secret.getProjectIdentifier()))
                       .resourceType(SECRET_RESOURCE_TYPE)
                       .build())
            .collect(Collectors.toList());
    AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccessOrThrow(permissionChecks);

    List<Secret> permittedSecrets = new ArrayList<>();
    for (AccessControlDTO accessControlDTO : accessCheckResponse.getAccessControlList()) {
      if (accessControlDTO.isPermitted()) {
        permittedSecrets.add(secretsMap.get(SecretResource.fromAccessControlDTO(accessControlDTO)).get(0));
      }
    }

    return permittedSecrets;
  }

  @Override
  public Page<Secret> getPaginatedResult(List<Secret> unpagedSecrets, int page, int size) {
    if (unpagedSecrets.isEmpty()) {
      return Page.empty();
    }
    return PageUtils.getPage(unpagedSecrets, page, size);
  }

  @Value
  @Data
  @Builder
  private static class SecretResource {
    String accountIdentifier;
    String orgIdentifier;
    String projectIdentifier;
    String identifier;

    static SecretResource fromSecret(Secret secret) {
      return SecretResource.builder()
          .accountIdentifier(secret.getAccountIdentifier())
          .orgIdentifier(isBlank(secret.getOrgIdentifier()) ? null : secret.getOrgIdentifier())
          .projectIdentifier(isBlank(secret.getProjectIdentifier()) ? null : secret.getProjectIdentifier())
          .identifier(secret.getIdentifier())
          .build();
    }

    static SecretResource fromAccessControlDTO(AccessControlDTO accessControlDTO) {
      return SecretResource.builder()
          .accountIdentifier(accessControlDTO.getResourceScope().getAccountIdentifier())
          .orgIdentifier(isBlank(accessControlDTO.getResourceScope().getOrgIdentifier())
                  ? null
                  : accessControlDTO.getResourceScope().getOrgIdentifier())
          .projectIdentifier(isBlank(accessControlDTO.getResourceScope().getProjectIdentifier())
                  ? null
                  : accessControlDTO.getResourceScope().getProjectIdentifier())
          .identifier(accessControlDTO.getResourceIdentifier())
          .build();
    }
  }

  @Override
  public long count(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = Criteria.where(SecretKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(SecretKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(SecretKeys.projectIdentifier)
                            .is(projectIdentifier);
    return secretRepository.count(criteria);
  }

  @Override
  public Long countSecrets(String accountIdentifier) {
    return secretRepository.countByAccountIdentifier(accountIdentifier);
  }

  public Map<String, String> buildAbstractions(
      String accountIdIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = null;
    // Verify if its a Task from NG
    owner = taskSetupAbstractionHelper.getOwner(accountIdIdentifier, orgIdentifier, projectIdentifier);
    if (isNotEmpty(owner)) {
      abstractions.put(OWNER, owner);
    }
    abstractions.put(NG, "true");
    return abstractions;
  }
}
