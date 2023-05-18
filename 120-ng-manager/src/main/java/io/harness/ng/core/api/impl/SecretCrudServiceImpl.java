/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_SECRETS;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.secretmanagerclient.SecretType.SSHKey;
import static io.harness.secretmanagerclient.SecretType.SecretFile;
import static io.harness.secretmanagerclient.SecretType.SecretText;
import static io.harness.secretmanagerclient.SecretType.WinRmCredentials;
import static io.harness.secretmanagerclient.ValueType.CustomSecretManagerValues;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;
import static io.harness.secrets.SecretPermissions.SECRET_VIEW_PERMISSION;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.NGResourceFilterConstants;
import io.harness.NgAutoLogContext;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.SortOrder;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.governance.GovernanceMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.secrets.BaseSSHSpecDTO;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialSpecDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.models.Secret.SecretKeys;
import io.harness.ng.core.models.SecretTextSpec;
import io.harness.ng.core.remote.SecretValidationMetaData;
import io.harness.ng.core.remote.SecretValidationResultDTO;
import io.harness.ng.opa.entities.secret.OpaSecretService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.stream.BoundedInputStream;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class SecretCrudServiceImpl implements SecretCrudService {
  private final NGSecretServiceV2 ngSecretService;
  private final FileUploadLimit fileUploadLimit;
  private final SecretEntityReferenceHelper secretEntityReferenceHelper;
  private final Producer eventProducer;
  private final NGEncryptedDataService encryptedDataService;
  private final NGSettingsClient settingsClient;
  private final NGConnectorSecretManagerService ngConnectorSecretManagerService;
  private final AccessControlClient accessControlClient;
  private final OpaSecretService opaSecretService;
  private final NGFeatureFlagHelperService featureFlagHelperService;

  @Inject
  public SecretCrudServiceImpl(SecretEntityReferenceHelper secretEntityReferenceHelper, FileUploadLimit fileUploadLimit,
      NGSecretServiceV2 ngSecretService, @Named(ENTITY_CRUD) Producer eventProducer,
      NGEncryptedDataService encryptedDataService, NGConnectorSecretManagerService ngConnectorSecretManagerService,
      AccessControlClient accessControlClient, OpaSecretService opaSecretService, NGSettingsClient settingsClient,
      NGFeatureFlagHelperService featureFlagHelperService) {
    this.fileUploadLimit = fileUploadLimit;
    this.secretEntityReferenceHelper = secretEntityReferenceHelper;
    this.ngSecretService = ngSecretService;
    this.eventProducer = eventProducer;
    this.encryptedDataService = encryptedDataService;
    this.ngConnectorSecretManagerService = ngConnectorSecretManagerService;
    this.accessControlClient = accessControlClient;
    this.opaSecretService = opaSecretService;
    this.settingsClient = settingsClient;
    this.featureFlagHelperService = featureFlagHelperService;
  }

  private void checkEqualityOrThrow(Object str1, Object str2) {
    if (!Objects.equals(str1, str2)) {
      throw new InvalidRequestException(
          "Cannot change organization, project, identifier, type or secret manager of a secret after creation.",
          INVALID_REQUEST, USER);
    }
  }

  private void validateUpdateRequest(String orgIdentifier, String projectIdentifier, String identifier,
      SecretType secretType, String secretManagerIdentifier, SecretDTOV2 updateDTO) {
    checkEqualityOrThrow(orgIdentifier, updateDTO.getOrgIdentifier());
    checkEqualityOrThrow(projectIdentifier, updateDTO.getProjectIdentifier());
    checkEqualityOrThrow(identifier, updateDTO.getIdentifier());
    checkEqualityOrThrow(secretType, updateDTO.getType());
    checkEqualityOrThrow(secretManagerIdentifier, getSecretManagerIdentifier(updateDTO));
  }

  private SecretResponseWrapper getResponseWrapper(@NotNull Secret secret) {
    if (secret.getType() == SecretText) {
      SecretTextSpec secretSpec = (SecretTextSpec) secret.getSecretSpec();
      if (ValueType.Reference.equals(secretSpec.getValueType())
          || CustomSecretManagerValues.equals(secretSpec.getValueType())) {
        NGEncryptedData encryptedData = encryptedDataService.get(secret.getAccountIdentifier(),
            secret.getOrgIdentifier(), secret.getProjectIdentifier(), secret.getIdentifier());
        secretSpec.setValue(encryptedData.getPath());
      }
    }
    SecretDTOV2 secretDTO = secret.toDTO();
    if (null != secretDTO && secretDTO.getSpec() instanceof SSHKeySpecDTO) {
      SSHKeySpecDTO sshKeySpecDTO = (SSHKeySpecDTO) secretDTO.getSpec();
      sshKeySpecDTO.getAuth().setUseSshClient(
          featureFlagHelperService.isEnabled(secret.getAccountIdentifier(), FeatureName.CDS_SSH_CLIENT));
      sshKeySpecDTO.getAuth().setUseSshj(
          featureFlagHelperService.isEnabled(secret.getAccountIdentifier(), FeatureName.CDS_SSH_SSHJ));
    }
    return SecretResponseWrapper.builder()
        .secret(secretDTO)
        .updatedAt(secret.getLastModifiedAt())
        .createdAt(secret.getCreatedAt())
        .draft(secret.isDraft())
        .build();
  }

  public SecretDTOV2 getMaskedDTOForOpa(SecretDTOV2 dto) {
    SecretDTOV2 secretDTOV2ForOpa;
    try {
      ObjectMapper mapper = new ObjectMapper();
      String jsonSource = mapper.writeValueAsString(dto);
      secretDTOV2ForOpa = mapper.readValue(jsonSource, SecretDTOV2.class);
    } catch (JsonProcessingException je) {
      throw new InvalidRequestException("Cannot parse secret json with error", je);
    }
    if (secretDTOV2ForOpa.getSpec() instanceof SecretTextSpecDTO) {
      SecretTextSpecDTO secretTextSpecDTOForOpa = (SecretTextSpecDTO) secretDTOV2ForOpa.getSpec();
      secretTextSpecDTOForOpa.setValue(null);
      secretDTOV2ForOpa.setSpec(secretTextSpecDTOForOpa);
    }
    if (secretDTOV2ForOpa.getSpec() instanceof SSHKeySpecDTO) {
      SSHKeySpecDTO sshKeySpecDTOForOpa = (SSHKeySpecDTO) secretDTOV2ForOpa.getSpec();
      SSHAuthDTO sshAuthDTO = sshKeySpecDTOForOpa.getAuth();
      BaseSSHSpecDTO baseSSHSpecDTOForOpa = sshAuthDTO.getSpec();
      if (baseSSHSpecDTOForOpa instanceof SSHConfigDTO) {
        SSHCredentialSpecDTO sshCredentialSpecDTOForOpa = ((SSHConfigDTO) baseSSHSpecDTOForOpa).getSpec();
        if (sshCredentialSpecDTOForOpa instanceof SSHKeyPathCredentialDTO) {
          ((SSHKeyPathCredentialDTO) sshCredentialSpecDTOForOpa).setEncryptedPassphrase(null);
        }
        if (sshCredentialSpecDTOForOpa instanceof SSHKeyReferenceCredentialDTO) {
          ((SSHKeyReferenceCredentialDTO) sshCredentialSpecDTOForOpa).setKey(null);
          ((SSHKeyReferenceCredentialDTO) sshCredentialSpecDTOForOpa).setEncryptedPassphrase(null);
        }
        if (sshCredentialSpecDTOForOpa instanceof SSHPasswordCredentialDTO) {
          ((SSHPasswordCredentialDTO) sshCredentialSpecDTOForOpa).setPassword(null);
        }
        ((SSHConfigDTO) baseSSHSpecDTOForOpa).setSpec(sshCredentialSpecDTOForOpa);
      }
      sshAuthDTO.setSpec(baseSSHSpecDTOForOpa);
      sshKeySpecDTOForOpa.setAuth(sshAuthDTO);
      secretDTOV2ForOpa.setSpec(sshKeySpecDTOForOpa);
    }
    return secretDTOV2ForOpa;
  }

  @Override
  public Boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return ngSecretService.validateTheIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  @FeatureRestrictionCheck(MULTIPLE_SECRETS)
  public SecretResponseWrapper create(@AccountIdentifier String accountIdentifier, SecretDTOV2 dto) {
    if (SecretText.equals(dto.getType()) && isEmpty(((SecretTextSpecDTO) dto.getSpec()).getValue())) {
      if ((((SecretTextSpecDTO) dto.getSpec()).getValueType()).equals(CustomSecretManagerValues)) {
        log.info(format("Secret [%s] does not have any path for custom secret manager: [%s]", dto.getIdentifier(),
            ((SecretTextSpecDTO) dto.getSpec()).getSecretManagerIdentifier()));
      } else {
        throw new InvalidRequestException("value cannot be empty for a secret text.");
      }
    }

    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    boolean isHarnessManaged = checkIfSecretManagerUsedIsHarnessManaged(accountIdentifier, dto);
    Boolean isBuiltInSMDisabled = false;

    if (isNgSettingsFFEnabled(accountIdentifier)) {
      isBuiltInSMDisabled = parseBoolean(
          NGRestUtils
              .getResponse(settingsClient.getSetting(
                  SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
              .getValue());
    }

    if (isBuiltInSMDisabled && isHarnessManaged) {
      throw new InvalidRequestException(
          "Built-in Harness Secret Manager cannot be used to create Secret as it has been disabled.");
    }

    switch (dto.getType()) {
      case SecretText:
        NGEncryptedData encryptedData = encryptedDataService.createSecretText(accountIdentifier, dto);
        if (Optional.ofNullable(encryptedData).isPresent()) {
          secretResponseWrapper = createSecretInternal(accountIdentifier, dto, false);
          secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
          return secretResponseWrapper;
        }
        break;
      case SSHKey:
      case WinRmCredentials:
        secretResponseWrapper = createSecretInternal(accountIdentifier, dto, false);
        secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
        return secretResponseWrapper;
      default:
        throw new IllegalArgumentException("Invalid secret type provided: " + dto.getType());
    }

    throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to create secret remotely.", USER);
  }

  @VisibleForTesting
  public boolean checkIfSecretManagerUsedIsHarnessManaged(String accountIdentifier, SecretDTOV2 dto) {
    /**
     * SSH and WinRm are special kind of secrets and are not associated to any secret manager, therefore return false in
     * such a case.
     */
    if (dto.getType() == SSHKey || dto.getType() == WinRmCredentials) {
      return false;
    }

    final String secretManagerIdentifier = getSecretManagerIdentifier(dto);
    /**
     * Using scope identifiers of secret because as of now Secrets can be created using SM at same scope. This should
     * also change when across scope SM can be used for secret creation. *
     */
    final SecretManagerConfigDTO secretManagerConfig = ngConnectorSecretManagerService.getUsingIdentifier(
        accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), secretManagerIdentifier, false);

    final Boolean isHarnessManaged = secretManagerConfig.isHarnessManaged();
    return Boolean.TRUE.equals(isHarnessManaged);
  }

  private boolean isOpaPoliciesSatisfied(
      String accountIdentifier, SecretDTOV2 dto, SecretResponseWrapper secretResponseWrapper) {
    GovernanceMetadata governanceMetadata =
        opaSecretService.evaluatePoliciesWithEntity(accountIdentifier, dto, dto.getOrgIdentifier(),
            dto.getProjectIdentifier(), OpaConstants.OPA_EVALUATION_ACTION_CONNECTOR_SAVE, dto.getIdentifier());
    secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
    return governanceMetadata == null || !OpaConstants.OPA_STATUS_ERROR.equals(governanceMetadata.getStatus());
  }

  private SecretResponseWrapper createSecretInternal(String accountIdentifier, SecretDTOV2 dto, boolean draft) {
    Secret secret = ngSecretService.create(accountIdentifier, dto, draft);
    secretEntityReferenceHelper.createSetupUsageForSecretManager(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), dto.getIdentifier(), dto.getName(), getSecretManagerIdentifier(dto));
    secretEntityReferenceHelper.createSetupUsageForSecret(accountIdentifier, dto);
    return getResponseWrapper(secret);
  }

  @Override
  @FeatureRestrictionCheck(MULTIPLE_SECRETS)
  public SecretResponseWrapper createViaYaml(@AccountIdentifier @NotNull String accountIdentifier, SecretDTOV2 dto) {
    Optional<String> message = dto.getSpec().getErrorMessageForInvalidYaml();
    if (message.isPresent()) {
      throw new InvalidRequestException(message.get(), USER);
    }

    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    NGEncryptedData encryptedData;

    switch (dto.getType()) {
      case SecretText:
        encryptedData = encryptedDataService.createSecretText(accountIdentifier, dto);
        if (Optional.ofNullable(encryptedData).isPresent()) {
          secretResponseWrapper = createSecretInternal(accountIdentifier, dto, true);
          secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
          return secretResponseWrapper;
        }
        break;
      case SecretFile:
        encryptedData = encryptedDataService.createSecretFile(accountIdentifier, dto, null);
        if (Optional.ofNullable(encryptedData).isPresent()) {
          secretResponseWrapper = createSecretInternal(accountIdentifier, dto, true);
          secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
          return secretResponseWrapper;
        }
        break;
      case SSHKey:
      case WinRmCredentials:
        secretResponseWrapper = createSecretInternal(accountIdentifier, dto, true);
        secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
        return secretResponseWrapper;
      default:
        throw new IllegalArgumentException("Invalid secret type provided: " + dto.getType());
    }

    throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to create secret remotely.", USER);
  }

  @Override
  public Long countSecrets(String accountIdentifier) {
    return ngSecretService.countSecrets(accountIdentifier);
  }

  @Override
  public Optional<SecretResponseWrapper> get(
      @NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    Optional<Secret> secretV2Optional =
        ngSecretService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return secretV2Optional.map(this::getResponseWrapper);
  }

  @Override
  public Page<SecretResponseWrapper> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<String> identifiers, List<SecretType> secretTypes, boolean includeSecretsFromEverySubScope,
      String searchTerm, ConnectorCategory sourceCategory, boolean includeAllSecretsAccessibleAtScope,
      PageRequest pageRequest) {
    Criteria criteria = Criteria.where(SecretKeys.accountIdentifier).is(accountIdentifier);
    addCriteriaForRequestedScopes(criteria, orgIdentifier, projectIdentifier, includeAllSecretsAccessibleAtScope,
        includeSecretsFromEverySubScope);

    if (isNotEmpty(secretTypes)) {
      criteria = criteria.and(SecretKeys.type).in(secretTypes);
    }

    criteria.and(SecretKeys.owner).is(null);
    if (!StringUtils.isEmpty(searchTerm)) {
      criteria = criteria.orOperator(
          Criteria.where(SecretKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(SecretKeys.identifier)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(SecretKeys.tags + "." + NGTagKeys.key)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(SecretKeys.tags + "." + NGTagKeys.value)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    if (Objects.nonNull(identifiers) && !identifiers.isEmpty()) {
      criteria.and(SecretKeys.identifier).in(identifiers);
    }

    if (accessControlClient.hasAccess(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(SECRET_RESOURCE_TYPE, null), SECRET_VIEW_PERMISSION)) {
      if (isEmpty(pageRequest.getSortOrders())) {
        SortOrder order =
            SortOrder.Builder.aSortOrder().withField(SecretKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
        pageRequest.setSortOrders(ImmutableList.of(order));
      }
      return ngSecretService.list(criteria, getPageRequest(pageRequest)).map(this::getResponseWrapper);
    } else {
      List<Secret> allMatchingSecrets =
          ngSecretService
              .list(criteria,
                  getPageRequest(PageRequest.builder()
                                     .pageIndex(0)
                                     .pageSize(50000) // keeping the default max supported value
                                     .sortOrders(pageRequest.getSortOrders())
                                     .build()))
              .getContent();
      allMatchingSecrets = ngSecretService.getPermitted(allMatchingSecrets);
      return ngSecretService
          .getPaginatedResult(allMatchingSecrets, pageRequest.getPageIndex(), pageRequest.getPageSize())
          .map(this::getResponseWrapper);
    }
  }

  @VisibleForTesting
  protected void addCriteriaForRequestedScopes(Criteria criteria, String orgIdentifier, String projectIdentifier,
      boolean includeAllSecretsAccessibleAtScope, boolean includeSecretsFromEverySubScope) {
    if (!includeAllSecretsAccessibleAtScope && !includeSecretsFromEverySubScope) {
      criteria.and(SecretKeys.orgIdentifier).is(orgIdentifier).and(SecretKeys.projectIdentifier).is(projectIdentifier);
    } else {
      Criteria superScopeCriteriaOrSubScopeCriteria = new Criteria();
      Criteria subScopeCriteria = new Criteria();
      Criteria superScopeCriteria = new Criteria();
      addCriteriaForIncludeAllSecretsAccessibleAtScope(superScopeCriteria, orgIdentifier, projectIdentifier);
      addCriteriaForIncludeSecretsFromSubScope(subScopeCriteria, orgIdentifier, projectIdentifier);
      if (includeAllSecretsAccessibleAtScope && includeSecretsFromEverySubScope) {
        superScopeCriteriaOrSubScopeCriteria.orOperator(superScopeCriteria, subScopeCriteria);
        criteria.andOperator(superScopeCriteriaOrSubScopeCriteria);
      } else if (includeSecretsFromEverySubScope) {
        criteria.andOperator(subScopeCriteria);
      } else {
        criteria.andOperator(superScopeCriteria);
      }
    }
  }

  private void addCriteriaForIncludeSecretsFromSubScope(
      Criteria subScopeCriteria, String orgIdentifier, String projectIdentifier) {
    if (isNotBlank(orgIdentifier)) {
      subScopeCriteria.and(SecretKeys.orgIdentifier).is(orgIdentifier);
      if (isNotBlank(projectIdentifier)) {
        subScopeCriteria.and(SecretKeys.projectIdentifier).is(projectIdentifier);
      }
    }
  }

  private void addCriteriaForIncludeAllSecretsAccessibleAtScope(
      Criteria criteria, String orgIdentifier, String projectIdentifier) {
    Criteria accountCriteria =
        Criteria.where(SecretKeys.orgIdentifier).is(null).and(SecretKeys.projectIdentifier).is(null);
    Criteria orgCriteria =
        Criteria.where(SecretKeys.orgIdentifier).is(orgIdentifier).and(SecretKeys.projectIdentifier).is(null);
    Criteria projectCriteria = Criteria.where(SecretKeys.orgIdentifier)
                                   .is(orgIdentifier)
                                   .and(SecretKeys.projectIdentifier)
                                   .is(projectIdentifier);

    if (isNotBlank(projectIdentifier)) {
      criteria.orOperator(projectCriteria, orgCriteria, accountCriteria);
    } else if (isNotBlank(orgIdentifier)) {
      criteria.orOperator(orgCriteria, accountCriteria);
    } else {
      criteria.orOperator(accountCriteria);
    }
  }
  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier,
      boolean forceDelete) {
    try (AutoLogContext ignore1 =
             new NgAutoLogContext(projectIdentifier, orgIdentifier, accountIdentifier, OVERRIDE_ERROR)) {
      if (forceDelete && !isForceDeleteEnabled(accountIdentifier)) {
        throw new InvalidRequestException(
            format(
                "Parameter forcedDelete cannot be true. Force deletion of secret is not enabled for this account [%s]",
                accountIdentifier),
            USER);
      }

      Optional<SecretResponseWrapper> optionalSecret =
          get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (optionalSecret.isPresent()) {
        if (!forceDelete) {
          secretEntityReferenceHelper.validateSecretIsNotUsedByOthers(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier);
        }
      } else {
        log.error(format("Secret with identifier [%s] could not be deleted as it does not exist", identifier));
        throw new EntityNotFoundException(
            format("Secret with identifier [%s] does not exist in the specified scope", identifier));
      }

      NGEncryptedData encryptedData =
          encryptedDataService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

      boolean remoteDeletionSuccess = true;
      boolean localDeletionSuccess = false;
      if (encryptedData != null) {
        remoteDeletionSuccess =
            encryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, forceDelete);
      }

      if (remoteDeletionSuccess) {
        localDeletionSuccess =
            ngSecretService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, forceDelete);
      }
      if (remoteDeletionSuccess && localDeletionSuccess) {
        secretEntityReferenceHelper.deleteExistingSetupUsage(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
        publishEvent(accountIdentifier, orgIdentifier, projectIdentifier, identifier,
            EventsFrameworkMetadataConstants.DELETE_ACTION);
        return true;
      }
      if (!remoteDeletionSuccess) {
        throw new InvalidRequestException(format("Unable to delete secret: [%s] remotely.", identifier), USER);
      } else {
        throw new InvalidRequestException(
            format("Unable to delete secret: [%s] locally, data might be inconsistent", identifier), USER);
      }
    }
  }

  public void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> secretIdentifiersList) {
    for (String identifier : secretIdentifiersList) {
      Optional<SecretResponseWrapper> optionalSecret =
          get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (optionalSecret.isPresent()) {
        boolean deletionSuccess =
            ngSecretService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
        if (deletionSuccess) {
          secretEntityReferenceHelper.deleteExistingSetupUsage(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier);
          encryptedDataService.hardDelete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
          publishEvent(accountIdentifier, orgIdentifier, projectIdentifier, identifier,
              EventsFrameworkMetadataConstants.DELETE_ACTION);
        } else {
          log.error("Unable to delete secret {} locally, data might be inconsistent", identifier);
        }
      }
    }
  }

  private void publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String action) {
    try {
      EntityChangeDTO.Builder secretEntityChangeDTOBuilder =
          EntityChangeDTO.newBuilder()
              .setAccountIdentifier(StringValue.of(accountIdentifier))
              .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        secretEntityChangeDTOBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        secretEntityChangeDTOBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      EventsFrameworkMetadataConstants.SECRET_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(secretEntityChangeDTOBuilder.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework secret Identifier: {}", identifier, e);
    }
  }

  private String getSecretManagerIdentifier(SecretDTOV2 secret) {
    switch (secret.getType()) {
      case SecretText:
        return ((SecretTextSpecDTO) secret.getSpec()).getSecretManagerIdentifier();
      case SecretFile:
        return ((SecretFileSpecDTO) secret.getSpec()).getSecretManagerIdentifier();
      default:
        return HARNESS_SECRET_MANAGER_IDENTIFIER;
    }
  }

  private SecretResponseWrapper processAndGetSecret(boolean remoteUpdateSuccess, Secret updatedSecret) {
    if (remoteUpdateSuccess && updatedSecret != null) {
      publishEvent(updatedSecret, EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return getResponseWrapper(updatedSecret);
    }
    if (!remoteUpdateSuccess) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to update secret remotely", USER);
    } else {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Unable to update secret locally, data might be inconsistent", USER);
    }
  }

  @Override
  public SecretResponseWrapper update(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, SecretDTOV2 dto) {
    validateUpdateRequestAndGetSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto);
    boolean remoteUpdateSuccess = true;

    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    if (SecretText.equals(dto.getType())) {
      NGEncryptedData encryptedData = encryptedDataService.updateSecretText(accountIdentifier, dto);
      if (!Optional.ofNullable(encryptedData).isPresent()) {
        remoteUpdateSuccess = false;
      }
    }
    Secret updatedSecret = null;
    if (remoteUpdateSuccess) {
      secretEntityReferenceHelper.createSetupUsageForSecret(accountIdentifier, dto);
      updatedSecret = ngSecretService.update(accountIdentifier, dto, false);
    }
    secretResponseWrapper = processAndGetSecret(remoteUpdateSuccess, updatedSecret);
    secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
    return secretResponseWrapper;
  }

  @Override
  public SecretResponseWrapper updateViaYaml(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, SecretDTOV2 dto) {
    if (dto.getSpec().getErrorMessageForInvalidYaml().isPresent()) {
      throw new InvalidRequestException(dto.getSpec().getErrorMessageForInvalidYaml().get(), USER);
    }

    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    validateUpdateRequestAndGetSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto);

    boolean remoteUpdateSuccess = true;
    if (SecretText.equals(dto.getType())) {
      NGEncryptedData encryptedData = encryptedDataService.updateSecretText(accountIdentifier, dto);
      if (!Optional.ofNullable(encryptedData).isPresent()) {
        remoteUpdateSuccess = false;
      }
    } else if (SecretFile.equals(dto.getType())) {
      NGEncryptedData encryptedData = encryptedDataService.updateSecretFile(accountIdentifier, dto, null);
      if (!Optional.ofNullable(encryptedData).isPresent()) {
        remoteUpdateSuccess = false;
      }
    }
    Secret updatedSecret = null;
    if (remoteUpdateSuccess) {
      secretEntityReferenceHelper.createSetupUsageForSecret(accountIdentifier, dto);
      updatedSecret = ngSecretService.update(accountIdentifier, dto, true);
    }
    secretResponseWrapper = processAndGetSecret(remoteUpdateSuccess, updatedSecret);
    secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
    return secretResponseWrapper;
  }

  private void publishEvent(Secret secret, String action) {
    try {
      EntityChangeDTO.Builder secretEntityChangeDTOBuilder =
          EntityChangeDTO.newBuilder()
              .setAccountIdentifier(StringValue.of(secret.getAccountIdentifier()))
              .setIdentifier(StringValue.of(secret.getIdentifier()));
      if (isNotBlank(secret.getOrgIdentifier())) {
        secretEntityChangeDTOBuilder.setOrgIdentifier(StringValue.of(secret.getOrgIdentifier()));
      }
      if (isNotBlank(secret.getProjectIdentifier())) {
        secretEntityChangeDTOBuilder.setProjectIdentifier(StringValue.of(secret.getProjectIdentifier()));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", secret.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.SECRET_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(secretEntityChangeDTOBuilder.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework secret Identifier: " + secret.getIdentifier(), e);
    }
  }

  @SneakyThrows
  @Override
  public SecretResponseWrapper createFile(
      @NotNull String accountIdentifier, @NotNull SecretDTOV2 dto, @NotNull InputStream inputStream) {
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    SecretFileSpecDTO specDTO = (SecretFileSpecDTO) dto.getSpec();
    NGEncryptedData encryptedData = encryptedDataService.createSecretFile(
        accountIdentifier, dto, new BoundedInputStream(inputStream, fileUploadLimit.getEncryptedFileLimit()));

    if (Optional.ofNullable(encryptedData).isPresent()) {
      secretEntityReferenceHelper.createSetupUsageForSecretManager(accountIdentifier, dto.getOrgIdentifier(),
          dto.getProjectIdentifier(), dto.getIdentifier(), dto.getName(), specDTO.getSecretManagerIdentifier());
      Secret secret = ngSecretService.create(accountIdentifier, dto, false);
      secretResponseWrapper = getResponseWrapper(secret);
      secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
      return secretResponseWrapper;
    }
    throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to create secret file remotely", USER);
  }

  @SneakyThrows
  @Override
  public SecretResponseWrapper createFile(@NotNull String accountIdentifier, @NotNull SecretDTOV2 dto,
      @NotNull String encryptionKey, @NotNull String encryptedValue) {
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    SecretFileSpecDTO specDTO = (SecretFileSpecDTO) dto.getSpec();
    NGEncryptedData encryptedData =
        encryptedDataService.createSecretFile(accountIdentifier, dto, encryptionKey, encryptedValue);

    if (Optional.ofNullable(encryptedData).isPresent()) {
      secretEntityReferenceHelper.createSetupUsageForSecretManager(accountIdentifier, dto.getOrgIdentifier(),
          dto.getProjectIdentifier(), dto.getIdentifier(), dto.getName(), specDTO.getSecretManagerIdentifier());
      Secret secret = ngSecretService.create(accountIdentifier, dto, false);
      secretResponseWrapper = getResponseWrapper(secret);
      secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
      return secretResponseWrapper;
    }
    throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to create secret file remotely", USER);
  }

  private SecretDTOV2 validateUpdateRequestAndGetSecret(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, SecretDTOV2 updateDTO) {
    Optional<SecretResponseWrapper> secretOptional =
        get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (!secretOptional.isPresent()) {
      throw new InvalidRequestException("No such secret found, please check identifier/scope and try again.");
    }

    SecretDTOV2 existingSecret = secretOptional.get().getSecret();
    validateUpdateRequest(existingSecret.getOrgIdentifier(), existingSecret.getProjectIdentifier(),
        existingSecret.getIdentifier(), existingSecret.getType(), getSecretManagerIdentifier(existingSecret),
        updateDTO);
    return existingSecret;
  }

  @SneakyThrows
  @Override
  public SecretResponseWrapper updateFile(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, @Valid SecretDTOV2 dto, @NotNull InputStream inputStream) {
    SecretResponseWrapper secretResponseWrapper = SecretResponseWrapper.builder().build();
    if (!isOpaPoliciesSatisfied(accountIdentifier, getMaskedDTOForOpa(dto), secretResponseWrapper)) {
      return secretResponseWrapper;
    }
    GovernanceMetadata governanceMetadata = secretResponseWrapper.getGovernanceMetadata();

    validateUpdateRequestAndGetSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier, dto);
    boolean success =
        Optional
            .ofNullable(encryptedDataService.updateSecretFile(accountIdentifier, dto,
                (inputStream == null) ? null
                                      : new BoundedInputStream(inputStream, fileUploadLimit.getEncryptedFileLimit())))
            .isPresent();

    if (success) {
      Secret updatedSecret = ngSecretService.update(accountIdentifier, dto, false);
      publishEvent(updatedSecret, EventsFrameworkMetadataConstants.UPDATE_ACTION);
      secretResponseWrapper = getResponseWrapper(updatedSecret);
      secretResponseWrapper.setGovernanceMetadata(governanceMetadata);
      return secretResponseWrapper;
    }
    throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to update secret file remotely", USER);
  }

  @Override
  public SecretValidationResultDTO validateSecret(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, @Valid SecretValidationMetaData metadata) {
    return ngSecretService.validateSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier, metadata);
  }

  @Override
  public void validateSshWinRmSecretRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretDTOV2 secretDTO) {
    SecretRefData secretRef = null;

    if (secretDTO.getSpec() instanceof SSHKeySpecDTO) {
      SSHKeySpecDTO sshKeySpecDTO = (SSHKeySpecDTO) secretDTO.getSpec();
      if (sshKeySpecDTO.getAuth().getSpec() instanceof SSHConfigDTO) {
        SSHConfigDTO sshConfigDTO = (SSHConfigDTO) sshKeySpecDTO.getAuth().getSpec();
        if (sshConfigDTO.getSpec() instanceof SSHKeyReferenceCredentialDTO) {
          SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO =
              (SSHKeyReferenceCredentialDTO) sshConfigDTO.getSpec();
          secretRef = sshKeyReferenceCredentialDTO.getKey();
        } else if (sshConfigDTO.getSpec() instanceof SSHPasswordCredentialDTO) {
          SSHPasswordCredentialDTO sshPasswordCredentialDTO = (SSHPasswordCredentialDTO) sshConfigDTO.getSpec();
          secretRef = sshPasswordCredentialDTO.getPassword();
        }
      } else if (sshKeySpecDTO.getAuth().getSpec() instanceof KerberosConfigDTO) {
        KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) sshKeySpecDTO.getAuth().getSpec();
        if (kerberosConfigDTO.getSpec() instanceof TGTPasswordSpecDTO) {
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
          secretRef = tgtPasswordSpecDTO.getPassword();
        }
      }
    } else if (secretDTO.getSpec() instanceof WinRmCredentialsSpecDTO) {
      WinRmCredentialsSpecDTO winRmCredentialsSpecDTO = (WinRmCredentialsSpecDTO) secretDTO.getSpec();
      if (winRmCredentialsSpecDTO.getAuth().getSpec() instanceof KerberosWinRmConfigDTO) {
        KerberosWinRmConfigDTO kerberosConfigDTO = (KerberosWinRmConfigDTO) winRmCredentialsSpecDTO.getAuth().getSpec();
        if (kerberosConfigDTO.getSpec() instanceof TGTPasswordSpecDTO) {
          TGTPasswordSpecDTO tgtPasswordSpecDTO = (TGTPasswordSpecDTO) kerberosConfigDTO.getSpec();
          secretRef = tgtPasswordSpecDTO.getPassword();
        }
      } else if (winRmCredentialsSpecDTO.getAuth().getSpec() instanceof NTLMConfigDTO) {
        NTLMConfigDTO ntlmConfigDTO = (NTLMConfigDTO) winRmCredentialsSpecDTO.getAuth().getSpec();
        secretRef = ntlmConfigDTO.getPassword();
      }
    }

    // in case of Kerberos no TGT, SSH key with optional password
    if (secretRef == null) {
      return;
    }

    BaseNGAccess secretRefScopeInfo =
        SecretRefHelper.getScopeIdentifierForSecretRef(secretRef, accountIdentifier, orgIdentifier, projectIdentifier);

    Optional<Secret> secretOptional = ngSecretService.get(accountIdentifier, secretRefScopeInfo.getOrgIdentifier(),
        secretRefScopeInfo.getProjectIdentifier(), secretRef.getIdentifier());

    if (!secretOptional.isPresent()) {
      throw new EntityNotFoundException(
          format("No such secret found [%s], please check identifier/scope and try again.", secretRef.getIdentifier()));
    }
  }

  @Override
  public void validateSecretDtoSpec(SecretDTOV2 secretDTO) {
    if (secretDTO != null) {
      if (secretDTO.getSpec() instanceof WinRmCredentialsSpecDTO) {
        WinRmCredentialsSpecDTO winRmCredentialsSpecDTO = (WinRmCredentialsSpecDTO) secretDTO.getSpec();
        validateCommandParameters(winRmCredentialsSpecDTO.getParameters());
      }
    }
  }

  private void validateCommandParameters(List<WinRmCommandParameter> parameters) {
    if (!isEmpty(parameters)) {
      validateParameterNameUnique(parameters);
    }
  }

  private void validateParameterNameUnique(List<WinRmCommandParameter> parameters) {
    Set<String> uniqueParamNames = new HashSet<>();
    List<String> duplicateParamNames = parameters.stream()
                                           .map(WinRmCommandParameter::getParameter)
                                           .filter(param -> !uniqueParamNames.add(param))
                                           .collect(Collectors.toList());

    if (!isEmpty(duplicateParamNames)) {
      throw new InvalidRequestException(format("Command parameter names must be unique, however duplicate(s) found: %s",
          String.join(", ", duplicateParamNames)));
    }
  }

  private boolean isForceDeleteEnabled(String accountIdentifier) {
    boolean isForceDeleteFFEnabled = isForceDeleteFFEnabled(accountIdentifier);
    boolean isForceDeleteEnabledViaSettings =
        isNgSettingsFFEnabled(accountIdentifier) && isForceDeleteFFEnabledViaSettings(accountIdentifier);
    return isForceDeleteFFEnabled && isForceDeleteEnabledViaSettings;
  }

  @VisibleForTesting
  protected boolean isNgSettingsFFEnabled(String accountIdentifier) {
    return featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.NG_SETTINGS);
  }
  @VisibleForTesting
  protected boolean isForceDeleteFFEnabled(String accountIdentifier) {
    return featureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_FORCE_DELETE_CONNECTOR_SECRET);
  }
  @VisibleForTesting
  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
}
