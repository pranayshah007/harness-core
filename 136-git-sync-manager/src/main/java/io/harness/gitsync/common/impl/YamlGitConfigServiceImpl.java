/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.NGConstants.ENTITY_REFERENCE_LOG_PREFIX;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.nullIfEmpty;
import static io.harness.gitsync.common.YamlConstants.HARNESS_FOLDER_EXTENSION;
import static io.harness.gitsync.common.YamlConstants.PATH_DELIMITER;
import static io.harness.gitsync.common.beans.BranchSyncStatus.SYNCED;
import static io.harness.gitsync.common.remote.YamlGitConfigMapper.toYamlGitConfig;
import static io.harness.ng.core.utils.URLDecoderUtility.getDecodedString;
import static io.harness.scope.ScopeHelper.getScope;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HookEventType;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.Scope;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.DuplicateEntityException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.beans.YamlGitConfig;
import io.harness.gitsync.common.beans.YamlGitConfig.YamlGitConfigKeys;
import io.harness.gitsync.common.events.GitSyncConfigChangeEventConstants;
import io.harness.gitsync.common.events.GitSyncConfigChangeEventType;
import io.harness.gitsync.common.events.GitSyncConfigSwitchType;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.remote.YamlGitConfigMapper;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.repositories.repositories.yamlGitConfig.YamlGitConfigRepository;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.utils.CryptoUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(DX)
public class YamlGitConfigServiceImpl implements YamlGitConfigService {
  private final YamlGitConfigRepository yamlGitConfigRepository;
  private final ConnectorService connectorService;
  private final Producer gitSyncConfigEventProducer;
  private final ExecutorService executorService;
  private final GitBranchService gitBranchService;
  private final GitSyncConnectorHelper gitSyncConnectorHelper;
  private final WebhookEventService webhookEventService;
  private final PersistentLocker persistentLocker;
  private final IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  private final Producer setupUsageEventProducer;
  private final GitSyncSettingsService gitSyncSettingsService;
  private final UserProfileHelper userProfileHelper;
  private final ScmFacilitatorService scmFacilitatorService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject
  public YamlGitConfigServiceImpl(YamlGitConfigRepository yamlGitConfigRepository,
      @Named("connectorDecoratorService") ConnectorService connectorService,
      @Named(EventsFrameworkConstants.GIT_CONFIG_STREAM) Producer gitSyncConfigEventProducer,
      ExecutorService executorService, GitBranchService gitBranchService, GitSyncConnectorHelper gitSyncConnectorHelper,
      WebhookEventService webhookEventService, PersistentLocker persistentLocker,
      IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper,
      @Named(EventsFrameworkConstants.SETUP_USAGE) Producer setupUsageEventProducer,
      GitSyncSettingsService gitSyncSettingsService, UserProfileHelper userProfileHelper,
      ScmFacilitatorService scmFacilitatorService, NGFeatureFlagHelperService ngFeatureFlagHelperService) {
    this.yamlGitConfigRepository = yamlGitConfigRepository;
    this.connectorService = connectorService;
    this.gitSyncConfigEventProducer = gitSyncConfigEventProducer;
    this.executorService = executorService;
    this.gitBranchService = gitBranchService;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.webhookEventService = webhookEventService;
    this.persistentLocker = persistentLocker;
    this.identifierRefProtoDTOHelper = identifierRefProtoDTOHelper;
    this.setupUsageEventProducer = setupUsageEventProducer;
    this.gitSyncSettingsService = gitSyncSettingsService;
    this.userProfileHelper = userProfileHelper;
    this.scmFacilitatorService = scmFacilitatorService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
  }

  @Override
  public YamlGitConfigDTO get(String projectIdentifier, String orgIdentifier, String accountId, String identifier) {
    Optional<YamlGitConfig> yamlGitConfig =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgIdentifier, projectIdentifier, identifier);
    return yamlGitConfig.map(YamlGitConfigMapper::toYamlGitConfigDTO)
        .orElseThrow(()
                         -> new InvalidRequestException(
                             getYamlGitConfigNotFoundMessage(accountId, orgIdentifier, projectIdentifier, identifier)));
  }

  private Optional<YamlGitConfig> getYamlGitConfigEntity(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountId, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public List<YamlGitConfigDTO> list(String projectIdentifier, String orgIdentifier, String accountId) {
    Scope scope = getScope(accountId, orgIdentifier, projectIdentifier);
    List<YamlGitConfig> yamlGitConfigs =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndScopeOrderByCreatedAtDesc(
            accountId, orgIdentifier, projectIdentifier, scope);
    return emptyIfNull(yamlGitConfigs)
        .stream()
        .map(YamlGitConfigMapper::toYamlGitConfigDTO)
        .collect(Collectors.toList());
  }

  @Override
  public YamlGitConfigDTO updateDefault(
      String projectIdentifier, String orgIdentifier, String accountId, String identifier, String folderPath) {
    Optional<YamlGitConfig> yamlGitConfigOptional =
        getYamlGitConfigEntity(accountId, orgIdentifier, projectIdentifier, identifier);
    if (!yamlGitConfigOptional.isPresent()) {
      throw new InvalidRequestException(
          getYamlGitConfigNotFoundMessage(accountId, orgIdentifier, projectIdentifier, identifier));
    }
    YamlGitConfig yamlGitConfig = yamlGitConfigOptional.get();
    List<YamlGitConfigDTO.RootFolder> rootFolders = yamlGitConfig.getRootFolders();
    YamlGitConfigDTO.RootFolder newDefaultRootFolder = null;
    for (YamlGitConfigDTO.RootFolder folder : rootFolders) {
      if (folder.getRootFolder().equals(folderPath)) {
        newDefaultRootFolder = folder;
      }
    }
    if (newDefaultRootFolder == null) {
      throw new InvalidRequestException("No folder exists with the path " + folderPath);
    }
    yamlGitConfig.setDefaultRootFolder(newDefaultRootFolder);
    YamlGitConfig updatedYamlGitConfig = yamlGitConfigRepository.save(yamlGitConfig);
    return YamlGitConfigMapper.toYamlGitConfigDTO(updatedYamlGitConfig);
  }

  @Override
  public List<YamlGitConfigDTO> getByConnectorRepoAndBranch(
      String gitConnectorId, String repo, String branchName, String accountId) {
    List<YamlGitConfig> yamlGitConfigs = yamlGitConfigRepository.findByGitConnectorRefAndRepoAndBranchAndAccountId(
        gitConnectorId, repo, branchName, accountId);
    return emptyIfNull(yamlGitConfigs)
        .stream()
        .map(YamlGitConfigMapper::toYamlGitConfigDTO)
        .collect(Collectors.toList());
  }

  @Override
  public YamlGitConfigDTO save(YamlGitConfigDTO ygs) {
    // before saving the git config, check if branch exists
    // otherwise we end-up saving invalid git configs
    if (!canEnableOldGitSync(ygs.getAccountIdentifier(), ygs.getOrganizationIdentifier(), ygs.getProjectIdentifier())) {
      throw new InvalidRequestException(
          "Cannot enable Git Management for this project, please use new Git Simplified experience.");
    }
    checkIfBranchExists(ygs);
    return saveInternal(ygs, ygs.getAccountIdentifier());
  }

  private boolean canEnableOldGitSync(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.FF_GITSYNC)
        || isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  @Override
  public YamlGitConfigDTO update(YamlGitConfigDTO gitSyncConfig) {
    validatePresenceOfRequiredFields(gitSyncConfig.getAccountIdentifier(), gitSyncConfig.getIdentifier());
    return updateInternal(gitSyncConfig);
  }

  private YamlGitConfigDTO updateInternal(YamlGitConfigDTO gitSyncConfigDTO) {
    validateTheGitConfigInput(gitSyncConfigDTO);
    Optional<YamlGitConfig> existingYamlGitConfigDTO =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier(),
            gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getIdentifier());
    if (!existingYamlGitConfigDTO.isPresent()) {
      throw new InvalidRequestException(getYamlGitConfigNotFoundMessage(gitSyncConfigDTO.getAccountIdentifier(),
          gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier(),
          gitSyncConfigDTO.getIdentifier()));
    }
    validateThatImmutableValuesAreNotChanged(gitSyncConfigDTO, existingYamlGitConfigDTO.get());
    YamlGitConfig yamlGitConfigToBeSaved = toYamlGitConfig(gitSyncConfigDTO, gitSyncConfigDTO.getAccountIdentifier());
    yamlGitConfigToBeSaved.setWebhookToken(existingYamlGitConfigDTO.get().getWebhookToken());
    yamlGitConfigToBeSaved.setUuid(existingYamlGitConfigDTO.get().getUuid());
    yamlGitConfigToBeSaved.setVersion(existingYamlGitConfigDTO.get().getVersion());
    YamlGitConfig yamlGitConfig = yamlGitConfigRepository.save(yamlGitConfigToBeSaved);
    return YamlGitConfigMapper.toYamlGitConfigDTO(yamlGitConfig);
  }

  private void validateThatImmutableValuesAreNotChanged(
      YamlGitConfigDTO gitSyncConfigDTO, YamlGitConfig existingYamlGitConfigDTO) {
    if (!gitSyncConfigDTO.getRepo().equals(existingYamlGitConfigDTO.getRepo())) {
      throw new InvalidRequestException("The repository url of an git config cannot be changed");
    }
    if (!gitSyncConfigDTO.getBranch().equals(existingYamlGitConfigDTO.getBranch())) {
      throw new InvalidRequestException("The default branch of an git config cannot be changed");
    }
  }

  private String getYamlGitConfigNotFoundMessage(
      String accountId, String organizationId, String projectId, String identifier) {
    return String.format("No git sync config exists with the id %s, in account %s, org %s, project %s", identifier,
        accountId, organizationId, projectId);
  }

  private YamlGitConfigDTO saveInternal(YamlGitConfigDTO gitSyncConfigDTO, String accountId) {
    validateTheGitConfigInput(gitSyncConfigDTO);
    YamlGitConfig yamlGitConfigToBeSaved = toYamlGitConfig(gitSyncConfigDTO, accountId);
    yamlGitConfigToBeSaved.setWebhookToken(CryptoUtils.secureRandAlphaNumString(40));
    YamlGitConfig savedYamlGitConfig = null;
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             getYamlGitConfigScopeKey(gitSyncConfigDTO), Duration.ofMinutes(1), Duration.ofMinutes(2))) {
      final boolean wasGitSyncEnabled = isGitSyncEnabled(
          accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier());
      final boolean isNewRepoInProject = isNewRepoInProject(gitSyncConfigDTO);
      savedYamlGitConfig = yamlGitConfigRepository.save(yamlGitConfigToBeSaved);
      if (isNewRepoInProject) {
        registerWebhookAsync(gitSyncConfigDTO);
      }
      sendEventForGitSyncConfigChange(gitSyncConfigDTO, GitSyncConfigChangeEventType.SAVE_EVENT,
          wasGitSyncEnabled ? GitSyncConfigSwitchType.NONE : GitSyncConfigSwitchType.ENABLED);
      sendEventForConnectorSetupUsageChange(gitSyncConfigDTO);
    } catch (DuplicateKeyException ex) {
      String errorMessage = String.format("A git sync config with identifier [%s] or repo [%s] already exists",
          gitSyncConfigDTO.getIdentifier(), gitSyncConfigDTO.getRepo());
      log.error(errorMessage);
      throw new DuplicateEntityException(errorMessage);
    }

    executorService.submit(() -> {
      gitBranchService.createBranches(accountId, gitSyncConfigDTO.getOrganizationIdentifier(),
          gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getGitConnectorRef(), gitSyncConfigDTO.getRepo(),
          gitSyncConfigDTO.getIdentifier());
      gitBranchService.updateBranchSyncStatus(
          accountId, gitSyncConfigDTO.getRepo(), gitSyncConfigDTO.getBranch(), SYNCED);
    });

    return YamlGitConfigMapper.toYamlGitConfigDTO(savedYamlGitConfig);
  }

  private void registerWebhookAsync(YamlGitConfigDTO gitSyncConfigDTO) {
    executorService.submit(() -> saveWebhook(gitSyncConfigDTO));
  }

  private void saveWebhook(YamlGitConfigDTO gitSyncConfigDTO) {
    final RetryPolicy<Object> retryPolicy = getWebhookRegistrationRetryPolicy(
        "[Retrying] attempt: {} for failure case of save webhook call", "Failed to save webhook after {} attempts");
    Failsafe.with(retryPolicy).get(() -> registerWebhook(gitSyncConfigDTO));
  }

  private UpsertWebhookResponseDTO registerWebhook(YamlGitConfigDTO gitSyncConfigDTO) {
    UpsertWebhookRequestDTO upsertWebhookRequest = getUpsertWebhookRequest(gitSyncConfigDTO);
    return webhookEventService.upsertWebhook(upsertWebhookRequest);
  }

  private UpsertWebhookRequestDTO getUpsertWebhookRequest(YamlGitConfigDTO gitSyncConfigDTO) {
    return UpsertWebhookRequestDTO.builder()
        .accountIdentifier(gitSyncConfigDTO.getAccountIdentifier())
        .orgIdentifier(gitSyncConfigDTO.getOrganizationIdentifier())
        .projectIdentifier(gitSyncConfigDTO.getProjectIdentifier())
        .connectorIdentifierRef(gitSyncConfigDTO.getGitConnectorRef())
        .hookEventType(HookEventType.TRIGGER_EVENTS)
        .repoURL(gitSyncConfigDTO.getRepo())
        .build();
  }

  private boolean isNewRepoInProject(YamlGitConfigDTO gitSyncConfigDTO) {
    final Optional<YamlGitConfig> yamlGitConfig =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier(),
            gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getIdentifier());
    return !yamlGitConfig.isPresent();
  }

  private void sendEventForGitSyncConfigChange(YamlGitConfigDTO yamlGitConfigDTO,
      GitSyncConfigChangeEventType eventType, GitSyncConfigSwitchType configSwitchType) {
    String accountId = yamlGitConfigDTO.getAccountIdentifier();
    final EntityScopeInfo.Builder entityScopeInfoBuilder =
        EntityScopeInfo.newBuilder().setAccountId(accountId).setIdentifier(yamlGitConfigDTO.getIdentifier());
    if (isNotEmpty(yamlGitConfigDTO.getOrganizationIdentifier())) {
      entityScopeInfoBuilder.setOrgId(StringValue.of(yamlGitConfigDTO.getOrganizationIdentifier()));
    }
    if (isNotEmpty(yamlGitConfigDTO.getProjectIdentifier())) {
      entityScopeInfoBuilder.setProjectId(StringValue.of(yamlGitConfigDTO.getProjectIdentifier()));
    }

    try {
      final String messageId = gitSyncConfigEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountId, GitSyncConfigChangeEventConstants.EVENT_TYPE,
                  eventType.name(), GitSyncConfigChangeEventConstants.CONFIG_SWITCH_TYPE, configSwitchType.name()))
              .setData(entityScopeInfoBuilder.build().toByteString())
              .build());
      log.info(
          "Produced event with id [{}] for git config change for accountId [{}] during [{}] event for yamlgitconfig [{}]",
          messageId, accountId, eventType, yamlGitConfigDTO.getIdentifier());
    } catch (Exception e) {
      log.error("Event to send git config update failed for accountId [{}] during [{}] event for yamlgitconfig [{}]",
          accountId, eventType, yamlGitConfigDTO.getIdentifier(), e);
    }
  }

  private void validateTheGitConfigInput(YamlGitConfigDTO ygs) {
    ensureFolderEndsWithDelimiter(ygs);
    validateFolderFollowsHarnessParadigm(ygs);
    validateFolderPathIsUnique(ygs);
    validateFoldersAreIndependant(ygs);
    validateAPIAccessFieldPresence(ygs);
    validateThatHarnessStringShouldNotComeMoreThanOnce(ygs);
  }

  @VisibleForTesting
  void validateThatHarnessStringShouldNotComeMoreThanOnce(YamlGitConfigDTO ygs) {
    if (ygs.getRootFolders() == null) {
      return;
    }
    for (YamlGitConfigDTO.RootFolder folder : ygs.getRootFolders()) {
      if (checkIfHarnessDirComesMoreThanOnce(folder.getRootFolder())) {
        throw new InvalidRequestException("The .harness should come only once in the folder path");
      }
    }
  }

  private boolean checkIfHarnessDirComesMoreThanOnce(String folderPath) {
    String[] directoryList = folderPath.split(PATH_DELIMITER);
    boolean harnessDirAlreadyFound = false;
    for (String directory : directoryList) {
      if (HARNESS_FOLDER_EXTENSION.equals(directory)) {
        if (harnessDirAlreadyFound) {
          return true;
        }
        harnessDirAlreadyFound = true;
      }
    }
    return false;
  }

  private void validateAPIAccessFieldPresence(YamlGitConfigDTO ygs) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(ygs.getGitConnectorRef(),
        ygs.getAccountIdentifier(), ygs.getOrganizationIdentifier(), ygs.getProjectIdentifier());
    Optional<ConnectorInfoDTO> gitConnectorOptional = getGitConnector(identifierRef);
    if (gitConnectorOptional.isPresent()) {
      ConnectorConfigDTO connectorConfig = gitConnectorOptional.get().getConnectorConfig();
      if (connectorConfig instanceof ScmConnector) {
        gitSyncConnectorHelper.validateTheAPIAccessPresence((ScmConnector) connectorConfig);
      } else {
        throw new InvalidRequestException(
            String.format("The connector reference %s is not a git connector", ygs.getGitConnectorRef()));
      }
    } else {
      throw new InvalidRequestException(
          String.format("No connector found for the connector reference %s", ygs.getGitConnectorRef()));
    }
  }

  @Override
  public boolean deleteAll(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<YamlGitConfigDTO> yamlGitConfigDTOS = list(projectIdentifier, orgIdentifier, accountIdentifier);
    boolean isDeleted = yamlGitConfigRepository.deleteByAccountIdAndOrgIdentifierAndProjectIdentifier(
                            accountIdentifier, orgIdentifier, projectIdentifier)
        > 0;
    if (isDeleted) {
      gitSyncSettingsService.delete(accountIdentifier, orgIdentifier, projectIdentifier);
      deleteExistingSetupUsages(yamlGitConfigDTOS);
      deleteBranches(yamlGitConfigDTOS);
    }
    return isDeleted;
  }

  @Override
  public void updateTheConnectorRepoAndBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigIdentifier, String repo, String branch) {
    Update update =
        new Update().set(YamlGitConfigKeys.gitConnectorsRepo, repo).set(YamlGitConfigKeys.gitConnectorsBranch, branch);
    Query query = new Query().addCriteria(new Criteria()
                                              .and(YamlGitConfigKeys.accountId)
                                              .is(accountIdentifier)
                                              .and(YamlGitConfigKeys.orgIdentifier)
                                              .is(orgIdentifier)
                                              .and(YamlGitConfigKeys.projectIdentifier)
                                              .is(projectIdentifier)
                                              .and(YamlGitConfigKeys.identifier)
                                              .is(yamlGitConfigIdentifier));
    final UpdateResult status = yamlGitConfigRepository.update(query, update);
    log.info("Updated the repo and branch of YamlGitConfig [{}] with modified count [{}]", yamlGitConfigIdentifier,
        status.getModifiedCount());
  }

  @Override
  public void deleteAllEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<YamlGitConfigDTO> yamlGitConfigDTOS = list(projectIdentifier, orgIdentifier, accountIdentifier);
    deleteExistingSetupUsages(yamlGitConfigDTOS);
    yamlGitConfigRepository.deleteByAccountIdAndOrgIdentifierAndProjectIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier);
  }

  public Optional<ConnectorInfoDTO> getGitConnector(IdentifierRef identifierRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    return connectorDTO.map(ConnectorResponseDTO::getConnector);
  }

  /**
   * Checks that one folder should not be contained within other
   * for eg portal/.harness, portal/.harness/ci/.harness is an
   * invalid folder pair.
   *
   */
  private void validateFoldersAreIndependant(YamlGitConfigDTO ygs) {
    final List<YamlGitConfigDTO.RootFolder> rootFolders = ygs.getRootFolders();
    for (int i = 0; i < rootFolders.size(); i++) {
      for (int j = 0; j < rootFolders.size() && j != i; j++) {
        String firstFolder = rootFolders.get(i).getRootFolder();
        String secondFolder = rootFolders.get(j).getRootFolder();
        if (firstFolder.startsWith(secondFolder)) {
          throw new InvalidRequestException(
              String.format("The folder %s is already contained in %s", firstFolder, secondFolder));
        }
      }
    }
  }

  private void validateFolderPathIsUnique(YamlGitConfigDTO ygs) {
    if (ygs.getRootFolders() == null) {
      return;
    }
    Set<String> folderPaths = new HashSet();
    for (YamlGitConfigDTO.RootFolder folder : ygs.getRootFolders()) {
      if (folderPaths.contains(folder.getRootFolder())) {
        throw new DuplicateFieldException(
            String.format("A folder with name %s already exists in the list", folder.getRootFolder()));
      }
      folderPaths.add(folder.getRootFolder());
    }
  }

  private void ensureFolderEndsWithDelimiter(YamlGitConfigDTO ygs) {
    if (ygs.getRootFolders() == null) {
      return;
    }
    final Optional<YamlGitConfigDTO.RootFolder> rootFolder =
        ygs.getRootFolders().stream().filter(config -> !config.getRootFolder().endsWith(PATH_DELIMITER)).findFirst();
    if (rootFolder.isPresent()) {
      throw new InvalidRequestException("The folder should end with /");
    }
  }

  private void validateFolderFollowsHarnessParadigm(YamlGitConfigDTO ygs) {
    if (ygs.getRootFolders() == null) {
      return;
    }
    final Optional<YamlGitConfigDTO.RootFolder> rootFolder =
        ygs.getRootFolders()
            .stream()
            .filter(
                config -> !config.getRootFolder().endsWith(PATH_DELIMITER + HARNESS_FOLDER_EXTENSION + PATH_DELIMITER))
            .findFirst();
    if (rootFolder.isPresent()) {
      throw new InvalidRequestException("The folder should end with /.harness/");
    }
  }

  @Override
  public boolean isGitSyncEnabled(String accountIdentifier, String organizationIdentifier, String projectIdentifier) {
    return yamlGitConfigRepository.existsByAccountIdAndOrgIdentifierAndProjectIdentifier(
        accountIdentifier, nullIfEmpty(organizationIdentifier), nullIfEmpty(projectIdentifier));
  }

  @Override
  public Boolean isRepoExists(String repo) {
    return yamlGitConfigRepository.existsByRepo(repo);
  }

  @Override
  public List<YamlGitConfigDTO> getByAccountAndRepo(String accountIdentifier, String repo) {
    List<YamlGitConfigDTO> yamlGitConfigDTOs = new ArrayList<>();

    List<YamlGitConfig> yamlGitConfigs =
        yamlGitConfigRepository.findByAccountIdAndRepoOrderByCreatedAtDesc(accountIdentifier, repo);
    yamlGitConfigs.forEach(
        yamlGitConfig -> yamlGitConfigDTOs.add(YamlGitConfigMapper.toYamlGitConfigDTO(yamlGitConfig)));
    return yamlGitConfigDTOs;
  }

  @Override
  public YamlGitConfigDTO getByProjectIdAndRepo(String accountId, String orgId, String projectId, String repo) {
    Optional<YamlGitConfig> yamlGitConfig =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndRepo(
            accountId, orgId, projectId, repo);
    return yamlGitConfig.map(YamlGitConfigMapper::toYamlGitConfigDTO)
        .orElseThrow(() -> new InvalidRequestException("No git sync config exists"));
  }

  @Override
  public Optional<YamlGitConfigDTO> getByProjectIdAndRepoOptional(
      String accountId, String orgId, String projectId, String repo) {
    Optional<YamlGitConfig> yamlGitConfig =
        yamlGitConfigRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndRepo(
            accountId, orgId, projectId, repo);
    if (yamlGitConfig.isPresent()) {
      return yamlGitConfig.map(YamlGitConfigMapper::toYamlGitConfigDTO);
    }
    return Optional.empty();
  }
  private String getYamlGitConfigScopeKey(YamlGitConfigDTO yamlGitConfig) {
    return Stream
        .of(yamlGitConfig.getAccountIdentifier(), yamlGitConfig.getOrganizationIdentifier(),
            yamlGitConfig.getProjectIdentifier())
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(":"));
  }

  private void sendEventForConnectorSetupUsageChange(YamlGitConfigDTO gitSyncConfigDTO) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(gitSyncConfigDTO.getGitConnectorRef(),
        gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier(),
        gitSyncConfigDTO.getProjectIdentifier());

    IdentifierRefProtoDTO yamlGitConfigReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
        gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier(),
        gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getIdentifier());
    IdentifierRefProtoDTO connectorReference =
        identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    EntityDetailProtoDTO yamlGitConfigDetails = EntityDetailProtoDTO.newBuilder()
                                                    .setIdentifierRef(yamlGitConfigReference)
                                                    .setType(EntityTypeProtoEnum.GIT_REPOSITORIES)
                                                    .setName(gitSyncConfigDTO.getName())
                                                    .build();
    EntityDetailProtoDTO connectorDetails = EntityDetailProtoDTO.newBuilder()
                                                .setIdentifierRef(connectorReference)
                                                .setType(EntityTypeProtoEnum.CONNECTORS)
                                                .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(gitSyncConfigDTO.getAccountIdentifier())
                                                         .setReferredByEntity(yamlGitConfigDetails)
                                                         .addReferredEntities(connectorDetails)
                                                         .setDeleteOldReferredByRecords(false)
                                                         .build();
    try {
      setupUsageEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", gitSyncConfigDTO.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                  EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
              .setData(entityReferenceDTO.toByteString())
              .build());
    } catch (Exception e) {
      log.info(ENTITY_REFERENCE_LOG_PREFIX
              + "The entity reference was not created when the connector [{}] was set up from yamlGitConfig [{}]",
          gitSyncConfigDTO.getGitConnectorRef(), gitSyncConfigDTO.getIdentifier());
    }
  }

  private void deleteExistingSetupUsages(List<YamlGitConfigDTO> yamlGitConfigDTOS) {
    for (YamlGitConfigDTO gitSyncConfigDTO : yamlGitConfigDTOS) {
      IdentifierRefProtoDTO yamlGitConfigReference = identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(
          gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier(),
          gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getIdentifier());

      EntityDetailProtoDTO yamlGitConfigDetails = EntityDetailProtoDTO.newBuilder()
                                                      .setIdentifierRef(yamlGitConfigReference)
                                                      .setType(EntityTypeProtoEnum.GIT_REPOSITORIES)
                                                      .setName(gitSyncConfigDTO.getName())
                                                      .build();
      EntitySetupUsageCreateV2DTO entityReferenceDTO =
          EntitySetupUsageCreateV2DTO.newBuilder()
              .setAccountIdentifier(gitSyncConfigDTO.getAccountIdentifier())
              .setReferredByEntity(yamlGitConfigDetails)
              .setDeleteOldReferredByRecords(true)
              .build();

      try {
        setupUsageEventProducer.send(
            Message.newBuilder()
                .putAllMetadata(ImmutableMap.of(EventsFrameworkMetadataConstants.ACCOUNT_IDENTIFIER_METRICS_KEY,
                    gitSyncConfigDTO.getAccountIdentifier(), EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE,
                    EntityTypeProtoEnum.CONNECTORS.name(), EventsFrameworkMetadataConstants.ACTION,
                    EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                .setData(entityReferenceDTO.toByteString())
                .build());
      } catch (Exception ex) {
        log.error(
            String.format(
                "Error deleting the setup usages for the YamlGitConfigs with the identifier {} in project {} in org {}",
                gitSyncConfigDTO.getIdentifier(), gitSyncConfigDTO.getProjectIdentifier(),
                gitSyncConfigDTO.getOrganizationIdentifier()),
            ex);
      }
    }
  }

  private void deleteBranches(List<YamlGitConfigDTO> yamlGitConfigDTOS) {
    for (YamlGitConfigDTO gitSyncConfigDTO : yamlGitConfigDTOS) {
      List<YamlGitConfigDTO> yamlGitConfigDTOList =
          getByAccountAndRepo(gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getRepo());
      if (yamlGitConfigDTOList.size() == 0) {
        gitBranchService.deleteAll(gitSyncConfigDTO.getAccountIdentifier(), gitSyncConfigDTO.getRepo());
      }
    }
  }

  private void checkIfBranchExists(YamlGitConfigDTO ygs) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(ygs.getGitConnectorRef(),
        ygs.getAccountIdentifier(), ygs.getOrganizationIdentifier(), ygs.getProjectIdentifier());
    // listBranchesUsingConnector will throw error if repo url doesn't exists
    List<String> branches = scmFacilitatorService.listBranchesUsingConnector(identifierRef.getAccountIdentifier(),
        ygs.getOrganizationIdentifier(), ygs.getProjectIdentifier(), ygs.getGitConnectorRef(),
        getDecodedString(ygs.getRepo()), null, null);

    if (isEmpty(branches) || !branches.contains(ygs.getBranch())) {
      throw new InvalidRequestException(String.format("Error while checking the branch. Branch doesn't exists."));
    }
  }

  private RetryPolicy<Object> getWebhookRegistrationRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withMaxAttempts(2)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
