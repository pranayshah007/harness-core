/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.NO_APPS_ASSIGNED;
import static io.harness.persistence.CreatedAtAware.CREATED_AT_KEY;

import static software.wings.beans.EntityType.APPLICATION;
import static software.wings.beans.EntityType.ARTIFACT;
import static software.wings.beans.instance.dashboard.InstanceSummaryStats.Builder.anInstanceSummaryStats;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static dev.morphia.query.Sort.ascending;
import static dev.morphia.query.Sort.descending;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.SetUtils.emptyIfNull;

import io.harness.beans.EnvironmentType;
import io.harness.beans.PageResponse;
import io.harness.exception.GeneralException;
import io.harness.exception.NoResultFoundException;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.beans.infrastructure.instance.InvocationCount;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountFields;
import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.ServerlessInstance;
import software.wings.beans.infrastructure.instance.ServerlessInstance.ServerlessInstanceKeys;
import software.wings.beans.infrastructure.instance.SyncStatus;
import software.wings.beans.infrastructure.instance.info.ServerlessInstanceInfo.ServerlessInstanceInfoKeys;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.instance.dashboard.ArtifactSummary.ArtifactSummaryBuilder;
import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.beans.instance.dashboard.EntitySummaryStats;
import software.wings.beans.instance.dashboard.EnvironmentSummary;
import software.wings.beans.instance.dashboard.EnvironmentSummary.EnvironmentSummaryBuilder;
import software.wings.beans.instance.dashboard.InstanceStats;
import software.wings.beans.instance.dashboard.InstanceStatsByArtifact;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment.InstanceStatsByEnvironmentBuilder;
import software.wings.beans.instance.dashboard.InstanceSummaryStats;
import software.wings.beans.instance.dashboard.InstanceSummaryStatsByService;
import software.wings.beans.instance.dashboard.ServiceSummary;
import software.wings.dl.WingsPersistence;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.ServerlessDashboardService;
import software.wings.service.intfc.instance.ServerlessInstanceService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.aggregation.Group;
import dev.morphia.annotations.Id;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@Singleton
@Slf4j
public class ServerlessDashboardServiceImpl implements ServerlessDashboardService {
  public static final String SERVERLESS_FUNCTION_INVOCATION = "SERVERLESS_FUNCTION_INVOCATION";
  public static final String SERVICE_ID = "serviceId";
  public static final String COUNT = "count";
  public static final String $_FIRST = "$first";
  public static final String APP_ID = "appId";
  public static final String IS_DELETED = "isDeleted";
  public static final String DELETED_AT = "deletedAt";
  public static final String ACCOUNT_ID = "accountId";
  private final String invocationCountFieldPath =
      String.join(".", ServerlessInstanceKeys.instanceInfo, ServerlessInstanceInfoKeys.invocationCountMap,
          InvocationCountKey.LAST_30_DAYS.name(), InvocationCountFields.count);
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserService userService;
  @Inject private AppService appService;
  @Inject private ServerlessInstanceService serverlessInstanceService;

  @Override
  public InstanceSummaryStats getAppInstanceSummaryStats(
      String accountId, List<String> appIds, List<String> groupByEntityTypes, long timestamp) {
    Query<ServerlessInstance> query;
    try {
      query = getServerlessInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException nrfe) {
      return anInstanceSummaryStats().totalCount(0).countMap(null).build();
    } catch (Exception e) {
      log.error("Error while creating query for getting app instance summary stats");
      return anInstanceSummaryStats().totalCount(0).countMap(null).build();
    }

    long serverlessInstanceCount = getServerlessInstanceCount(query);

    Map<String, List<EntitySummaryStats>> instanceSummaryMap = new HashMap<>();
    for (String groupByEntityType : groupByEntityTypes) {
      String entityNameColumn;
      String entityIdColumn;
      List<EntitySummaryStats> entitySummaryStatsList;
      if (EntityType.SERVICE.name().equals(groupByEntityType)) {
        entityIdColumn = SERVICE_ID;
        entityNameColumn = "serviceName";
        entitySummaryStatsList = getEntitySummaryStats(entityIdColumn, entityNameColumn, groupByEntityType, query, 1);
      } else if (SettingCategory.CLOUD_PROVIDER.name().equals(groupByEntityType)) {
        entityIdColumn = "computeProviderId";
        entityNameColumn = "computeProviderName";
        entitySummaryStatsList = getEntitySummaryStats(entityIdColumn, entityNameColumn, groupByEntityType, query, 1);
      } else if (SERVERLESS_FUNCTION_INVOCATION.equals(groupByEntityType)) {
        entityIdColumn = ServerlessInstanceKeys.serviceId;
        entityNameColumn = ServerlessInstanceKeys.serviceName;
        entitySummaryStatsList = getEntitySummaryStats(
            entityIdColumn, entityNameColumn, groupByEntityType, query, "$" + invocationCountFieldPath);
      } else {
        throw new GeneralException("Unsupported groupBy entity type:" + groupByEntityType);
      }
      instanceSummaryMap.put(groupByEntityType, entitySummaryStatsList);
    }

    return anInstanceSummaryStats().countMap(instanceSummaryMap).totalCount(serverlessInstanceCount).build();
  }

  @Override
  public PageResponse<InstanceSummaryStatsByService> getAppInstanceSummaryStatsByService(
      String accountId, List<String> appIds, long timestamp, int offset, int limit) {
    Query<ServerlessInstance> query;
    try {
      query = getServerlessInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException nre) {
      return getEmptyPageResponse();
    } catch (Exception e) {
      log.error("Error while compiling query for instance stats by service");
      return getEmptyPageResponse();
    }

    List<ServiceInstanceCount> instanceInfoList = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(ServerlessInstance.class)
            .match(query)
            .group(Group.id(grouping(SERVICE_ID)), grouping(COUNT, accumulator("$sum", 1)),
                grouping("appInfo", grouping($_FIRST, projection("id", APP_ID), projection("name", "appName"))),
                grouping(
                    "serviceInfo", grouping($_FIRST, projection("id", SERVICE_ID), projection("name", "serviceName"))),
                grouping("envTypeList", grouping("$push", projection("type", "envType"))),
                grouping("invocationCount", accumulator("$sum", invocationCountFieldPath)));
    aggregationPipeline.skip(offset);
    aggregationPipeline.limit(limit);

    Iterator<ServiceInstanceCount> aggregate = HPersistence.retry(
        ()
            -> aggregationPipeline.aggregate(ServiceInstanceCount.class,
                AggregationOptions.builder()
                    .maxTime(wingsPersistence.getMaxTimeMs(ServerlessInstance.class), TimeUnit.MILLISECONDS)
                    .build()));
    aggregate.forEachRemaining(instanceInfoList::add);
    return constructInstanceSummaryStatsByService(instanceInfoList, offset, limit);
  }

  @Override
  public List<InstanceStatsByEnvironment> getServiceInstances(String accountId, String serviceId, long timestamp) {
    Query<ServerlessInstance> query;
    try {
      query = getInstanceQueryAtTime(accountId, serviceId, timestamp);
    } catch (Exception e) {
      log.error("Error while compiling query for instance stats by service");
      return emptyList();
    }

    List<ServiceAggregationInfo> serviceAggregationInfoList = new ArrayList<>();

    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(ServerlessInstance.class)
            .match(query)
            .group(Group.id(grouping("envId"), grouping("lastArtifactId")), grouping(COUNT, accumulator("$sum", 1)),
                grouping("appInfo", grouping($_FIRST, projection("id", APP_ID), projection("name", "appName"))),
                grouping("envInfo",
                    grouping($_FIRST, projection("id", "envId"), projection("name", "envName"),
                        projection("type", "envType"))),
                grouping("artifactInfo",
                    grouping($_FIRST, projection("id", "lastArtifactId"), projection("name", "lastArtifactName"),
                        projection("buildNo", "lastArtifactBuildNum"), projection("streamId", "lastArtifactStreamId"),
                        projection("deployedAt", "lastDeployedAt"),
                        projection("sourceName", "lastArtifactSourceName"))),
                grouping(
                    "instanceInfoList", grouping("$addToSet", projection("id", "_id"), projection("name", "hostName"))),
                grouping("invocationCount", accumulator("$sum", invocationCountFieldPath)))
            .sort(ascending("_id.envId"), descending(COUNT));
    int limit = wingsPersistence.getMaxDocumentLimit(query.getEntityClass());
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(ServiceAggregationInfo.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(ServerlessInstance.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(serviceAggregationInfoList::add);
    return constructInstanceStatsForService(serviceId, serviceAggregationInfoList);
  }

  @Override
  public ServerlessInstance getInstanceDetails(String instanceId) {
    return serverlessInstanceService.get(instanceId);
  }

  @VisibleForTesting
  List<InstanceStatsByEnvironment> constructInstanceStatsForService(
      String serviceId, List<ServiceAggregationInfo> serviceAggregationInfoList) {
    if (isEmpty(serviceAggregationInfoList)) {
      return emptyList();
    }

    String appId = serviceAggregationInfoList.get(0).getAppInfo().getId();

    InstanceStatsByEnvironment currentEnv = null;
    InstanceStatsByArtifact currentArtifact;

    List<InstanceStatsByEnvironment> currentEnvList = Lists.newArrayList();
    List<InstanceStatsByArtifact> currentArtifactList = Lists.newArrayList();

    for (ServiceAggregationInfo serviceAggregationInfo : serviceAggregationInfoList) {
      int size = serviceAggregationInfo.getInstanceInfoList().size();
      List<EntitySummary> instanceList = ListUtils.emptyIfNull(serviceAggregationInfo.getInstanceInfoList())
                                             .stream()
                                             .map(instanceSummary
                                                 -> EntitySummary.builder()
                                                        .name(instanceSummary.getName())
                                                        .id(instanceSummary.getId())
                                                        .type(EntityType.SERVERLESS_INSTANCE.name())
                                                        .build())
                                             .collect(Collectors.toList());

      InstanceStats instanceStats = InstanceStats.Builder.anInstanceSummaryStats()
                                        .withEntitySummaryList(instanceList)
                                        .withTotalCount(size)
                                        .withInvocationCount(InvocationCount.builder()
                                                                 .count(serviceAggregationInfo.invocationCount)
                                                                 .key(InvocationCountKey.LAST_30_DAYS)
                                                                 .build())
                                        .build();

      if (currentEnv == null || !compareEnvironment(currentEnv, serviceAggregationInfo.getEnvInfo())) {
        currentArtifactList = Lists.newArrayList();
        currentEnv = getServerlessInstanceStatsByEnvironment(
            appId, serviceId, serviceAggregationInfo.getEnvInfo(), currentArtifactList);
        currentEnvList.add(currentEnv);
      }

      currentArtifact = getInstanceStatsByArtifact(serviceAggregationInfo.getArtifactInfo(), instanceStats);
      currentArtifactList.add(currentArtifact);
    }

    return currentEnvList;
  }

  @VisibleForTesting
  InstanceStatsByArtifact getInstanceStatsByArtifact(ArtifactInfo artifactInfo, InstanceStats instanceStats) {
    ArtifactSummaryBuilder builder = ArtifactSummary.builder();
    builder.buildNo(artifactInfo.buildNo)
        .artifactSourceName(artifactInfo.sourceName)
        .name(artifactInfo.getName())
        .type(ARTIFACT.name())
        .id(artifactInfo.getId());
    ArtifactSummary artifactSummary = builder.build();

    InstanceStatsByArtifact.Builder artifactBuilder = InstanceStatsByArtifact.Builder.anInstanceStatsByArtifact();
    artifactBuilder.withEntitySummary(artifactSummary);
    artifactBuilder.withInstanceStats(instanceStats);
    return artifactBuilder.build();
  }

  @VisibleForTesting
  InstanceStatsByEnvironment getServerlessInstanceStatsByEnvironment(
      String appId, String serviceId, EnvInfo envInfo, List<InstanceStatsByArtifact> currentArtifactList) {
    EnvironmentSummaryBuilder builder = EnvironmentSummary.builder();
    builder.prod("PROD".equals(envInfo.getType()))
        .id(envInfo.getId())
        .type(EntityType.ENVIRONMENT.name())
        .name(envInfo.getName());
    InstanceStatsByEnvironmentBuilder instanceStatsByEnvironmentBuilder =
        InstanceStatsByEnvironment.builder()
            .environmentSummary(builder.build())
            .instanceStatsByArtifactList(currentArtifactList);

    List<SyncStatus> syncStatusList = serverlessInstanceService.getSyncStatus(appId, serviceId, envInfo.getId());
    if (isNotEmpty(syncStatusList)) {
      boolean hasSyncIssues = hasSyncIssues(syncStatusList);
      instanceStatsByEnvironmentBuilder.infraMappingSyncStatusList(syncStatusList);
      instanceStatsByEnvironmentBuilder.hasSyncIssues(hasSyncIssues);
    }

    return instanceStatsByEnvironmentBuilder.build();
  }

  private boolean hasSyncIssues(List<SyncStatus> syncStatusList) {
    return syncStatusList.stream().anyMatch(
        syncStatus -> syncStatus.getLastSyncedAt() != syncStatus.getLastSuccessfullySyncedAt());
  }

  private boolean compareEnvironment(InstanceStatsByEnvironment currentEnv, EnvInfo envInfo) {
    return currentEnv != null && envInfo != null && envInfo.getId().equals(currentEnv.getEnvironmentSummary().getId());
  }

  @VisibleForTesting
  Query<ServerlessInstance> getInstanceQueryAtTime(String accountId, String serviceId, long timestamp) {
    Query<ServerlessInstance> query;
    if (timestamp > 0) {
      query = getInstanceQuery(accountId, serviceId, true);
      query.field(ServerlessInstance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria(IS_DELETED).equal(false), query.criteria(DELETED_AT).greaterThanOrEq(timestamp)));
    } else {
      query = getInstanceQuery(accountId, serviceId, false);
    }
    return query;
  }

  private Query<ServerlessInstance> getInstanceQuery(String accountId, String serviceId, boolean includeDeleted) {
    Query query = wingsPersistence.createQuery(ServerlessInstance.class);
    if (!includeDeleted) {
      query.filter(IS_DELETED, false);
    }
    query.filter(ACCOUNT_ID, accountId);
    query.filter(SERVICE_ID, serviceId);
    return query;
  }

  private PageResponse getEmptyPageResponse() {
    return aPageResponse().withResponse(emptyList()).build();
  }

  @VisibleForTesting
  PageResponse<InstanceSummaryStatsByService> constructInstanceSummaryStatsByService(
      List<ServiceInstanceCount> serviceInstanceCountList, int offset, int limit) {
    List<InstanceSummaryStatsByService> instanceSummaryStatsByServiceList =
        serviceInstanceCountList.stream().map(this::getInstanceSummaryStatsByService).collect(toList());
    return aPageResponse()
        .withResponse(instanceSummaryStatsByServiceList)
        .withOffset(Integer.toString(offset))
        .withLimit(Integer.toString(limit))
        .build();
  }

  @VisibleForTesting
  InstanceSummaryStatsByService getInstanceSummaryStatsByService(ServiceInstanceCount serviceInstanceCount) {
    EntitySummary appInfo = serviceInstanceCount.getAppInfo();
    EntitySummary appSummary =
        EntitySummary.builder().name(appInfo.getName()).id(appInfo.getId()).type(APPLICATION.name()).build();

    List<EnvType> envTypeList = ListUtils.emptyIfNull(serviceInstanceCount.envTypeList);
    long prodCount =
        envTypeList.stream().filter(envType -> EnvironmentType.PROD.name().equals(envType.getType())).count();

    long nonprodCount = envTypeList.size() - prodCount;

    EntitySummary serviceInfo = serviceInstanceCount.getServiceInfo();
    return InstanceSummaryStatsByService.builder()
        .serviceSummary(ServiceSummary.builder()
                            .appSummary(appSummary)
                            .id(serviceInfo.getId())
                            .type(EntityType.SERVICE.name())
                            .name(serviceInfo.getName())
                            .build())
        .totalCount(serviceInstanceCount.getCount())
        .prodCount(prodCount)
        .nonprodCount(nonprodCount)
        .invocationCount(InvocationCount.builder()
                             .count(serviceInstanceCount.invocationCount)
                             .key(InvocationCountKey.LAST_30_DAYS)
                             .build())
        .build();
  }

  @VisibleForTesting
  List<EntitySummaryStats> getEntitySummaryStats(String entityIdColumn, String entityNameColumn,
      String groupByEntityType, Query<ServerlessInstance> query, Object accumulatorObject) {
    List<EntitySummaryStats> entitySummaryStatsList = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(ServerlessInstance.class)
            .match(query)
            .group(Group.id(grouping(entityIdColumn)), grouping(COUNT, accumulator("$sum", accumulatorObject)),
                grouping(entityNameColumn, grouping($_FIRST, entityNameColumn)))
            .project(projection("_id").suppress(), projection("entityId", "_id." + entityIdColumn),
                projection("entityName", entityNameColumn), projection(COUNT))
            .sort(descending(COUNT));
    int limit = wingsPersistence.getMaxDocumentLimit(query.getEntityClass());
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(FlatEntitySummaryStats.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(ServerlessInstance.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(flatEntitySummaryStats -> {
          EntitySummaryStats entitySummaryStats = getEntitySummaryStats(flatEntitySummaryStats, groupByEntityType);
          entitySummaryStatsList.add(entitySummaryStats);
        });
    return entitySummaryStatsList;
  }

  private EntitySummaryStats getEntitySummaryStats(FlatEntitySummaryStats flatEntitySummaryStats, String entityType) {
    EntitySummary entitySummary = EntitySummary.builder()
                                      .id(flatEntitySummaryStats.entityId)
                                      .name(flatEntitySummaryStats.entityName)
                                      .type(entityType)
                                      .build();
    return EntitySummaryStats.Builder.anEntitySummaryStats()
        .count(flatEntitySummaryStats.count)
        .entitySummary(entitySummary)
        .build();
  }

  @VisibleForTesting
  long getServerlessInstanceCount(Query<ServerlessInstance> query) {
    AtomicLong totalCount = new AtomicLong();
    AggregationPipeline aggregationPipeline = wingsPersistence.getDatastore(query.getEntityClass())
                                                  .createAggregation(ServerlessInstance.class)
                                                  .match(query)
                                                  .group("_id", grouping(COUNT, accumulator("$sum", 1)));
    int limit = wingsPersistence.getMaxDocumentLimit(query.getEntityClass());
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(InstanceCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(ServerlessInstance.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(instanceCount -> totalCount.addAndGet(instanceCount.getCount()));
    return totalCount.get();
  }

  @VisibleForTesting
  Query<ServerlessInstance> getServerlessInstanceQueryAtTime(String accountId, List<String> appIds, long timestamp) {
    Query<ServerlessInstance> query;
    if (timestamp > 0) {
      query = getInstanceQuery(accountId, appIds, true, timestamp);
      query.field(ServerlessInstance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria(IS_DELETED).equal(false), query.criteria(DELETED_AT).greaterThanOrEq(timestamp)));
    } else {
      query = getInstanceQuery(accountId, appIds, false, timestamp);
    }
    return query;
  }

  public Set<String> detectDeletedAppIds(String accountId, long timestamp) {
    Query<ServerlessInstance> query = wingsPersistence.createQuery(ServerlessInstance.class);
    query.project(APP_ID, true);
    List<ServerlessInstance> instancesForAccount = getInstancesForAccount(accountId, timestamp, query);
    Set<String> appIdsFromInstances =
        instancesForAccount.stream().map(ServerlessInstance::getAppId).collect(Collectors.toSet());

    List<Application> appsByAccountId = appService.getAppsByAccountId(accountId);
    Set<String> existingApps = appsByAccountId.stream().map(Application::getUuid).collect(Collectors.toSet());
    appIdsFromInstances.removeAll(existingApps);
    return appIdsFromInstances;
  }

  @VisibleForTesting
  List<ServerlessInstance> getInstancesForAccount(String accountId, long timestamp, Query<ServerlessInstance> query) {
    List<ServerlessInstance> instanceList = new ArrayList<>();
    query.field(ServerlessInstanceKeys.accountId).equal(accountId);
    if (timestamp > 0) {
      query.field(ServerlessInstance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria(IS_DELETED).equal(false), query.criteria(DELETED_AT).greaterThanOrEq(timestamp)));
    } else {
      query.filter(IS_DELETED, false);
    }

    int counter = 0;
    try (HIterator<ServerlessInstance> iterator = new HIterator<>(query.fetch())) {
      for (ServerlessInstance instance : iterator) {
        counter++;
        instanceList.add(instance);
      }
    }

    if (isNotEmpty(instanceList)) {
      HashSet<ServerlessInstance> instanceSet = new HashSet<>(instanceList);
      log.info("Instances reported {}, set count {}", counter, instanceSet.size());
    } else {
      log.info("Instances reported {}", counter);
    }
    return instanceList;
  }

  private UserRequestContext getUserContext() {
    User user = UserThreadLocal.get();
    if (user != null) {
      return user.getUserRequestContext();
    }
    return null;
  }

  private boolean isUserAccountAdmin(String accountId) {
    return userService.isAccountAdmin(accountId);
  }

  @VisibleForTesting
  Query<ServerlessInstance> getInstanceQuery(
      String accountId, List<String> appIds, boolean includeDeleted, long timestamp) {
    Query<ServerlessInstance> query = wingsPersistence.createQuery(ServerlessInstance.class);
    if (isNotEmpty(appIds)) {
      query.field(ServerlessInstanceKeys.appId).in(appIds);
    } else {
      UserRequestContext userRequestContext = getUserContext();
      if (userRequestContext != null) {
        if (userRequestContext.isAppIdFilterRequired()) {
          Set<String> finalAllowedAppIdSet = Sets.newHashSet();
          finalAllowedAppIdSet.addAll(emptyIfNull(userRequestContext.getAppIds()));

          if (includeDeleted && isUserAccountAdmin(accountId)) {
            Set<String> deletedAppIds = detectDeletedAppIds(accountId, timestamp);
            if (isNotEmpty(deletedAppIds)) {
              finalAllowedAppIdSet.addAll(deletedAppIds);
            }
          }

          if (isNotEmpty(finalAllowedAppIdSet)) {
            if (userRequestContext.getUserPermissionInfo().isHasAllAppAccess()) {
              query.filter(ACCOUNT_ID, accountId);
            } else {
              query.field(APP_ID).in(finalAllowedAppIdSet);
            }
          } else {
            throw NoResultFoundException.newBuilder().code(NO_APPS_ASSIGNED).build();
          }
        }
      } else {
        throw NoResultFoundException.newBuilder().code(NO_APPS_ASSIGNED).build();
      }
    }

    if (!includeDeleted) {
      query.filter(IS_DELETED, false);
    }

    return query;
  }

  @Override
  @Nonnull
  public List<ServerlessInstance> getAppInstancesForAccount(String accountId, long timestamp) {
    Query<ServerlessInstance> query = wingsPersistence.createQuery(ServerlessInstance.class);
    return getInstancesForAccount(accountId, timestamp, query);
  }

  @Override
  public Set<String> getDeletedAppIds(String accountId, long fromTimestamp, long toTimestamp) {
    Query<ServerlessInstance> query = wingsPersistence.createQuery(ServerlessInstance.class);
    query.project(APP_ID, true);
    query.project(ServerlessInstance.CREATED_AT_KEY, true);
    // Find the timestamp of oldest instance alive at fromTimestamp
    long lhsCreatedAt = getCreatedTimeOfInstanceAtTimestamp(accountId, fromTimestamp, query, true);
    // Find the timestamp of latest instance alive at toTimestamp
    long rhsCreatedAt = getCreatedTimeOfInstanceAtTimestamp(accountId, toTimestamp, query, false);

    query = wingsPersistence.createQuery(ServerlessInstance.class);
    query.field(ACCOUNT_ID).equal(accountId);
    query.field(ServerlessInstance.CREATED_AT_KEY).greaterThanOrEq(lhsCreatedAt);
    query.field(ServerlessInstance.CREATED_AT_KEY).lessThanOrEq(rhsCreatedAt);
    query.project(APP_ID, true);

    List<ServerlessInstance> instanceList = new ArrayList<>();
    try (HIterator<ServerlessInstance> iterator = new HIterator<>(query.fetch())) {
      for (ServerlessInstance instance : iterator) {
        instanceList.add(instance);
      }
    }

    Set<String> appIdsFromInstances =
        instanceList.stream().map(ServerlessInstance::getAppId).collect(Collectors.toSet());

    List<Application> appsByAccountId = appService.getAppsByAccountId(accountId);
    Set<String> existingApps = appsByAccountId.stream().map(Application::getUuid).collect(Collectors.toSet());
    appIdsFromInstances.removeAll(existingApps);
    return appIdsFromInstances;
  }

  @VisibleForTesting
  long getCreatedTimeOfInstanceAtTimestamp(
      String accountId, long timestamp, Query<ServerlessInstance> query, boolean oldest) {
    query.field(ACCOUNT_ID).equal(accountId);
    query.field(ServerlessInstance.CREATED_AT_KEY).lessThanOrEq(timestamp);
    query.and(query.or(query.criteria(IS_DELETED).equal(false), query.criteria(DELETED_AT).greaterThanOrEq(timestamp)));
    if (oldest) {
      query.order(Sort.ascending(CREATED_AT_KEY));
    } else {
      query.order(Sort.descending(CREATED_AT_KEY));
    }

    ServerlessInstance instance = query.get();
    if (instance == null) {
      return timestamp;
    }

    return instance.getCreatedAt();
  }

  @Data
  @NoArgsConstructor
  static class InstanceCount {
    private int count;
  }

  @Data
  @NoArgsConstructor
  public static class FlatEntitySummaryStats {
    private String entityId;
    private String entityName;
    private String entityVersion;
    private int count;
  }

  @Data
  @NoArgsConstructor
  public static final class ServiceInstanceCount {
    @Id private String serviceId;
    private long count;
    private List<EnvType> envTypeList;
    private EntitySummary appInfo;
    private EntitySummary serviceInfo;
    private int invocationCount;
  }
  @Data
  @NoArgsConstructor
  protected static final class ArtifactInfo {
    private String id;
    private String name;
    private String sourceName;
    private String buildNo;
    private long deployedAt;
    private String streamId;
    private String streamName;
  }
  @Data
  @NoArgsConstructor
  public static final class EnvType {
    private String type;
  }

  @Data
  @NoArgsConstructor
  public static final class ServiceAggregationInfo {
    @Id private ID _id;
    private EntitySummary appInfo;
    private EnvInfo envInfo;
    private int invocationCount;
    private List<EntitySummary> instanceInfoList;
    private ArtifactInfo artifactInfo;
    private EntitySummary infraMappingInfo;

    @Data
    @NoArgsConstructor
    public static final class ID {
      private String envId;
      private String lastArtifactId;
    }
  }
}
