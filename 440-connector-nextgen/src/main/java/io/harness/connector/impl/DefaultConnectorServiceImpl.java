/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.connector.ConnectivityStatus.FAILURE;
import static io.harness.connector.ConnectivityStatus.UNKNOWN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.errorhandling.NGErrorHelper.DEFAULT_ERROR_SUMMARY;
import static io.harness.exception.WingsException.USER;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.EntityType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.connector.CombineCcmK8sConnectorResponseDTO;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorRegistryFactory;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.ManagerExecutable;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.events.ConnectorCreateEvent;
import io.harness.connector.events.ConnectorDeleteEvent;
import io.harness.connector.events.ConnectorForceDeleteEvent;
import io.harness.connector.events.ConnectorUpdateEvent;
import io.harness.connector.helper.CatalogueHelper;
import io.harness.connector.helper.HarnessManagedConnectorHelper;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.services.ConnectorFilterService;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.connector.stats.ConnectorStatusStats;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.ConnectorValidationException;
import io.harness.favorites.ResourceType;
import io.harness.favorites.entities.Favorite;
import io.harness.favorites.services.FavoritesService;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.clients.YamlGitConfigClient;
import io.harness.gitsync.common.dtos.GitSyncConfigDTO;
import io.harness.gitsync.common.utils.GitEntityFilePath;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.EntityObjectIdUtils;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.UserHelperService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class DefaultConnectorServiceImpl implements ConnectorService {
  private final ConnectorMapper connectorMapper;
  private final ConnectorRepository connectorRepository;
  private final ConnectorFilterService filterService;
  private UserHelperService userHelperService;
  private FavoritesService favoritesService;
  private Map<String, ConnectionValidator> connectionValidatorMap;
  private final CatalogueHelper catalogueHelper;
  private final ProjectService projectService;
  private final OrganizationService organizationService;
  ConnectorStatisticsHelper connectorStatisticsHelper;
  NGSettingsClient settingsClient;
  private NGErrorHelper ngErrorHelper;
  private ConnectorErrorMessagesHelper connectorErrorMessagesHelper;
  private SecretRefInputValidationHelper secretRefInputValidationHelper;
  ConnectorHeartbeatService connectorHeartbeatService;
  private final HarnessManagedConnectorHelper harnessManagedConnectorHelper;
  private final ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  private final AccountClient accountClient;
  GitSyncSdkService gitSyncSdkService;
  OutboxService outboxService;
  YamlGitConfigClient yamlGitConfigClient;
  EntitySetupUsageService entitySetupUsageService;
  private static final String CONNECTOR = "connector";

  @Override
  public Optional<ConnectorResponseDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<Connector> connector =
        getInternal(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    return connector.map(x -> getResponse(accountIdentifier, orgIdentifier, projectIdentifier, x));
  }

  @Override
  public Optional<ConnectorResponseDTO> getByRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
        connectorRef, accountIdentifier, orgIdentifier, projectIdentifier, CONNECTOR);
    return get(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  @Override
  public Optional<ConnectorResponseDTO> getByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed) {
    /***
     * In db currently we can have multiple connectors with same name
     * Once cleanup/verification is done, this Page can be converted into optional
     *
     */
    Page<Connector> connectorsWithGivenName =
        getConnectorsWithGivenName(accountIdentifier, orgIdentifier, projectIdentifier, name, isDeletedAllowed);
    if (connectorsWithGivenName.getTotalElements() == 0) {
      return Optional.empty();
    }
    Optional<Connector> connectorEntity = connectorsWithGivenName.get().findFirst();
    return connectorEntity.map(x -> getResponse(accountIdentifier, orgIdentifier, projectIdentifier, x));
  }

  @Override
  public Optional<ConnectorResponseDTO> getFromBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String repo, String branch) {
    Criteria criteria =
        createCriteriaToFetchConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    final Optional<Connector> connectorOptional = connectorRepository.findOne(criteria, repo, branch);
    return connectorOptional.map(x -> getResponse(accountIdentifier, orgIdentifier, projectIdentifier, x));
  }

  @Override
  public Page<ConnectorResponseDTO> list(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable) {
    Page<Connector> connectors = listHelper(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier,
        filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable, null);
    return getResponseList(accountIdentifier, orgIdentifier, projectIdentifier, connectors);
  }

  @Override
  public Page<Connector> listAll(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable,
      String version) {
    return listHelper(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier, filterIdentifier,
        searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable, version);
  }

  @Override
  public Page<ConnectorResponseDTO> list(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable, String version,
      Boolean onlyFavorites) {
    if (BooleanUtils.isTrue(onlyFavorites)) {
      updateFilterPropertiesFromFavorites(
          accountIdentifier, filterProperties, orgIdentifier, projectIdentifier, userHelperService.getUserId());
    }
    Page<Connector> connectors =
        listHelper(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier, filterIdentifier, searchTerm,
            includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable, version);
    return getResponseList(accountIdentifier, orgIdentifier, projectIdentifier, connectors);
  }

  private void updateFilterPropertiesFromFavorites(String accountIdentifier,
      ConnectorFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier, String userId) {
    List<String> favoriteIds = favoritesService
                                   .getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId,
                                       ResourceType.CONNECTOR.toString())
                                   .stream()
                                   .map(Favorite::getResourceIdentifier)
                                   .collect(Collectors.toList());
    List<String> filterConnectorIdentifiers = filterProperties.getConnectorIdentifiers();
    if (favoriteIds.isEmpty()) {
      favoriteIds.add("NO_MATCH");
    }
    if (filterConnectorIdentifiers == null) {
      filterConnectorIdentifiers = new ArrayList<>();
    }
    filterConnectorIdentifiers.addAll(favoriteIds);
    filterProperties.setConnectorIdentifiers(filterConnectorIdentifiers);
  }

  private Page<Connector> listHelper(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable,
      String version) {
    Boolean isBuiltInSMDisabled = isBuiltInSMDisabled(accountIdentifier);

    Criteria criteria = filterService.createCriteriaFromConnectorListQueryParams(accountIdentifier, orgIdentifier,
        projectIdentifier, filterIdentifier, searchTerm, filterProperties, includeAllConnectorsAccessibleAtScope,
        isBuiltInSMDisabled, version);
    Page<Connector> connectors;
    if (Boolean.TRUE.equals(getDistinctFromBranches)
        && gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
      connectors = connectorRepository.findAll(criteria, pageable, true);
    } else if (Boolean.FALSE.equals(getDistinctFromBranches)
        && gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
      connectors = connectorRepository.findAll(criteria, pageable, false);
    } else {
      connectors = connectorRepository.findAll(criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier);
    }
    return connectors;
  }

  private ConnectorResponseDTO getResponse(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Connector connector) {
    ConnectorResponseDTO connectorResponseDTO = connectorMapper.writeDTO(connector);
    populateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, connectorResponseDTO);
    return connectorResponseDTO;
  }

  private Page<ConnectorResponseDTO> getResponseList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Page<Connector> connectors) {
    Page<ConnectorResponseDTO> connectorResponseDTOPage = connectors.map(connectorMapper::writeDTO);
    populateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, connectorResponseDTOPage.getContent());
    populateFavoriteInformation(accountIdentifier, orgIdentifier, projectIdentifier, userHelperService.getUserId(),
        connectorResponseDTOPage.getContent());
    return connectorResponseDTOPage;
  }

  private Page<CombineCcmK8sConnectorResponseDTO> getCcmK8sResponseList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Page<Connector> k8sConnectors, Page<Connector> ccmk8sConnectors) {
    List<CombineCcmK8sConnectorResponseDTO> combineK8sConnectorResponseDTOList = new ArrayList<>();
    Page<ConnectorResponseDTO> connectorResponseDTOPage = k8sConnectors.map(connectorMapper::writeDTO);
    populateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, connectorResponseDTOPage.getContent());

    Page<ConnectorResponseDTO> ccmk8sConnectorResponseDTOPage = ccmk8sConnectors.map(connectorMapper::writeDTO);
    populateGitMetadata(
        accountIdentifier, orgIdentifier, projectIdentifier, ccmk8sConnectorResponseDTOPage.getContent());

    if (!connectorResponseDTOPage.hasContent()) {
      return new PageImpl<>(
          combineK8sConnectorResponseDTOList, k8sConnectors.getPageable(), k8sConnectors.getTotalElements());
    }

    for (ConnectorResponseDTO k8sconnectorResponseDTO : connectorResponseDTOPage.getContent()) {
      log.info("k8sconnectorResponseDTO is: {}", k8sconnectorResponseDTO);
      CombineCcmK8sConnectorResponseDTO combineCcmK8SConnectorResponseDTO = new CombineCcmK8sConnectorResponseDTO();
      combineCcmK8SConnectorResponseDTO.setK8sConnector(k8sconnectorResponseDTO);
      combineCcmK8SConnectorResponseDTO.setCcmk8sConnector(new ArrayList<>());
      for (ConnectorResponseDTO ccmOnlyConnectorResponseDTO : ccmk8sConnectorResponseDTOPage.getContent()) {
        log.info("ccmOnlyConnectorResponseDTO is: {}", ccmOnlyConnectorResponseDTO);
        CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO =
            (CEKubernetesClusterConfigDTO) ccmOnlyConnectorResponseDTO.getConnector().getConnectorConfig();
        if (ceKubernetesClusterConfigDTO.getConnectorRef().equals(
                k8sconnectorResponseDTO.getConnector().getIdentifier())) {
          List<ConnectorResponseDTO> sConnector = combineCcmK8SConnectorResponseDTO.getCcmk8sConnector();
          sConnector.add(ccmOnlyConnectorResponseDTO);
          combineCcmK8SConnectorResponseDTO.setCcmk8sConnector(sConnector);
        }
      }
      combineK8sConnectorResponseDTOList.add(combineCcmK8SConnectorResponseDTO);
    }

    log.info("combineK8sConnectorResponseDTOList is: {}", combineK8sConnectorResponseDTOList);
    return new PageImpl<>(
        combineK8sConnectorResponseDTOList, k8sConnectors.getPageable(), k8sConnectors.getTotalElements());
  }

  private void populateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ConnectorResponseDTO connectorResponseDTO) {
    populateGitMetadata(accountIdentifier, orgIdentifier, projectIdentifier, singletonList(connectorResponseDTO));
  }

  private Map<String, GitSyncConfigDTO> listByRepoIdentifiers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> repoIdentifiers) {
    Map<String, GitSyncConfigDTO> mapToBeReturned = new HashMap<>();
    try {
      List<GitSyncConfigDTO> yamlGitConfigs =
          yamlGitConfigClient.getConfigs(accountIdentifier, orgIdentifier, projectIdentifier).execute().body();
      Map<String, GitSyncConfigDTO> identifierToYamlGitConfigMap =
          yamlGitConfigs.stream().collect(Collectors.toMap(GitSyncConfigDTO::getIdentifier, Function.identity()));
      repoIdentifiers.forEach(
          repoIdentifier -> mapToBeReturned.put(repoIdentifier, identifierToYamlGitConfigMap.get(repoIdentifier)));
    } catch (Exception exception) {
      log.error("Exception while trying to get repo details", exception);
    }

    return mapToBeReturned;
  }

  private void populateGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<ConnectorResponseDTO> connectorResponseList) {
    List<String> repoIdentifiers =
        connectorResponseList.stream()
            .filter(x -> x.getGitDetails() != null && !StringUtils.isEmpty(x.getGitDetails().getRepoIdentifier()))
            .map(x -> x.getGitDetails().getRepoIdentifier())
            .collect(toList());
    Map<String, GitSyncConfigDTO> identifierToYamlGitConfigMap =
        listByRepoIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier, repoIdentifiers);
    connectorResponseList.forEach(connectorResponseDTO -> {
      if (connectorResponseDTO.getGitDetails() != null
          && !StringUtils.isEmpty(connectorResponseDTO.getGitDetails().getRepoIdentifier())) {
        String repoIdentifier = connectorResponseDTO.getGitDetails().getRepoIdentifier();
        GitSyncConfigDTO yamlGitConfigDTO = identifierToYamlGitConfigMap.get(repoIdentifier);
        if (yamlGitConfigDTO != null) {
          connectorResponseDTO.getGitDetails().setRepoName(yamlGitConfigDTO.getName());
        }
      }
    });
  }

  private void populateFavoriteInformation(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String userId, List<ConnectorResponseDTO> connectorResponseList) {
    Set<String> favoriteIds = favoritesService
                                  .getFavorites(accountIdentifier, orgIdentifier, projectIdentifier, userId,
                                      ResourceType.CONNECTOR.toString())
                                  .stream()
                                  .map(Favorite::getResourceIdentifier)
                                  .collect(Collectors.toSet());
    connectorResponseList.forEach(connectorResponseDTO
        -> connectorResponseDTO.setIsFavorite(
            favoriteIds.contains(connectorResponseDTO.getConnector().getIdentifier())));
  }

  public Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory, String version, List<String> connectorIds) {
    Page<Connector> connectors = listAllHelper(page, size, accountIdentifier, orgIdentifier, projectIdentifier,
        searchTerm, type, category, sourceCategory, version, connectorIds);
    return getResponseList(accountIdentifier, orgIdentifier, projectIdentifier, connectors);
  }

  public Page<Connector> listAll(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory, String version) {
    return listAllHelper(page, size, accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, type, category,
        sourceCategory, version, emptyList());
  }

  @Override
  public Page<Connector> listAll(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria =
        where(ConnectorKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(ConnectorKeys.orgIdentifier)
            .is(orgIdentifier)
            .and(ConnectorKeys.projectIdentifier)
            .is(projectIdentifier)
            .orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));

    return connectorRepository.findAll(criteria, Pageable.ofSize(50000));
  }

  private Page<Connector> listAllHelper(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory, String version, List<String> connectorIds) {
    Boolean isBuiltInSMDisabled = isBuiltInSMDisabled(accountIdentifier);

    Criteria criteria = filterService.createCriteriaFromConnectorFilter(accountIdentifier, orgIdentifier,
        projectIdentifier, searchTerm, type, category, sourceCategory, isBuiltInSMDisabled, version, connectorIds);
    Pageable pageable = getPageRequest(
        PageRequest.builder()
            .pageIndex(page)
            .pageSize(size)
            .sortOrders(Collections.singletonList(
                SortOrder.Builder.aSortOrder().withField(ConnectorKeys.createdAt, OrderType.DESC).build()))
            .build());
    return connectorRepository.findAll(criteria, pageable, projectIdentifier, orgIdentifier, accountIdentifier);
  }

  @VisibleForTesting
  void assurePredefined(ConnectorDTO connectorDTO, String accountIdentifier) {
    assureThatTheProjectAndOrgExists(connectorDTO, accountIdentifier);
    assureThatTheSecretIsValidAndItExists(accountIdentifier, connectorDTO.getConnectorInfo());
  }

  private void assureThatTheSecretIsValidAndItExists(String accountIdentifier, ConnectorInfoDTO connectorDTO) {
    List<DecryptableEntity> decryptableEntities = connectorDTO.getConnectorConfig().getDecryptableEntities();
    if (isEmpty(decryptableEntities)) {
      return;
    }
    Map<String, SecretRefData> secrets = secretRefInputValidationHelper.getDecryptableFieldsData(decryptableEntities);
    NGAccess baseNGAccess = BaseNGAccess.builder()
                                .accountIdentifier(accountIdentifier)
                                .orgIdentifier(connectorDTO.getOrgIdentifier())
                                .projectIdentifier(connectorDTO.getProjectIdentifier())
                                .build();
    if (isNotEmpty(secrets)) {
      secrets.forEach((fieldName, secret) -> {
        try {
          secretRefInputValidationHelper.validateTheSecretInput(secret, baseNGAccess);
        } catch (InvalidRequestException | InvalidIdentifierRefException ex) {
          // including field name in the error thrown
          throw new InvalidRequestException(
              String.format("Error while validating %s field : %s", fieldName, ExceptionUtils.getMessage(ex)), ex);
        }
      });
    }
  }

  void assureThatTheProjectAndOrgExists(ConnectorDTO connectorDTO, String accountIdentifier) {
    String orgIdentifier = connectorDTO.getConnectorInfo().getOrgIdentifier();
    String projectIdentifier = connectorDTO.getConnectorInfo().getProjectIdentifier();

    if (isNotEmpty(projectIdentifier)) {
      // its a project level connector
      if (isEmpty(orgIdentifier)) {
        throw new InvalidRequestException(
            String.format("Project %s specified without the org Identifier", projectIdentifier));
      }
      checkThatTheProjectExists(orgIdentifier, projectIdentifier, accountIdentifier);
    } else if (isNotEmpty(orgIdentifier)) {
      // its a org level connector
      checkThatTheOrganizationExists(orgIdentifier, accountIdentifier);
    }
  }

  private void checkThatTheOrganizationExists(String orgIdentifier, String accountIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      final Optional<Organization> organization = organizationService.get(accountIdentifier, orgIdentifier);
      if (!organization.isPresent()) {
        throw new NotFoundException(String.format("org [%s] not found.", orgIdentifier));
      }
    }
  }

  private void checkThatTheProjectExists(String orgIdentifier, String projectIdentifier, String accountIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      final Optional<Project> project = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
      if (!project.isPresent()) {
        throw new NotFoundException(String.format("project [%s] not found.", projectIdentifier));
      }
    }
  }

  /***
   * Saves a connector
   *
   * Note: Don't add any logic in create function, all logic should go inside createInternal method
   *
   * @param connectorRequestDTO
   * @param accountIdentifier
   * @return
   */
  @Override
  public ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    return createInternal(connectorRequestDTO, accountIdentifier, ADD);
  }

  @Override
  public ConnectorResponseDTO create(ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    return createInternal(connector, accountIdentifier, gitChangeType);
  }

  private ConnectorResponseDTO createInternal(
      ConnectorDTO connectorRequestDTO, String accountIdentifier, ChangeType changeType) {
    assurePredefined(connectorRequestDTO, accountIdentifier);
    ConnectorInfoDTO connectorInfo = connectorRequestDTO.getConnectorInfo();
    final boolean isIdentifierUnique = validateTheIdentifierIsUnique(accountIdentifier,
        connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
    if (!isIdentifierUnique) {
      throw new InvalidRequestException(
          String.format("Connector: %s has been given an identifier: %s that already exists. Please enter a unique ID.",
              connectorInfo.getName(), connectorInfo.getIdentifier()));
    }
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equalsIgnoreCase(connectorRequestDTO.getConnectorInfo().getIdentifier())) {
      log.info("[AccountSetup]:Creating default SecretManager");
    }

    Connector connectorEntity = connectorMapper.toConnector(connectorRequestDTO, accountIdentifier);
    connectorEntity.setTimeWhenConnectorIsLastUpdated(System.currentTimeMillis());
    Connector savedConnectorEntity = null;
    try {
      Supplier<OutboxEvent> supplier = null;
      if (!gitSyncSdkService.isGitSyncEnabled(
              accountIdentifier, connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier())) {
        supplier = ()
            -> outboxService.save(new ConnectorCreateEvent(accountIdentifier, connectorRequestDTO.getConnectorInfo()));
      }
      savedConnectorEntity = connectorRepository.save(connectorEntity, connectorRequestDTO, changeType, supplier);
      if (HARNESS_SECRET_MANAGER_IDENTIFIER.equalsIgnoreCase(connectorRequestDTO.getConnectorInfo().getIdentifier())) {
        log.info("[AccountSetup]:Default SecretManager created successfully");
      }
      connectorEntityReferenceHelper.createSetupUsageForSecret(
          connectorRequestDTO.getConnectorInfo(), accountIdentifier, false);
      connectorEntityReferenceHelper.createSetupUsageForTemplate(
          connectorRequestDTO.getConnectorInfo(), accountIdentifier, false);
      log.info("[SecretManagerCreate] Created secret Manager {}", savedConnectorEntity);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format("Connector [%s] already exists", connectorEntity.getIdentifier()));
    }
    return getResponse(
        accountIdentifier, connectorEntity.getOrgIdentifier(), connectorEntity.getProjectIdentifier(), connectorEntity);
  }

  private Page<Connector> getConnectorsWithGivenName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed) {
    Criteria criteria = new Criteria()
                            .and(ConnectorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ConnectorKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ConnectorKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ConnectorKeys.name)
                            .is(name);
    if (isDeletedAllowed) {
      criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    }
    return connectorRepository.findAll(
        criteria, Pageable.unpaged(), projectIdentifier, orgIdentifier, accountIdentifier);
  }

  private List<Connector> getConnectors(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiers) {
    Criteria criteria = new Criteria()
                            .and(ConnectorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ConnectorKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ConnectorKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ConnectorKeys.identifier)
                            .in(connectorIdentifiers);
    return connectorRepository
        .findAll(criteria, Pageable.unpaged(), projectIdentifier, orgIdentifier, accountIdentifier)
        .getContent();
  }

  @Override
  public ConnectorResponseDTO update(ConnectorDTO connectorRequest, String accountIdentifier) {
    return update(connectorRequest, accountIdentifier, ChangeType.MODIFY);
  }

  @Override
  public ConnectorResponseDTO update(
      ConnectorDTO connectorRequest, String accountIdentifier, ChangeType gitChangeType) {
    assurePredefined(connectorRequest, accountIdentifier);
    ConnectorInfoDTO connector = connectorRequest.getConnectorInfo();
    Objects.requireNonNull(connector.getIdentifier());
    Optional<Connector> existingConnectorOptional;

    existingConnectorOptional = getInternal(
        accountIdentifier, connector.getOrgIdentifier(), connector.getProjectIdentifier(), connector.getIdentifier());
    if (!existingConnectorOptional.isPresent()) {
      throw new InvalidRequestException(
          format("No connector exists with the  Identifier %s", connector.getIdentifier()));
    }
    Connector existingConnector = existingConnectorOptional.get();
    final ConnectorResponseDTO oldConnectorDTO = connectorMapper.writeDTO(existingConnector);
    Connector newConnector = connectorMapper.toConnector(connectorRequest, accountIdentifier);
    newConnector.setId(existingConnector.getId());
    newConnector.setVersion(existingConnector.getVersion());
    newConnector.setConnectivityDetails(existingConnector.getConnectivityDetails());
    newConnector.setCreatedAt(existingConnector.getCreatedAt());
    newConnector.setTimeWhenConnectorIsLastUpdated(System.currentTimeMillis());
    newConnector.setActivityDetails(existingConnector.getActivityDetails());
    setGitDetails(existingConnector, newConnector);

    final boolean executeOnDelegate = checkConnectorExecutableOnDelegate(connector);
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        newConnector.getOrgIdentifier(), newConnector.getProjectIdentifier(), newConnector.getIdentifier());

    if (existingConnector.getIsFromDefaultBranch() == null || existingConnector.getIsFromDefaultBranch()) {
      if (existingConnector.getHeartbeatPerpetualTaskId() == null
          && !harnessManagedConnectorHelper.isHarnessManagedSecretManager(connector) && executeOnDelegate
          && !ConnectorType.CUSTOM_SECRET_MANAGER.equals(connector.getConnectorType())) {
        PerpetualTaskId connectorHeartbeatTaskId = connectorHeartbeatService.createConnectorHeatbeatTask(
            accountIdentifier, existingConnector.getOrgIdentifier(), existingConnector.getProjectIdentifier(),
            existingConnector.getIdentifier());
        newConnector.setHeartbeatPerpetualTaskId(
            connectorHeartbeatTaskId == null ? null : connectorHeartbeatTaskId.getId());
      } else if (existingConnector.getHeartbeatPerpetualTaskId() != null) {
        if (executeOnDelegate) {
          connectorHeartbeatService.resetPerpetualTask(
              accountIdentifier, existingConnector.getHeartbeatPerpetualTaskId());
          newConnector.setHeartbeatPerpetualTaskId(existingConnector.getHeartbeatPerpetualTaskId());
        } else {
          connectorHeartbeatService.deletePerpetualTask(
              accountIdentifier, existingConnector.getHeartbeatPerpetualTaskId(), fullyQualifiedIdentifier);
        }
      }
    }
    try {
      Supplier<OutboxEvent> supplier = null;
      if (!gitSyncSdkService.isGitSyncEnabled(
              accountIdentifier, connector.getOrgIdentifier(), connector.getProjectIdentifier())) {
        supplier = ()
            -> outboxService.save(new ConnectorUpdateEvent(
                accountIdentifier, oldConnectorDTO.getConnector(), connectorRequest.getConnectorInfo()));
      }
      Connector updatedConnector = connectorRepository.save(newConnector, connectorRequest, gitChangeType, supplier);
      connectorEntityReferenceHelper.createSetupUsageForSecret(connector, accountIdentifier, true);
      connectorEntityReferenceHelper.createSetupUsageForTemplate(connector, accountIdentifier, true);
      return getResponse(accountIdentifier, updatedConnector.getOrgIdentifier(),
          updatedConnector.getProjectIdentifier(), updatedConnector);

    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(format("Connector [%s] already exists", existingConnector.getIdentifier()));
    }
  }

  @Override
  public ConnectorDTO fullSyncEntity(EntityDetailProtoDTO entityDetailProtoDTO, boolean isFullSyncingToDefaultBranch) {
    IdentifierRefProtoDTO identifierRef = entityDetailProtoDTO.getIdentifierRef();
    String accountIdentifier = identifierRef.getAccountIdentifier().getValue();
    String orgIdentifier = identifierRef.getOrgIdentifier().getValue();
    String projectIdentifier = identifierRef.getProjectIdentifier().getValue();
    String identifier = identifierRef.getIdentifier().getValue();

    Preconditions.checkNotNull(accountIdentifier, "The account identifier input cannot be null for the full sync");
    Preconditions.checkNotNull(orgIdentifier, "The org identifier input cannot be null for the full sync");
    Preconditions.checkNotNull(projectIdentifier, "The project identifier input cannot be null for the full sync");
    Preconditions.checkNotNull(identifier, "The connector identifier input cannot be null for the full sync");

    Optional<Connector> existingConnectorOptional =
        getUnSyncedConnector(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (!existingConnectorOptional.isPresent()) {
      throw new InvalidRequestException(format("No connector exists with the  Identifier %s", identifier));
    }
    Connector connector = existingConnectorOptional.get();
    connector.setHeartbeatPerpetualTaskId(null);
    Connector updatedConnector = connectorRepository.save(connector, ADD);
    ConnectorInfoDTO connectorInfoDTO = getResponse(accountIdentifier, updatedConnector.getOrgIdentifier(),
        updatedConnector.getProjectIdentifier(), updatedConnector)
                                            .getConnector();
    deleteTheExistingReferences(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    connectorEntityReferenceHelper.createSetupUsageForSecret(connectorInfoDTO, accountIdentifier, true);
    String fqn = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (!isFullSyncingToDefaultBranch) {
      connectorHeartbeatService.deletePerpetualTask(accountIdentifier, connector.getHeartbeatPerpetualTaskId(), fqn);
    }
    return ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
  }

  @Override
  public ConnectorResponseDTO updateGitFilePath(
      ConnectorDTO connectorDTO, String accountIdentifier, String newFilePath) {
    Criteria criteria = Criteria.where(ConnectorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ConnectorKeys.orgIdentifier)
                            .is(connectorDTO.getConnectorInfo().getOrgIdentifier())
                            .and(ConnectorKeys.projectIdentifier)
                            .is(connectorDTO.getConnectorInfo().getProjectIdentifier())
                            .and(ConnectorKeys.identifier)
                            .is(connectorDTO.getConnectorInfo().getIdentifier());

    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(newFilePath);
    Update update = new Update()
                        .set(ConnectorKeys.filePath, gitEntityFilePath.getFilePath())
                        .set(ConnectorKeys.rootFolder, gitEntityFilePath.getRootFolder());
    return getResponse(accountIdentifier, connectorDTO.getConnectorInfo().getOrgIdentifier(),
        connectorDTO.getConnectorInfo().getProjectIdentifier(),
        connectorRepository.update(accountIdentifier, connectorDTO.getConnectorInfo().getOrgIdentifier(),
            connectorDTO.getConnectorInfo().getProjectIdentifier(), criteria, update));
  }

  @Override
  public List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiers) {
    Map<String, List<Connector>> connectors =
        getConnectors(accountId, orgIdentifier, projectIdentifier, connectorIdentifiers)
            .stream()
            .collect(groupingBy(Connector::getIdentifier));
    List<Map<String, String>> attributes = new ArrayList<>();
    for (String connectorId : connectorIdentifiers) {
      if (connectors.containsKey(connectorId)) {
        attributes.add(ImmutableMap.of("category",
            ConnectorRegistryFactory.getConnectorCategory(connectors.get(connectorId).get(0).getType()).name()));
      } else {
        attributes.add(Collections.emptyMap());
      }
    }

    return attributes;
  }

  private void deleteTheExistingReferences(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      connectorEntityReferenceHelper.deleteExistingSetupUsages(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
  }

  private Optional<Connector> getUnSyncedConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    Optional<Connector> connectorOptional = Optional.empty();
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
      // todo: @deepak Why we are following this upsert pattern, why not try with resources
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      connectorOptional = connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
          fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier, true);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
    return connectorOptional;
  }

  private Criteria createCriteriaToFetchConnector(
      Object accountIdentifier, Object orgIdentifier, Object projectIdentifier, Object identifier) {
    Criteria criteria = new Criteria();
    criteria.and(ConnectorKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(ConnectorKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(ConnectorKeys.projectIdentifier).is(projectIdentifier);
    criteria.and(ConnectorKeys.identifier).is(identifier);
    criteria.orOperator(where(ConnectorKeys.deleted).exists(false), where(ConnectorKeys.deleted).is(false));
    return criteria;
  }

  private void setGitDetails(Connector existingConnector, Connector newConnector) {
    EntityGitDetailsMapper.copyEntityGitDetails(existingConnector, newConnector);
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, boolean forceDelete) {
    return delete(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, ChangeType.DELETE, forceDelete);
  }

  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, ChangeType changeType, boolean forceDelete) {
    Optional<Connector> existingConnectorOptional =
        getInternal(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    if (!existingConnectorOptional.isPresent()) {
      throw new InvalidRequestException(connectorErrorMessagesHelper.createConnectorNotFoundMessage(
          accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
    }
    Connector existingConnector = existingConnectorOptional.get();
    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(existingConnector);
    if (forceDelete && !isForceDeleteFFEnabledViaSettings(accountIdentifier)) {
      throw new InvalidRequestException(
          format(
              "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [%s]", accountIdentifier),
          USER);
    }

    if (!forceDelete) {
      checkThatTheConnectorIsNotUsedByOthers(existingConnector);
    }
    try {
      connectorEntityReferenceHelper.deleteExistingSetupUsages(
          accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    } catch (Exception e) {
      if (forceDelete) {
        log.warn(
            "Exception occured while deleting references of connector {} with accountId {}, orgId {}, projectId {} - {} ",
            connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier, e);
      } else {
        throw e;
      }
    }
    existingConnector.setDeleted(true);
    Supplier<OutboxEvent> supplier = null;
    if (!gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
      if (forceDelete) {
        supplier = ()
            -> outboxService.save(new ConnectorForceDeleteEvent(
                accountIdentifier, connectorMapper.writeDTO(existingConnector).getConnector()));
      } else {
        supplier = ()
            -> outboxService.save(new ConnectorDeleteEvent(
                accountIdentifier, connectorMapper.writeDTO(existingConnector).getConnector()));
      }
    }

    connectorRepository.delete(existingConnector, null, changeType, supplier);
    favoritesService.deleteFavorites(
        accountIdentifier, orgIdentifier, projectIdentifier, ResourceType.CONNECTOR.toString(), connectorIdentifier);
    return true;
  }

  @Override
  public long count(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = filterService.createCriteriaFromConnectorFilter(
        accountIdentifier, orgIdentifier, projectIdentifier, null, null, null, null, false, null, emptyList());
    return connectorRepository.count(criteria);
  }

  private void checkThatTheConnectorIsNotUsedByOthers(Connector connector) {
    boolean isEntityReferenced;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(connector.getAccountIdentifier())
                                      .orgIdentifier(connector.getOrgIdentifier())
                                      .projectIdentifier(connector.getProjectIdentifier())
                                      .identifier(connector.getIdentifier())
                                      .build();
    String referredEntityFQN = identifierRef.getFullyQualifiedName();
    try {
      isEntityReferenced = entitySetupUsageService.isEntityReferenced(
          connector.getAccountIdentifier(), referredEntityFQN, EntityType.CONNECTORS);
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          connector.getIdentifier(), ex);
      throw new UnexpectedException("Error while deleting the connector");
    }
    if (isEntityReferenced) {
      throw new ReferencedEntityException(String.format(
          "Could not delete the connector %s as it is referenced by other entities", connector.getIdentifier()));
    }
  }

  public ConnectorValidationResult validate(ConnectorDTO connectorRequest, String accountIdentifier) {
    ConnectorInfoDTO connectorInfoDTO = connectorRequest.getConnectorInfo();
    Connector connector =
        getConnectorOrThrowException(accountIdentifier, connectorRequest.getConnectorInfo().getOrgIdentifier(),
            connectorRequest.getConnectorInfo().getProjectIdentifier(),
            connectorRequest.getConnectorInfo().getIdentifier());
    ConnectorResponseDTO connectorResponseDTO = connectorMapper.writeDTO(connector);
    return validateSafely(connectorResponseDTO, connectorInfoDTO, accountIdentifier, connector.getOrgIdentifier(),
        connector.getProjectIdentifier(), connector.getIdentifier());
  }

  public boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    return !connectorRepository.existsByFullyQualifiedIdentifier(
        fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
  }

  @Override
  public ConnectorValidationResult testConnection(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Connector connector =
        getConnectorOrThrowException(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(connector);
    if (!connectorDTO.getEntityValidityDetails().isValid()) {
      return ConnectorValidationResult.builder()
          .status(FAILURE)
          .testedAt(System.currentTimeMillis())
          .errorSummary("Invalid connector yaml")
          .errors(Collections.singletonList(ErrorDetail.builder()
                                                .message("Invalid connector yaml")
                                                .reason("Invalid connector yaml")
                                                .code(400)
                                                .build()))
          .build();
    }
    ConnectorInfoDTO connectorInfo = connectorDTO.getConnector();
    return validateConnector(connector, connectorDTO, connectorInfo, accountIdentifier, orgIdentifier,
        projectIdentifier, connectorIdentifier);
  }

  /**
   * This function tests connection for git connector with repo url from parameter.
   */

  public ConnectorValidationResult testGitRepoConnection(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String gitRepoURL) {
    Connector connector =
        getConnectorOrThrowException(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    ConnectorValidationResult validationResult;

    if (connector.getCategories() != null & !connector.getCategories().contains(ConnectorCategory.CODE_REPO)) {
      log.info("Test Connection failed for connector with identifier[{}] in account[{}] with error [{}]",
          connector.getIdentifier(), accountIdentifier, "Non git connector is provided for repo verification");
      validationResult = ConnectorValidationResult.builder().status(FAILURE).build();
      return validationResult;
    }

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(connector);
    ConnectorInfoDTO connectorInfo = connectorDTO.getConnector();
    ConnectorConfigDTO connectorConfig = connectorInfo.getConnectorConfig();
    // Use Repo URL from parameter instead of using configured URL
    if (isNotEmpty(gitRepoURL)) {
      ScmConnector scmConnector = (ScmConnector) connectorConfig;
      setConnectorGitRepo(scmConnector, gitRepoURL);
      connectorInfo.setConnectorConfig(connectorConfig);
    }
    return validateConnector(connector, connectorDTO, connectorInfo, accountIdentifier, orgIdentifier,
        projectIdentifier, connectorIdentifier);
  }

  private void setConnectorGitRepo(ScmConnector scmConnector, String gitRepoURL) {
    scmConnector.setUrl(gitRepoURL);
    if (scmConnector instanceof GitConfigDTO) {
      ((GitConfigDTO) scmConnector).setGitConnectionType(GitConnectionType.REPO);
    } else if (scmConnector instanceof GithubConnectorDTO) {
      ((GithubConnectorDTO) scmConnector).setConnectionType(GitConnectionType.REPO);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      ((GitlabConnectorDTO) scmConnector).setConnectionType(GitConnectionType.REPO);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      ((BitbucketConnectorDTO) scmConnector).setConnectionType(GitConnectionType.REPO);
    } else if (scmConnector instanceof AwsCodeCommitConnectorDTO) {
      ((AwsCodeCommitConnectorDTO) scmConnector).setUrlType(AwsCodeCommitUrlType.REPO);
    }
  }

  private ConnectorValidationResult validateConnector(Connector connector, ConnectorResponseDTO connectorResponseDTO,
      ConnectorInfoDTO connectorInfo, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier) {
    ConnectorValidationResult validationResult;
    validationResult = validateSafely(
        connectorResponseDTO, connectorInfo, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return validationResult;
  }

  public void updateActivityDetailsInTheConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, ConnectorValidationResult connectorValidationResult,
      Long activityTime) {}

  private Connector getConnectorOrThrowException(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    Optional<Connector> connectorOptional =
        getInternal(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);

    if (connectorOptional.isPresent()) {
      return connectorOptional.get();
    } else {
      throw new InvalidRequestException(connectorErrorMessagesHelper.createConnectorNotFoundMessage(
          accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
    }
  }

  private ConnectorValidationResult validateSafely(ConnectorResponseDTO connectorResponseDTO,
      ConnectorInfoDTO connectorInfo, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier) {
    ConnectionValidator connectionValidator = connectionValidatorMap.get(connectorInfo.getConnectorType().toString());
    ConnectorValidationResult validationResult;
    try {
      log.info("connectorInfo.getConnectorType() {}", connectorInfo.getConnectorType());
      if (isCCMConnector(connectorInfo)) {
        validationResult = connectionValidator.validate(
            connectorResponseDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
        log.info("validation result {}", validationResult);
      } else {
        validationResult = connectionValidator.validate(
            connectorInfo.getConnectorConfig(), accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      }

    } catch (ConnectorValidationException | DelegateServiceDriverException ex) {
      log.error("Test Connection failed for connector with identifier[{}] in account[{}]",
          connectorInfo.getIdentifier(), accountIdentifier, ex);
      ConnectorValidationResultBuilder validationFailureBuilder = ConnectorValidationResult.builder();
      validationFailureBuilder.status(FAILURE).testedAt(System.currentTimeMillis());
      String errorMessage = ex.getMessage();
      if (isNotEmpty(errorMessage)) {
        String errorSummary = ngErrorHelper.getErrorSummary(errorMessage);
        List<ErrorDetail> errorDetail = Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage));
        validationFailureBuilder.errorSummary(errorSummary).errors(errorDetail);
      }
      return validationFailureBuilder.build();
    } catch (WingsException wingsException) {
      log.error("An error occurred while validating the Connector ", wingsException);
      // handle flows which are registered to error handling framework
      throw wingsException;
    } catch (Exception ex) {
      log.error("An error occurred while validating the Connector {}",
          String.format(CONNECTOR_STRING, connectorInfo.getIdentifier(), accountIdentifier,
              connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier()),
          ex);
      return createValidationResultWithGenericError(ex);
    }
    return validationResult;
  }

  private boolean isCCMConnector(ConnectorInfoDTO connectorInfo) {
    return connectorInfo.getConnectorType().equals(ConnectorType.CE_AWS)
        || connectorInfo.getConnectorType().equals(ConnectorType.GCP_CLOUD_COST)
        || connectorInfo.getConnectorType().equals(ConnectorType.CE_AZURE);
  }

  private ConnectorValidationResult createValidationResultWithGenericError(Exception ex) {
    List<ErrorDetail> errorDetails = Collections.singletonList(ngErrorHelper.getGenericErrorDetail());
    return ConnectorValidationResult.builder()
        .errors(errorDetails)
        .errorSummary(DEFAULT_ERROR_SUMMARY)
        .testedAt(System.currentTimeMillis())
        .status(FAILURE)
        .build();
  }

  @Override
  public ConnectorCatalogueResponseDTO getConnectorCatalogue(String accountIdentifier) {
    return ConnectorCatalogueResponseDTO.builder()
        .catalogue(catalogueHelper.getConnectorTypeToCategoryMapping(accountIdentifier))
        .build();
  }

  @Override
  public void updateConnectorEntityWithPerpetualtaskId(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier, String perpetualTaskId) {
    try {
      String fqn = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
          accountIdentifier, connectorOrgIdentifier, connectorProjectIdentifier, connectorIdentifier);
      Criteria criteria = new Criteria();
      criteria.and(ConnectorKeys.fullyQualifiedIdentifier).is(fqn);
      Update update = new Update();
      update.set(ConnectorKeys.heartbeatPerpetualTaskId, perpetualTaskId);
      connectorRepository.update(
          criteria, update, ChangeType.NONE, connectorProjectIdentifier, connectorOrgIdentifier, accountIdentifier);
    } catch (Exception ex) {
      log.info("{} Exception while saving perpetual task id for the {}", CONNECTOR_HEARTBEAT_LOG_PREFIX,
          String.format(CONNECTOR_STRING, connectorIdentifier, accountIdentifier, connectorOrgIdentifier,
              connectorProjectIdentifier),
          ex);
    }
  }

  @Override
  public ConnectorStatistics getConnectorStatistics(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIds) {
    ConnectorStatistics stats =
        connectorStatisticsHelper.getStats(accountIdentifier, orgIdentifier, projectIdentifier, connectorIds);
    changeTheNullStatusToUnknown(stats);
    return stats;
  }

  private void changeTheNullStatusToUnknown(ConnectorStatistics stats) {
    if (stats == null) {
      return;
    }
    List<ConnectorStatusStats> statusStats = stats.getStatusStats();
    for (ConnectorStatusStats connectorStatusStats : statusStats) {
      if (connectorStatusStats.getStatus() == null) {
        connectorStatusStats.setStatus(UNKNOWN);
      }
    }
  }

  @Override
  public String getHeartbeatPerpetualTaskId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Optional<Connector> connectorOptional =
        getInternal(accountIdentifier, orgIdentifier, projectIdentifier, identifier);

    return connectorOptional
        .filter(connector -> connector.getIsFromDefaultBranch() == null || connector.getIsFromDefaultBranch())
        .map(connector -> {
          if (isEmpty(connector.getHeartbeatPerpetualTaskId())) {
            PerpetualTaskId connectorHeatbeatTaskId = connectorHeartbeatService.createConnectorHeatbeatTask(
                accountIdentifier, orgIdentifier, projectIdentifier, identifier);
            if (connectorHeatbeatTaskId != null) {
              updateConnectorEntityWithPerpetualtaskId(
                  accountIdentifier, orgIdentifier, projectIdentifier, identifier, connectorHeatbeatTaskId.getId());
              return connectorHeatbeatTaskId.getId();
            } else {
              return null;
            }
          } else {
            return connector.getHeartbeatPerpetualTaskId();
          }
        })
        .orElseThrow(
            ()
                -> new ConnectorNotFoundException(
                    format(
                        "No connector found with identifier [%s] with accountidentifier [%s], orgIdentifier [%s] and projectIdentifier [%s]",
                        identifier, accountIdentifier, orgIdentifier, projectIdentifier),
                    USER));
  }

  @Override
  public void resetHeartbeatForReferringConnectors(List<Pair<String, String>> connectorPerpetualTaskInfo) {
    for (Pair<String, String> item : connectorPerpetualTaskInfo) {
      log.info("Resetting perpetual task with id {} in account {}", item.getValue(), item.getKey());
      if (item.getValue() != null) {
        connectorHeartbeatService.resetPerpetualTask(item.getKey(), item.getValue());
      }
    }
  }

  public void resetHeartBeatTask(String accountId, String taskId) {
    connectorHeartbeatService.resetPerpetualTask(accountId, taskId);
  }

  @Override
  public List<ConnectorResponseDTO> listbyFQN(String accountIdentifier, List<String> connectorFQN) {
    if (isEmpty(connectorFQN)) {
      return emptyList();
    }

    Pageable pageable = getPageRequest(
        PageRequest.builder()
            .pageSize(connectorFQN.size())
            .sortOrders(Collections.singletonList(
                SortOrder.Builder.aSortOrder().withField(ConnectorKeys.createdAt, OrderType.DESC).build()))
            .build());
    Page<Connector> connectors = connectorRepository.findAll(
        new Criteria().andOperator(Criteria.where(ConnectorKeys.fullyQualifiedIdentifier).in(connectorFQN),
            new Criteria().orOperator(Criteria.where(ConnectorKeys.yamlGitConfigRef)
                                          .exists(true)
                                          .and(ConnectorKeys.isFromDefaultBranch)
                                          .is(true),
                Criteria.where(ConnectorKeys.yamlGitConfigRef).exists(false))),
        pageable, false);
    return connectors.getContent().stream().map(connectorMapper::writeDTO).collect(toList());
  }

  @Override
  public void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiersList) {
    for (String connectorIdentifier : connectorIdentifiersList) {
      Optional<Connector> connectorOptional =
          getInternal(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      connectorOptional
          .map(item -> {
            String heartbeatTaskId = item.getHeartbeatPerpetualTaskId();
            String connectorFQN = item.getFullyQualifiedIdentifier();
            if (isNotBlank(heartbeatTaskId)) {
              boolean perpetualTaskIsDeleted =
                  connectorHeartbeatService.deletePerpetualTask(accountIdentifier, heartbeatTaskId, connectorFQN);
              if (!perpetualTaskIsDeleted) {
                log.info("{} The perpetual task could not be deleted {}", CONNECTOR_HEARTBEAT_LOG_PREFIX, connectorFQN);
                return false;
              }
            }
            item.setDeleted(true);
            connectorRepository.delete(item, ChangeType.DELETE);
            Connector existingConnector = connectorOptional.get();
            ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(existingConnector);
            connectorEntityReferenceHelper.deleteConnectorEntityReferenceWhenConnectorGetsDeleted(
                connectorDTO.getConnector(), accountIdentifier);
            favoritesService.deleteFavorites(accountIdentifier, orgIdentifier, projectIdentifier,
                ResourceType.CONNECTOR.toString(), item.getIdentifier());
            return true;
          })
          .orElseThrow(()
                           -> new ConnectorNotFoundException(
                               String.format("No connector found with identifier %s", connectorIdentifier), USER));
    }
  }

  private Optional<Connector> getInternal(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
        fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier, true);
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String invalidYaml) {
    Optional<Connector> existingConnectorOptional = getInternal(accountIdentifier, entityReference.getOrgIdentifier(),
        entityReference.getProjectIdentifier(), entityReference.getIdentifier());
    if (!existingConnectorOptional.isPresent()) {
      return false;
    }
    Connector existingConnector = existingConnectorOptional.get();
    existingConnector.setEntityInvalid(true);
    existingConnector.setYaml(invalidYaml);
    existingConnector.setObjectIdOfYaml(EntityObjectIdUtils.getObjectIdOfYaml(invalidYaml));
    connectorRepository.save(existingConnector, ChangeType.NONE);
    if (existingConnector.getHeartbeatPerpetualTaskId() != null) {
      log.info("Reset invalid connector heartbeat");
      connectorHeartbeatService.resetPerpetualTask(accountIdentifier, existingConnector.getHeartbeatPerpetualTaskId());
    }
    return true;
  }
  @Override
  public boolean checkConnectorExecutableOnDelegate(ConnectorInfoDTO connectorInfo) {
    final ConnectorConfigDTO connectorConfig = connectorInfo.getConnectorConfig();
    if (connectorConfig instanceof ManagerExecutable) {
      final Boolean executeOnDelegate = ((ManagerExecutable) connectorConfig).getExecuteOnDelegate();
      if (executeOnDelegate == null) {
        return Boolean.TRUE;
      } else {
        return executeOnDelegate;
      }
    }
    return Boolean.TRUE;
  }

  @Override
  public Page<CombineCcmK8sConnectorResponseDTO> listCcmK8S(String accountIdentifier,
      ConnectorFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, Boolean includeAllConnectorsAccessibleAtScope,
      Boolean getDistinctFromBranches, Pageable pageable) {
    filterProperties.setTypes(List.of(ConnectorType.KUBERNETES_CLUSTER));
    Page<Connector> k8sConnectors = listHelper(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier,
        filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable, null);

    List<String> k8sConnectorRefList = new ArrayList<>();
    for (Connector k8sConnector : k8sConnectors) {
      k8sConnectorRefList.add(k8sConnector.getIdentifier());
    }
    log.info("k8sConnectorRefList {}", k8sConnectorRefList);

    Pageable ccmK8sPageable = getPageRequest(0, 100, null);
    filterProperties.setTypes(List.of(ConnectorType.CE_KUBERNETES_CLUSTER));
    filterProperties.setCcmConnectorFilter(CcmConnectorFilter.builder().k8sConnectorRef(k8sConnectorRefList).build());
    Page<Connector> ccmK8sConnectors =
        listHelper(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier, filterIdentifier, searchTerm,
            includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, ccmK8sPageable, null);
    log.info("ccmK8sConnectors count elements: {} pages: {}", ccmK8sConnectors.getTotalElements(),
        ccmK8sConnectors.getTotalPages());
    return getCcmK8sResponseList(accountIdentifier, orgIdentifier, projectIdentifier, k8sConnectors, ccmK8sConnectors);
  }

  @Override
  public Long countConnectors(String accountIdentifier) {
    return connectorRepository.countByAccountIdentifierAndDeletedIsFalse(accountIdentifier);
  }

  private Boolean isBuiltInSMDisabled(String accountIdentifier) {
    return parseBoolean(
        NGRestUtils
            .getResponse(settingsClient.getSetting(
                SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
            .getValue());
  }

  @VisibleForTesting
  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
}
