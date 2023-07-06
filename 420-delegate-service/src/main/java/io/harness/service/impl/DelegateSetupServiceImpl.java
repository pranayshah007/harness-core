/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateType.DOCKER;
import static io.harness.delegate.utils.DelegateServiceConstants.HEARTBEAT_EXPIRY_TIME_FIVE_MINS;
import static io.harness.filter.FilterType.DELEGATEPROFILE;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.service.impl.DelegateConnectivityStatus.GROUP_STATUS_CONNECTED;
import static io.harness.service.impl.DelegateConnectivityStatus.GROUP_STATUS_DISCONNECTED;
import static io.harness.service.impl.DelegateConnectivityStatus.GROUP_STATUS_PARTIALLY_CONNECTED;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.delegate.beans.AutoUpgrade;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.beans.DelegateGroupDTO;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.beans.DelegateGroupStatus;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateListResponse;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.events.DelegateUpsertEvent;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.delegate.filter.DelegateInstanceConnectivityStatus;
import io.harness.delegate.filter.DelegateInstanceFilter;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.beans.SelectorType;
import software.wings.service.impl.DelegateDao;
import software.wings.service.intfc.ownership.OwnedByAccount;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Criteria;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateSetupServiceImpl implements DelegateSetupService, OwnedByAccount {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;
  @Inject private DelegateDao delegateDao;
  @Inject private FilterService filterService;
  @Inject private OutboxService outboxService;
  // grpc heartbeat thread is scheduled at 5 mins, hence we are allowing a gap of 15 mins
  private static final long MAX_GRPC_HB_TIMEOUT = TimeUnit.MINUTES.toMillis(15);

  private static final long AUTO_UPGRADE_CHECK_TIME_IN_MINUTES = 90;

  @Override
  public long getDelegateGroupCount(
      final String accountId, @Nullable final String orgId, @Nullable final String projectId) {
    return createDelegateGroupsQuery(accountId, orgId, projectId, false).count();
  }

  @Override
  public DelegateGroupListing listDelegateGroupDetails(String accountId, String orgId, String projectId) {
    final List<DelegateGroupDetails> delegateGroupDetails =
        getDelegateGroupDetails(accountId, orgId, projectId, false, null);

    return DelegateGroupListing.builder().delegateGroupDetails(delegateGroupDetails).build();
  }

  @Override
  public DelegateGroupListing listDelegateGroupDetailsUpTheHierarchy(String accountId, String orgId, String projectId) {
    final List<DelegateGroupDetails> delegateGroupDetails =
        getDelegateGroupDetails(accountId, orgId, projectId, true, null);

    return DelegateGroupListing.builder().delegateGroupDetails(delegateGroupDetails).build();
  }

  @Override
  public DelegateGroupDetails getDelegateGroupDetails(String accountId, String delegateGroupId) {
    List<Delegate> groupDelegates = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .filter(DelegateKeys.delegateGroupId, delegateGroupId)
                                        .field(DelegateKeys.status)
                                        .notEqual(DelegateInstanceStatus.DELETED)
                                        .asList();
    DelegateGroup delegateGroup = getDelegateGroup(accountId, delegateGroupId);

    return buildDelegateGroupDetails(accountId, delegateGroup, groupDelegates, delegateGroupId);
  }

  @Override
  public DelegateGroupDetails getDelegateGroupDetailsV2(
      String accountId, String orgId, String projectId, String identifier) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    DelegateGroup delegateGroup = getDelegateGroupByAccountAndOwnerAndIdentifier(accountId, owner, identifier);

    String delegateGroupId = delegateGroup != null ? delegateGroup.getUuid() : null;
    List<Delegate> groupDelegates = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .filter(DelegateKeys.delegateGroupId, delegateGroupId)
                                        .field(DelegateKeys.status)
                                        .notEqual(DelegateInstanceStatus.DELETED)
                                        .asList();

    return buildDelegateGroupDetails(accountId, delegateGroup, groupDelegates, delegateGroupId);
  }

  @Override
  public String getHostNameForGroupedDelegate(String hostname) {
    if (isNotEmpty(hostname)) {
      int indexOfLastHyphen = hostname.lastIndexOf('-');
      if (indexOfLastHyphen > 0) {
        hostname = hostname.substring(0, indexOfLastHyphen) + "-{n}";
      }
    }

    return hostname;
  }

  @Override
  public Map<String, SelectorType> retrieveDelegateImplicitSelectors(Delegate delegate, boolean fetchFromCache) {
    SortedMap<String, SelectorType> selectorTypeMap = new TreeMap<>();

    if (isNotEmpty(delegate.getDelegateGroupId())) {
      DelegateGroup delegateGroup = fetchFromCache
          ? delegateCache.getDelegateGroup(delegate.getAccountId(), delegate.getDelegateGroupId())
          : getDelegateGroup(delegate.getAccountId(), delegate.getDelegateGroupId());

      if (delegateGroup != null) {
        selectorTypeMap.put(delegateGroup.getName().toLowerCase(), SelectorType.GROUP_NAME);

        if (isNotEmpty(delegateGroup.getTags())) {
          for (String selector : delegateGroup.getTags()) {
            selectorTypeMap.put(selector.toLowerCase(), SelectorType.GROUP_SELECTORS);
          }
        }
        if (isNotEmpty(delegateGroup.getRunnerTypes())) {
          for (String selector : delegateGroup.getRunnerTypes()) {
            selectorTypeMap.put(selector.toLowerCase(), SelectorType.GROUP_SELECTORS);
          }
        }
      }
    }

    if (isNotEmpty(delegate.getHostName())) {
      // Consider hostname as selector for delegate.
      selectorTypeMap.put(delegate.getHostName().toLowerCase(), SelectorType.HOST_NAME);
    }

    if (isNotEmpty(delegate.getDelegateName())) {
      selectorTypeMap.put(delegate.getDelegateName().toLowerCase(), SelectorType.DELEGATE_NAME);
    }

    DelegateProfile delegateProfile =
        delegateCache.getDelegateProfile(delegate.getAccountId(), delegate.getDelegateProfileId());

    if (delegateProfile != null && isNotEmpty(delegateProfile.getName())) {
      selectorTypeMap.put(delegateProfile.getName().toLowerCase(), SelectorType.PROFILE_NAME);
    }

    if (delegateProfile != null && isNotEmpty(delegateProfile.getSelectors())) {
      for (String selector : delegateProfile.getSelectors()) {
        selectorTypeMap.put(selector.toLowerCase(), SelectorType.PROFILE_SELECTORS);
      }
    }

    return selectorTypeMap;
  }

  @Override
  public Map<String, SelectorType> retrieveDelegateGroupImplicitSelectors(final DelegateGroup delegateGroup) {
    final SortedMap<String, SelectorType> result = new TreeMap<>();

    if (delegateGroup == null) {
      return result;
    }

    result.put(delegateGroup.getName().toLowerCase(), SelectorType.GROUP_NAME);

    final DelegateProfile delegateProfile =
        delegateCache.getDelegateProfile(delegateGroup.getAccountId(), delegateGroup.getDelegateConfigurationId());

    if (delegateProfile != null && isNotEmpty(delegateProfile.getName())) {
      result.put(delegateProfile.getName().toLowerCase(), SelectorType.PROFILE_NAME);
    }

    if (delegateProfile != null && isNotEmpty(delegateProfile.getSelectors())) {
      for (final String selector : delegateProfile.getSelectors()) {
        result.put(selector.toLowerCase(), SelectorType.PROFILE_SELECTORS);
      }
    }
    return result;
  }

  @Override
  public List<Boolean> validateDelegateGroups(
      String accountId, String orgId, String projectId, List<String> identifiers) {
    if (isEmpty(identifiers)) {
      return emptyList();
    }
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    Query<DelegateGroup> queryByUuids = persistence.createQuery(DelegateGroup.class)
                                            .filter(DelegateGroupKeys.accountId, accountId)
                                            .filter(DelegateGroupKeys.ng, true)
                                            .field(DelegateGroupKeys.uuid)
                                            .in(identifiers);

    Query<DelegateGroup> queryByIdentifiers = persistence.createQuery(DelegateGroup.class)
                                                  .filter(DelegateGroupKeys.accountId, accountId)
                                                  .filter(DelegateGroupKeys.ng, true)
                                                  .filter(DelegateGroupKeys.owner, owner)
                                                  .field(DelegateGroupKeys.identifier)
                                                  .in(identifiers)
                                                  .field(DelegateGroupKeys.status)
                                                  .notEqual(DelegateGroupStatus.DELETED);

    if (owner != null) {
      queryByUuids.filter(DelegateGroupKeys.owner, owner);
    } else {
      // Account level delegates
      queryByUuids.field(DelegateGroupKeys.owner).doesNotExist();
    }
    queryByUuids.field(DelegateGroupKeys.status).notEqual(DelegateGroupStatus.DELETED);

    // We need to fetch all entries by both uuid and identifiers, because it is possible to have them in the list
    // Identifiers are part of NG as user friendly ids which are used as unique identifier in combination with accountId
    // and owner.
    Set<String> existingRecordsKeys =
        queryByUuids.asKeyList().stream().map(key -> (String) key.getId()).collect(toSet());

    Set<String> existingRecordsIdentifiers =
        queryByIdentifiers.asList().stream().map(DelegateGroup::getIdentifier).collect(toSet());

    // creating union from two sets
    existingRecordsKeys.addAll(existingRecordsIdentifiers);

    return identifiers.stream().map(existingRecordsKeys::contains).collect(toList());
  }

  @Override
  public List<Boolean> validateDelegateConfigurations(
      String accountId, String orgId, String projectId, List<String> identifiers) {
    if (isEmpty(identifiers)) {
      return emptyList();
    }

    Query<DelegateProfile> query = persistence.createQuery(DelegateProfile.class)
                                       .filter(DelegateProfileKeys.accountId, accountId)
                                       .filter(DelegateProfileKeys.ng, true);
    Query<DelegateProfile> filterIdentifiersQuery = persistence.createQuery(DelegateProfile.class)
                                                        .filter(DelegateProfileKeys.accountId, accountId)
                                                        .filter(DelegateProfileKeys.ng, true);

    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    if (owner != null) {
      query.field(DelegateProfileKeys.owner).equal(owner);
      filterIdentifiersQuery.field(DelegateProfileKeys.owner).equal(owner);
    } else {
      // Account level delegate configurations
      query.field(DelegateProfileKeys.owner).doesNotExist();
      filterIdentifiersQuery.field(DelegateProfileKeys.owner).doesNotExist();
    }
    query.field(DelegateProfileKeys.uuid).in(identifiers);
    List<String> existingRecordsKeys = query.asKeyList().stream().map(key -> (String) key.getId()).collect(toList());

    filterIdentifiersQuery.field(DelegateProfileKeys.identifier).in(identifiers);
    List<String> existingRecordsIdentifiers =
        filterIdentifiersQuery.asList().stream().map(DelegateProfile::getIdentifier).collect(toList());
    return identifiers.stream()
        .map(i -> existingRecordsKeys.contains(i) || existingRecordsIdentifiers.contains(i))
        .collect(toList());
  }

  @Override
  public DelegateGroupDetails updateDelegateGroup(
      String accountId, String delegateGroupId, DelegateGroupDetails delegateGroupDetails) {
    Query<DelegateGroup> updateQuery = persistence.createQuery(DelegateGroup.class)
                                           .filter(DelegateKeys.accountId, accountId)
                                           .filter(DelegateKeys.uuid, delegateGroupId);

    UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
    setUnset(updateOperations, DelegateGroupKeys.tags, delegateGroupDetails.getGroupCustomSelectors());

    DelegateGroup updatedDelegateGroup =
        persistence.findAndModify(updateQuery, updateOperations, HPersistence.returnNewOptions);

    return buildDelegateGroupDetails(accountId, updatedDelegateGroup, null, delegateGroupId);
  }

  @Override
  public DelegateGroupDetails updateDelegateGroup(
      String accountId, String orgId, String projectId, String identifier, DelegateGroupDetails delegateGroupDetails) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    Query<DelegateGroup> updateQuery = persistence.createQuery(DelegateGroup.class)
                                           .filter(DelegateGroupKeys.accountId, accountId)
                                           .filter(DelegateGroupKeys.owner, owner)
                                           .filter(DelegateGroupKeys.identifier, identifier);

    UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
    setUnset(updateOperations, DelegateGroupKeys.tags, delegateGroupDetails.getGroupCustomSelectors());

    DelegateGroup updatedDelegateGroup =
        persistence.findAndModify(updateQuery, updateOperations, HPersistence.returnNewOptions);

    String delegateGroupId = updatedDelegateGroup != null ? updatedDelegateGroup.getUuid() : null;
    return buildDelegateGroupDetails(accountId, updatedDelegateGroup, null, delegateGroupId);
  }

  private List<DelegateGroupDetails> getDelegateGroupDetails(final String accountId, final String orgId,
      final String projectId, final boolean upTheHierarchy, String delegateTokenName) {
    final Query<DelegateGroup> query = createDelegateGroupsQuery(accountId, orgId, projectId, upTheHierarchy);

    final List<String> delegateGroupIds = query.asKeyList().stream().map(key -> (String) key.getId()).collect(toList());

    Query<Delegate> delegateQuery = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .field(DelegateKeys.delegateGroupId)
                                        .in(delegateGroupIds);

    if (isNotEmpty(delegateTokenName)) {
      delegateQuery = delegateQuery.filter(DelegateKeys.delegateTokenName, delegateTokenName);
    }

    final Map<String, List<Delegate>> delegatesByGroup =
        delegateQuery.asList().stream().collect(groupingBy(Delegate::getDelegateGroupId));

    return delegateGroupIds.stream()
        .filter(delegateGroupId
            -> isEmpty(delegateTokenName)
                || (delegatesByGroup.get(delegateGroupId) != null && !delegatesByGroup.get(delegateGroupId).isEmpty()))
        .map(delegateGroupId -> {
          DelegateGroup delegateGroup = getDelegateGroup(accountId, delegateGroupId);
          return buildDelegateGroupDetails(
              accountId, delegateGroup, delegatesByGroup.get(delegateGroupId), delegateGroupId);
        })
        .collect(toList());
  }

  private Query<DelegateGroup> createDelegateGroupsQuery(final String accountId, @Nullable final String orgId,
      @Nullable final String projectId, final boolean upTheHierarchy) {
    final Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class)
                                                        .filter(DelegateGroupKeys.accountId, accountId)
                                                        .filter(DelegateGroupKeys.ng, true);

    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    if (owner != null) {
      if (upTheHierarchy) {
        final String projectIdentifier = projectId != null ? owner.getIdentifier() : null;
        delegateGroupQuery.field(DelegateKeys.owner_identifier).in(Arrays.asList(null, orgId, projectIdentifier));
      } else {
        delegateGroupQuery.filter(DelegateGroupKeys.owner, owner);
      }
    } else {
      // Account level delegates
      delegateGroupQuery.field(DelegateGroupKeys.owner).doesNotExist();
    }

    return delegateGroupQuery.field(DelegateGroupKeys.status).notEqual(DelegateGroupStatus.DELETED);
  }

  private DelegateGroupDetails buildDelegateGroupDetails(
      String accountId, DelegateGroup delegateGroup, List<Delegate> groupDelegates, String delegateGroupId) {
    if (groupDelegates == null) {
      log.debug("There are no delegates related to this delegate group.");
      groupDelegates = emptyList();
    }

    List<String> delegateTokensNameList = new ArrayList<>();
    groupDelegates.forEach(delegate -> delegateTokensNameList.add(delegate.getDelegateTokenName()));
    Map<String, Boolean> delegateTokenStatusMap = isDelegateTokenActive(accountId, delegateTokensNameList);

    String delegateType = delegateGroup != null ? delegateGroup.getDelegateType() : null;
    String groupName = delegateGroup != null ? delegateGroup.getName() : null;
    String delegateDescription = delegateGroup != null ? delegateGroup.getDescription() : null;
    String delegateConfigurationId = delegateGroup != null ? delegateGroup.getDelegateConfigurationId() : null;
    String delegateGroupIdentifier = delegateGroup != null ? delegateGroup.getIdentifier() : null;
    Set<String> groupCustomSelectors = delegateGroup != null ? delegateGroup.getTags() : null;
    long upgraderLastUpdated = delegateGroup != null ? delegateGroup.getUpgraderLastUpdated() : 0;
    long groupExpirationTime = groupDelegates.stream().mapToLong(Delegate::getExpirationTime).min().orElse(0);
    long delegateCreationTime = groupDelegates.stream().mapToLong(Delegate::getCreatedAt).min().orElse(0);
    boolean immutableDelegate = isNotEmpty(groupDelegates) && groupDelegates.get(0).isImmutable();

    // pick any connected delegateId to check whether grpc is active or not
    AtomicReference<String> delegateId = new AtomicReference<>();

    long lastHeartBeat = groupDelegates.stream().mapToLong(Delegate::getLastHeartBeat).max().orElse(0);
    AtomicInteger countOfDelegatesConnected = new AtomicInteger();
    AtomicBoolean isDelegateTokenActiveAtGroupLevel = new AtomicBoolean(true);
    List<Delegate> connectedDelegates = new ArrayList<>();
    List<DelegateGroupListing.DelegateInner> delegateInstanceDetails =
        groupDelegates.stream()
            .map(delegate -> {
              boolean isDelegateConnected =
                  delegate.getLastHeartBeat() > System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis();
              countOfDelegatesConnected.addAndGet(isDelegateConnected ? 1 : 0);

              String delegateTokenName = delegate.getDelegateTokenName();

              // TODO: Arpit, fetch the tokenStatus from cache instead of db
              boolean isTokenActive = true;
              if (delegateTokenName != null && delegateTokenStatusMap.containsKey(delegateTokenName)) {
                isTokenActive = delegateTokenStatusMap.get(delegateTokenName);
              }
              // if delegate token is not active, then token at group level will not be active
              isDelegateTokenActiveAtGroupLevel.compareAndSet(!isTokenActive, false);
              if (isDelegateConnected) {
                delegateId.set(delegate.getUuid());
                connectedDelegates.add(delegate);
              }
              return DelegateGroupListing.DelegateInner.builder()
                  .uuid(delegate.getUuid())
                  .lastHeartbeat(delegate.getLastHeartBeat())
                  .activelyConnected(isDelegateConnected)
                  .hostName(delegate.getHostName())
                  .tokenActive(isTokenActive)
                  .delegateExpirationTime(delegate.getExpirationTime())
                  .version(delegate.getVersion())
                  .build();
            })
            .collect(Collectors.toList());

    String connectivityStatus = GROUP_STATUS_PARTIALLY_CONNECTED;
    if (countOfDelegatesConnected.get() == 0) {
      connectivityStatus = GROUP_STATUS_DISCONNECTED;
    } else if (countOfDelegatesConnected.get() >= groupDelegates.size()) {
      connectivityStatus = GROUP_STATUS_CONNECTED;
    }

    String groupVersion = connectedDelegates.stream()
                              .min(Comparator.comparing(Delegate::getVersion))
                              .map(Delegate::getVersion)
                              .orElse(null);

    return DelegateGroupDetails.builder()
        .groupId(delegateGroupId)
        .delegateGroupIdentifier(delegateGroupIdentifier)
        .delegateType(delegateType)
        .groupName(groupName)
        .autoUpgrade(
            setAutoUpgrade(upgraderLastUpdated, immutableDelegate, delegateCreationTime, groupVersion, delegateType))
        .upgraderLastUpdated(upgraderLastUpdated)
        .delegateGroupExpirationTime(groupExpirationTime)
        .delegateDescription(delegateDescription)
        .delegateConfigurationId(delegateConfigurationId)
        .groupImplicitSelectors(retrieveDelegateGroupImplicitSelectors(delegateGroup))
        .groupCustomSelectors(groupCustomSelectors)
        .lastHeartBeat(lastHeartBeat)
        .delegateInstanceDetails(delegateInstanceDetails)
        .connectivityStatus(connectivityStatus)
        .activelyConnected(!connectivityStatus.equals(GROUP_STATUS_DISCONNECTED))
        .tokenActive(isDelegateTokenActiveAtGroupLevel.get())
        .immutable(immutableDelegate)
        .groupVersion(groupVersion)
        .build();
  }

  @Override
  public DelegateGroupListing listDelegateGroupDetailsV2(String accountId, String orgId, String projectId,
      String filterIdentifier, String searchTerm, DelegateFilterPropertiesDTO filterProperties,
      PageRequest pageRequest) {
    if (isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    }

    if (isNotEmpty(filterIdentifier)) {
      FilterDTO filterDTO = filterService.get(accountId, orgId, projectId, filterIdentifier, DELEGATEPROFILE);
      filterProperties = (DelegateFilterPropertiesDTO) filterDTO.getFilterProperties();
    }

    List<String> delegateGroupIds = getDelegateGroupIds(accountId, orgId, projectId, filterProperties, searchTerm);

    List<Delegate> delegateList = getFilteredDelegateList(accountId, filterProperties, delegateGroupIds);

    final Map<String, List<Delegate>> delegatesByGroup =
        delegateList.stream().collect(groupingBy(Delegate::getDelegateGroupId));

    // Remove groupId which doesn't have single delegate after filter is applied.
    if (filterProperties != null && newFilterStatus(filterProperties)) {
      delegateGroupIds = delegateGroupIds.stream()
                             .filter(delegateGroup -> isNotEmpty(delegatesByGroup.get(delegateGroup)))
                             .collect(toList());
    }

    List<DelegateGroupDetails> delegateGroupDetails =
        delegateGroupIds.stream()
            .map(delegateGroupId -> {
              DelegateGroup delegateGroup = getDelegateGroup(accountId, delegateGroupId);
              return buildDelegateGroupDetails(
                  accountId, delegateGroup, delegatesByGroup.get(delegateGroupId), delegateGroupId);
            })
            .sorted(new DelegateGroupDetailsComparator())
            .collect(toList());
    sortDelegateGroups(delegateGroupDetails, pageRequest);
    return DelegateGroupListing.builder().delegateGroupDetails(delegateGroupDetails).build();
  }

  private List<Delegate> getFilteredDelegateList(
      String accountId, DelegateFilterPropertiesDTO filterProperties, List<String> delegateGroupIds) {
    List<Delegate> delegateList;
    Query<Delegate> delegateQuery = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .field(DelegateKeys.delegateGroupId)
                                        .in(delegateGroupIds);

    if (filterProperties != null && filterProperties.getHostName() != null) {
      delegateQuery.filter(DelegateKeys.hostName, filterProperties.getHostName());
    }
    delegateList = delegateQuery.asList();

    // filter delegates if filtering by delegateInstanceConnectivityStatus is enabled
    if (filterProperties != null) {
      if (filterProperties.getStatus() != null) {
        delegateList = filterByDelegateInstanceConnectionStatus(delegateList, filterProperties);
      }
      if (filterProperties.getDelegateInstanceFilter() != null) {
        delegateList = filterByDelegateInstanceStatus(delegateList, filterProperties.getDelegateInstanceFilter());
      }
    }
    return delegateList;
  }

  @Override
  public List<DelegateListResponse> listDelegates(
      String accountId, String orgId, String projectId, DelegateFilterPropertiesDTO filterProperties) {
    List<String> delegateGroupIds = getDelegateGroupIds(accountId, orgId, projectId, filterProperties, null);

    List<Delegate> delegateList = getFilteredDelegateList(accountId, filterProperties, delegateGroupIds);

    final Map<String, List<Delegate>> delegatesByGroup =
        delegateList.stream().collect(groupingBy(Delegate::getDelegateGroupId));

    // Remove groupId which doesn't have single delegate after filter is applied.
    if (filterProperties != null && newFilterStatus(filterProperties)) {
      delegateGroupIds = delegateGroupIds.stream()
                             .filter(delegateGroup -> isNotEmpty(delegatesByGroup.get(delegateGroup)))
                             .collect(toList());
    }

    return delegateGroupIds.stream()
        .map(delegateGroupId -> {
          DelegateGroup delegateGroup = getDelegateGroup(accountId, delegateGroupId);
          return buildDelegateGroupResponse(delegateGroup, delegatesByGroup.get(delegateGroupId));
        })
        .sorted(Comparator.comparing(DelegateListResponse::isConnected, Comparator.reverseOrder()))
        .collect(toList());
  }

  private DelegateListResponse buildDelegateGroupResponse(DelegateGroup delegateGroup, List<Delegate> groupDelegates) {
    if (groupDelegates == null) {
      log.debug("There are no delegates related to this delegate group.");
      groupDelegates = emptyList();
    }

    String delegateType = delegateGroup != null ? delegateGroup.getDelegateType() : null;
    Set<String> groupCustomSelectors = delegateGroup != null ? delegateGroup.getTags() : null;
    long delegateCreationTime = groupDelegates.stream().mapToLong(Delegate::getCreatedAt).min().orElse(0);
    boolean immutableDelegate = isNotEmpty(groupDelegates) && groupDelegates.get(0).isImmutable();

    long lastHeartBeat = groupDelegates.stream().mapToLong(Delegate::getLastHeartBeat).max().orElse(0);

    List<DelegateListResponse.DelegateReplica> delegateReplicas =
        groupDelegates.stream()
            .map(delegate -> {
              boolean isDelegateConnected =
                  delegate.getLastHeartBeat() > System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis();
              return DelegateListResponse.DelegateReplica.builder()
                  .uuid(delegate.getUuid())
                  .lastHeartbeat(delegate.getLastHeartBeat())
                  .connected(isDelegateConnected)
                  .hostName(delegate.getHostName())
                  .expiringAt(delegate.getExpirationTime())
                  .version(delegate.getVersion())
                  .build();
            })
            .collect(Collectors.toList());

    Set<String> delegateSelectors = new HashSet<>(retrieveDelegateGroupImplicitSelectors(delegateGroup).keySet());
    if (groupCustomSelectors != null) {
      delegateSelectors.addAll(groupCustomSelectors);
    }

    return DelegateListResponse.builder()
        .type(delegateType)
        .name(delegateGroup.getName())
        .autoUpgrade(setAutoUpgrade(
            delegateGroup.getUpgraderLastUpdated(), immutableDelegate, delegateCreationTime, null, delegateType))
        .description(delegateGroup.getDescription())
        .tags(delegateSelectors)
        .lastHeartBeat(lastHeartBeat)
        .delegateReplicas(delegateReplicas)
        .connected(delegateReplicas.stream().anyMatch(DelegateListResponse.DelegateReplica::isConnected))
        .legacy(!immutableDelegate)
        .build();
  }

  private List<Delegate> filterByDelegateInstanceStatus(
      List<Delegate> delegateList, DelegateInstanceFilter delegateInstanceStatus) {
    if (DelegateInstanceFilter.EXPIRED.equals(delegateInstanceStatus)) {
      return delegateList.stream()
          .filter(delegate -> delegate.getExpirationTime() < System.currentTimeMillis())
          .collect(toList());
    } else if (DelegateInstanceFilter.AVAILABLE.equals(delegateInstanceStatus)) {
      return delegateList.stream()
          .filter(delegate -> delegate.getExpirationTime() >= System.currentTimeMillis())
          .collect(toList());
    }
    return delegateList;
  }

  private List<DelegateGroupDetails> sortDelegateGroups(
      List<DelegateGroupDetails> delegateGroupDetails, PageRequest pageRequest) {
    if (isNotEmpty(pageRequest.getSortOrders())) {
      SortOrder sortOrder = pageRequest.getSortOrders().get(0);
      switch (sortOrder.getFieldName()) {
        case "name":
          if (sortOrder.getOrderType().equals(SortOrder.OrderType.DESC)) {
            delegateGroupDetails.sort(Comparator.comparing(DelegateGroupDetails::getGroupName).reversed());
          } else {
            delegateGroupDetails.sort(Comparator.comparing(DelegateGroupDetails::getGroupName));
          }
          break;
        case "version":
          if (sortOrder.getOrderType().equals(SortOrder.OrderType.DESC)) {
            delegateGroupDetails.sort(
                Comparator
                    .comparing(
                        (DelegateGroupDetails del) -> isEmpty(del.getGroupVersion()) ? "" : del.getGroupVersion())
                    .reversed());
          } else {
            delegateGroupDetails.sort(
                Comparator.comparing(del -> isEmpty(del.getGroupVersion()) ? "" : del.getGroupVersion()));
          }
          break;
        case "status":
          if (sortOrder.getOrderType().equals(SortOrder.OrderType.DESC)) {
            Collections.reverse(delegateGroupDetails);
          }
          break;
        default:
          break;
      }
    }
    return delegateGroupDetails;
  }

  // Todo: Anupam to remove this condition once UI changes are deployed.
  private boolean newFilterStatus(DelegateFilterPropertiesDTO filterProperties) {
    if (filterProperties.getDelegateInstanceFilter() != null) {
      return true;
    }
    if (filterProperties.getStatus() != null) {
      return DelegateInstanceConnectivityStatus.CONNECTED.equals(filterProperties.getStatus())
          || DelegateInstanceConnectivityStatus.DISCONNECTED.equals(filterProperties.getStatus());
    }
    return false;
  }

  private List<Delegate> filterByDelegateInstanceConnectionStatus(
      List<Delegate> delegateList, DelegateFilterPropertiesDTO filterProperties) {
    if (filterProperties.getStatus().equals(DelegateInstanceConnectivityStatus.DISCONNECTED)) {
      return delegateList.stream()
          .filter(delegate
              -> delegate.getLastHeartBeat() <= System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis())
          .collect(toList());
    } else if (filterProperties.getStatus().equals(DelegateInstanceConnectivityStatus.CONNECTED)) {
      return delegateList.stream()
          .filter(delegate
              -> delegate.getLastHeartBeat() > System.currentTimeMillis() - HEARTBEAT_EXPIRY_TIME_FIVE_MINS.toMillis())
          .collect(toList());
    }
    return delegateList;
  }

  @Override
  public DelegateGroupListing listDelegateGroupDetails(
      String accountId, String orgId, String projectId, String delegateTokenName) {
    return DelegateGroupListing.builder()
        .delegateGroupDetails(getDelegateGroupDetails(accountId, orgId, projectId, false, delegateTokenName))
        .build();
  }

  private List<String> getDelegateGroupIds(String accountId, String orgId, String projectId,
      DelegateFilterPropertiesDTO filterProperties, String searchTerm) {
    Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class)
                                                  .filter(DelegateGroupKeys.accountId, accountId)
                                                  .filter(DelegateGroupKeys.ng, true);
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    if (owner != null) {
      delegateGroupQuery.filter(DelegateGroupKeys.owner, owner);
    } else {
      // Account level delegates
      delegateGroupQuery.field(DelegateGroupKeys.owner).doesNotExist();
    }

    if (isNotEmpty(searchTerm)) {
      Criteria criteria =
          delegateGroupQuery.or(delegateGroupQuery.criteria(DelegateGroupKeys.name).contains(searchTerm),
              delegateGroupQuery.criteria(DelegateGroupKeys.description).contains(searchTerm),
              delegateGroupQuery.criteria(DelegateGroupKeys.identifier).contains(searchTerm),
              delegateGroupQuery.criteria(DelegateGroupKeys.tags).contains(searchTerm));
      delegateGroupQuery.and(criteria);
    }

    if (filterProperties != null) {
      if (isNotEmpty(filterProperties.getDelegateGroupIdentifier())) {
        delegateGroupQuery.field(DelegateGroupKeys.identifier).contains(filterProperties.getDelegateGroupIdentifier());
      }

      if (isNotEmpty(filterProperties.getDelegateName())) {
        delegateGroupQuery.field(DelegateGroupKeys.name).contains(filterProperties.getDelegateName());
      }

      if (isNotEmpty(filterProperties.getDelegateType())) {
        delegateGroupQuery.field(DelegateGroupKeys.delegateType).contains(filterProperties.getDelegateType());
      }

      if (isNotEmpty(filterProperties.getDescription())) {
        delegateGroupQuery.field(DelegateGroupKeys.description).contains(filterProperties.getDescription());
      }

      if (isNotEmpty(filterProperties.getDelegateTags())) {
        delegateGroupQuery.field(DelegateGroupKeys.tags).hasAllOf(filterProperties.getDelegateTags());
      }
    }
    return delegateGroupQuery.field(DelegateGroupKeys.status)
        .notEqual(DelegateGroupStatus.DELETED)
        .asKeyList()
        .stream()
        .map(key -> (String) key.getId())
        .collect(toList());
  }

  @Override
  public DelegateGroup updateDelegateGroupTags_old(
      String accountId, String orgId, String projectId, String delegateGroupName, Set<String> tags) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    log.warn("Using a deprecated api for updating delegate group tags.");

    Query<DelegateGroup> updateQuery = persistence.createQuery(DelegateGroup.class)
                                           .filter(DelegateGroupKeys.accountId, accountId)
                                           .filter(DelegateGroupKeys.name, delegateGroupName)
                                           .filter(DelegateGroupKeys.owner, owner)
                                           .filter(DelegateGroupKeys.ng, true);

    final UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
    setUnset(updateOperations, DelegateGroupKeys.tags, tags);

    DelegateGroup updatedDelegateGroup =
        persistence.findAndModify(updateQuery, updateOperations, HPersistence.returnNewOptions);

    outboxService.save(DelegateUpsertEvent.builder()
                           .accountIdentifier(accountId)
                           .orgIdentifier(orgId)
                           .projectIdentifier(projectId)
                           .delegateGroupIdentifier(updatedDelegateGroup.getIdentifier())
                           .delegateSetupDetails(DelegateSetupDetails.builder()
                                                     .identifier(updatedDelegateGroup.getIdentifier())
                                                     .tags(updatedDelegateGroup.getTags())
                                                     .build())
                           .build());
    log.info("Updating tags for delegate group: {} tags:{}", delegateGroupName, String.valueOf(tags.toString()));
    return updatedDelegateGroup;
  }

  @Override
  public Optional<DelegateGroupDTO> listDelegateGroupTags(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String groupIdentifier) {
    try {
      DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
      DelegateGroup delegateGroup =
          getDelegateGroupByAccountAndOwnerAndIdentifier(accountIdentifier, owner, groupIdentifier);
      return Optional.of(DelegateGroupDTO.convertToDTO(delegateGroup, listDelegateGroupImplicitTags(delegateGroup)));
    } catch (Exception e) {
      log.error("Error occurred during fetching list of delegate group tags", e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<DelegateGroupDTO> addDelegateGroupTags(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String groupIdentifier, DelegateGroupTags delegateGroupTags) {
    try {
      DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
      DelegateGroup delegateGroup =
          getDelegateGroupByAccountAndOwnerAndIdentifier(accountIdentifier, owner, groupIdentifier);
      Set<String> existingTags = delegateGroup.getTags();
      if (isNotEmpty(existingTags)) {
        existingTags.addAll(delegateGroupTags.getTags());
      }
      return updateDelegateGroupTags(accountIdentifier, orgIdentifier, projectIdentifier, groupIdentifier,
          isNotEmpty(existingTags) ? new DelegateGroupTags(existingTags)
                                   : new DelegateGroupTags(delegateGroupTags.getTags()));
    } catch (Exception e) {
      log.error("Error occurred during adding delegate group tags", e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<DelegateGroupDTO> updateDelegateGroupTags(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String groupIdentifier, DelegateGroupTags delegateGroupTags) {
    try {
      DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);

      Query<DelegateGroup> updateQuery = persistence.createQuery(DelegateGroup.class)
                                             .filter(DelegateGroupKeys.accountId, accountIdentifier)
                                             .filter(DelegateGroupKeys.identifier, groupIdentifier)
                                             .filter(DelegateGroupKeys.owner, owner)
                                             .filter(DelegateGroupKeys.ng, true);

      final UpdateOperations<DelegateGroup> updateOperations = persistence.createUpdateOperations(DelegateGroup.class);
      setUnset(updateOperations, DelegateGroupKeys.tags, delegateGroupTags.getTags());

      DelegateGroup updatedDelegateGroup =
          persistence.findAndModify(updateQuery, updateOperations, HPersistence.returnNewOptions);

      outboxService.save(DelegateUpsertEvent.builder()
                             .accountIdentifier(accountIdentifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .delegateGroupIdentifier(updatedDelegateGroup.getIdentifier())
                             .delegateSetupDetails(DelegateSetupDetails.builder()
                                                       .identifier(updatedDelegateGroup.getIdentifier())
                                                       .tags(updatedDelegateGroup.getTags())
                                                       .build())
                             .build());
      log.info("Updating tags for delegate group: {} tags: {}", groupIdentifier, delegateGroupTags.getTags());
      return Optional.of(DelegateGroupDTO.convertToDTO(updatedDelegateGroup, null));
    } catch (Exception e) {
      log.error("Error occurred during updating delegate group tags", e);
      return Optional.empty();
    }
  }

  @Override
  public DelegateGroup getDelegateGroup(String accountId, String delegateGroupId) {
    if (isEmpty(accountId) || isEmpty(delegateGroupId)) {
      return null;
    }
    return persistence.createQuery(DelegateGroup.class)
        .filter(DelegateGroupKeys.accountId, accountId)
        .filter(DelegateGroupKeys.uuid, delegateGroupId)
        .get();
  }

  @Override
  public void deleteDelegateGroupsOnDeletingOwner(String accountId, DelegateEntityOwner owner) {
    Query<DelegateGroup> query = persistence.createQuery(DelegateGroup.class)
                                     .filter(DelegateGroupKeys.accountId, accountId)
                                     .filter(DelegateGroupKeys.ng, true)
                                     .filter(DelegateGroupKeys.owner, owner);

    persistence.delete(query);
  }

  @Override
  public List<DelegateGroupDTO> listDelegateGroupsHavingTags(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, DelegateGroupTags tags) {
    DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgIdentifier, projectIdentifier);
    List<DelegateGroup> allDelegateGroupList = persistence.createQuery(DelegateGroup.class)
                                                   .filter(DelegateGroupKeys.accountId, accountIdentifier)
                                                   .filter(DelegateGroupKeys.ng, true)
                                                   .filter(DelegateGroupKeys.owner, owner)
                                                   .asList();
    return allDelegateGroupList.stream()
        .filter(delegateGroup -> checkForDelegateGroupsHavingAllTags(delegateGroup, tags))
        .map(
            delegateGroup -> DelegateGroupDTO.convertToDTO(delegateGroup, listDelegateGroupImplicitTags(delegateGroup)))
        .collect(Collectors.toList());
  }

  @Override
  public List<String> listDelegateImplicitSelectors(Delegate delegate) {
    List<String> delegateImplicitSelectors = new ArrayList<>();

    if (isNotEmpty(delegate.getDelegateGroupId())) {
      DelegateGroup delegateGroup =
          delegateCache.getDelegateGroup(delegate.getAccountId(), delegate.getDelegateGroupId());

      if (delegateGroup != null) {
        delegateImplicitSelectors.add(delegateGroup.getName().toLowerCase());

        if (isNotEmpty(delegateGroup.getTags())) {
          delegateImplicitSelectors.addAll(delegateGroup.getTags());
        }
      }
    } else if (isNotEmpty(delegate.getHostName())) {
      delegateImplicitSelectors.add(delegate.getHostName().toLowerCase());
    }

    if (isNotEmpty(delegate.getDelegateName())) {
      delegateImplicitSelectors.add(delegate.getDelegateName().toLowerCase());
    }

    DelegateProfile delegateProfile =
        delegateCache.getDelegateProfile(delegate.getAccountId(), delegate.getDelegateProfileId());

    if (delegateProfile != null && isNotEmpty(delegateProfile.getName())) {
      delegateImplicitSelectors.add(delegateProfile.getName().toLowerCase());
    }

    if (delegateProfile != null && isNotEmpty(delegateProfile.getSelectors())) {
      delegateImplicitSelectors.addAll(delegateProfile.getSelectors());
    }

    return delegateImplicitSelectors;
  }

  @Override
  public AutoUpgrade setAutoUpgrade(long upgraderLastUpdated, boolean immutableDelegate, long delegateCreationTime,
      String version, String delegateType) {
    if (DOCKER.equals(delegateType)) {
      return AutoUpgrade.OFF;
    }

    // version can be empty in case of delegateGroup with no delegates.
    if (isNotEmpty(version)) {
      try {
        String[] split = version.split("\\.");
        if (Integer.parseInt(split[2]) < 76300) {
          return AutoUpgrade.OFF;
        }
      } catch (NumberFormatException ex) {
        log.error("Unable to parse delegate version ", ex);
      } catch (IndexOutOfBoundsException ex) {
        // This exception comes in local development because version is set to build.version.
        // Not adding exception because that will pollute logs.
        log.warn("Version is not set properly");
      }
    }

    // Auto Upgrade is on for legacy delegates.
    if (!immutableDelegate) {
      return AutoUpgrade.ON;
    }

    if (TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - upgraderLastUpdated)
        <= AUTO_UPGRADE_CHECK_TIME_IN_MINUTES) {
      return AutoUpgrade.ON;
    } else if (TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - delegateCreationTime)
        <= AUTO_UPGRADE_CHECK_TIME_IN_MINUTES) {
      return AutoUpgrade.DETECTING;
    }
    return AutoUpgrade.OFF;
  }

  @Override
  public void updateDelegateGroupValidity(@NotNull String accountId, @NotNull String delegateGroupId) {
    try {
      Query<DelegateGroup> delegateGroupQuery = persistence.createQuery(DelegateGroup.class)
                                                    .filter(DelegateGroupKeys.accountId, accountId)
                                                    .filter(DelegateGroupKeys.uuid, delegateGroupId);
      UpdateOperations<DelegateGroup> updateOperations =
          persistence.createUpdateOperations(DelegateGroup.class)
              .set(DelegateGroupKeys.validUntil,
                  Date.from(OffsetDateTime.now().plusDays(DelegateGroup.TTL.toDays()).toInstant()));
      persistence.update(delegateGroupQuery, updateOperations);
    } catch (Exception e) {
      log.info("Exception occurred while updating delegate group validity.", e);
    }
  }

  private boolean checkForDelegateGroupsHavingAllTags(DelegateGroup delegateGroup, DelegateGroupTags tags) {
    Set<String> delegateGroupTags = listDelegateGroupImplicitTags(delegateGroup);
    if (isNotEmpty(delegateGroup.getTags())) {
      delegateGroupTags.addAll(delegateGroup.getTags());
    }
    return delegateGroupTags.containsAll(tags.getTags());
  }

  private DelegateGroup getDelegateGroupByAccountAndOwnerAndIdentifier(
      String accountId, DelegateEntityOwner owner, String delegateGroupIdentifier) {
    if (isEmpty(accountId) || isEmpty(delegateGroupIdentifier)) {
      return null;
    }
    return persistence.createQuery(DelegateGroup.class)
        .filter(DelegateGroupKeys.accountId, accountId)
        .filter(DelegateGroupKeys.owner, owner)
        .filter(DelegateGroupKeys.identifier, delegateGroupIdentifier)
        .get();
  }

  private Map<String, Boolean> isDelegateTokenActive(String accountId, List<String> tokensNameList) {
    Map<String, Boolean> delegateTokenStatusMap = new HashMap<>();
    List<DelegateToken> delegateTokens = persistence.createQuery(DelegateToken.class)
                                             .filter(DelegateTokenKeys.accountId, accountId)
                                             .field(DelegateTokenKeys.name)
                                             .in(tokensNameList)
                                             .project(DelegateTokenKeys.name, true)
                                             .project(DelegateTokenKeys.status, true)
                                             .asList();
    delegateTokens.forEach(delegateToken
        -> delegateTokenStatusMap.put(
            delegateToken.getName(), DelegateTokenStatus.ACTIVE.equals(delegateToken.getStatus())));
    return delegateTokenStatusMap;
  }

  private Set<String> listDelegateGroupImplicitTags(final DelegateGroup delegateGroup) {
    Set<String> implicitTags = new HashSet<>();
    if (delegateGroup == null) {
      return implicitTags;
    }
    implicitTags.add(delegateGroup.getName().toLowerCase());
    final DelegateProfile delegateProfile =
        delegateCache.getDelegateProfile(delegateGroup.getAccountId(), delegateGroup.getDelegateConfigurationId());

    if (delegateProfile != null && isNotEmpty(delegateProfile.getName())) {
      implicitTags.add(delegateProfile.getName().toLowerCase());
    }

    if (delegateProfile != null && isNotEmpty(delegateProfile.getSelectors())) {
      for (final String selector : delegateProfile.getSelectors()) {
        implicitTags.add(selector.toLowerCase());
      }
    }
    return implicitTags;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    persistence.delete(persistence.createQuery(DelegateGroup.class).filter(DelegateGroupKeys.accountId, accountId));
  }
}
