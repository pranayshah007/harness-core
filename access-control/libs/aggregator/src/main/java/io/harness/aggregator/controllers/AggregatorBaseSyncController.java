/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.controllers;

import io.harness.accesscontrol.AccessControlEntity;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.persistence.RoleDBO;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.AggregatorConfiguration;
import io.harness.aggregator.DebeziumConfig;
import io.harness.aggregator.MongoOffsetBackingStore;
import io.harness.aggregator.consumers.AccessControlDebeziumChangeConsumer;
import io.harness.aggregator.consumers.ChangeConsumer;
import io.harness.aggregator.consumers.ChangeConsumerService;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.ResourceGroupChangeConsumerImpl;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.RoleAssignmentChangeConsumerImpl;
import io.harness.aggregator.consumers.RoleChangeConsumerImpl;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupChangeConsumerImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import io.debezium.serde.DebeziumSerdes;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public abstract class AggregatorBaseSyncController implements Runnable {
  private final Map<String, ChangeConsumer<? extends AccessControlEntity>> collectionToConsumerMap;
  protected final AggregatorConfiguration aggregatorConfiguration;
  protected final ExecutorService executorService;
  private final ChangeEventFailureHandler changeEventFailureHandler;
  private final AccessControlAdminService accessControlAdminService;
  private final PersistentLocker persistentLocker;
  private final AtomicLong hostSelectorIndex;

  protected static final String ACCESS_CONTROL_AGGREGATOR_LOCK = "ACCESS_CONTROL_AGGREGATOR_LOCK";
  private static final String MONGO_DB_CONNECTOR = "io.debezium.connector.mongodb.MongoDbConnector";
  private static final String CONNECTOR_NAME = "name";
  private static final String OFFSET_STORAGE = "offset.storage";
  private static final String OFFSET_STORAGE_FILE_FILENAME = "offset.storage.file.filename";
  private static final String OFFSET_STORAGE_COLLECTION = "offset.storage.topic";
  private static final String KEY_CONVERTER_SCHEMAS_ENABLE = "key.converter.schemas.enable";
  private static final String VALUE_CONVERTER_SCHEMAS_ENABLE = "value.converter.schemas.enable";
  private static final String OFFSET_FLUSH_INTERVAL_MS = "offset.flush.interval.ms";
  private static final String CONNECT_BACKOFF_INITIAL_DELAY_MS = "connect.backoff.initial.delay.ms";
  private static final String CONNECT_BACKOFF_MAX_DELAY_MS = "connect.backoff.max.delay.ms";
  private static final String CONNECT_MAX_ATTEMPTS = "connect.max.attempts";
  private static final String CONNECTOR_CLASS = "connector.class";
  private static final String MONGODB_HOSTS = "mongodb.hosts";
  private static final String MONGODB_HOSTS_DELIMITER = ",";
  private static final String MONGODB_NAME = "mongodb.name";
  private static final String MONGODB_USER = "mongodb.user";
  private static final String MONGODB_PASSWORD = "mongodb.password";
  private static final String MONGODB_SSL_ENABLED = "mongodb.ssl.enabled";
  private static final String DATABASE_INCLUDE_LIST = "database.include.list";
  private static final String COLLECTION_INCLUDE_LIST = "collection.include.list";
  private static final String TRANSFORMS = "transforms";
  private static final String TRANSFORMS_UNWRAP_TYPE = "transforms.unwrap.type";
  private static final String TRANSFORMS_UNWRAP_DROP_TOMBSTONES = "transforms.unwrap.drop.tombstones";
  private static final String TRANSFORMS_UNWRAP_ADD_HEADERS = "transforms.unwrap.add.headers";
  private static final String SNAPSHOT_FETCH_SIZE = "snapshot.fetch.size";
  private static final String DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE =
      "io.debezium.connector.mongodb.transforms.ExtractNewDocumentState";
  private static final String ROLE_ASSIGNMENTS = "roleassignments";
  private static final String ROLES = "roles";
  private static final String RESOURCE_GROUPS = "resourcegroups";
  private static final String USER_GROUPS = "usergroups";
  private static final String UNKNOWN_PROPERTIES_IGNORED = "unknown.properties.ignored";
  private static final String MAX_STREAM_BATCH_SIZE = "max.batch.size";

  public enum AggregatorJobType {
    PRIMARY,
    SECONDARY;
  }

  public AggregatorBaseSyncController(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      RoleRepository roleRepository, ResourceGroupRepository resourceGroupRepository,
      UserGroupRepository userGroupRepository, AggregatorConfiguration aggregatorConfiguration,
      PersistentLocker persistentLocker, ChangeEventFailureHandler changeEventFailureHandler,
      AggregatorJobType aggregatorJobType, ChangeConsumerService changeConsumerService,
      RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler,
      UserGroupCRUDEventHandler userGroupCRUDEventHandler, ScopeService scopeService,
      AccessControlAdminService accessControlAdminService) {
    ChangeConsumer<RoleAssignmentDBO> roleAssignmentChangeConsumer = new RoleAssignmentChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, changeConsumerService, roleAssignmentCRUDEventHandler);
    ChangeConsumer<RoleDBO> roleChangeConsumer = new RoleChangeConsumerImpl(
        aclRepository, roleAssignmentRepository, roleRepository, aggregatorJobType.name(), changeConsumerService);
    ChangeConsumer<ResourceGroupDBO> resourceGroupChangeConsumer = new ResourceGroupChangeConsumerImpl(aclRepository,
        roleAssignmentRepository, resourceGroupRepository, aggregatorJobType.name(), changeConsumerService);
    ChangeConsumer<UserGroupDBO> userGroupChangeConsumer =
        new UserGroupChangeConsumerImpl(aclRepository, roleAssignmentRepository, userGroupRepository,
            aggregatorJobType.name(), changeConsumerService, scopeService, userGroupCRUDEventHandler);
    collectionToConsumerMap = new HashMap<>();
    collectionToConsumerMap.put(ROLE_ASSIGNMENTS, roleAssignmentChangeConsumer);
    collectionToConsumerMap.put(ROLES, roleChangeConsumer);
    collectionToConsumerMap.put(RESOURCE_GROUPS, resourceGroupChangeConsumer);
    collectionToConsumerMap.put(USER_GROUPS, userGroupChangeConsumer);
    this.aggregatorConfiguration = aggregatorConfiguration;
    this.executorService = Executors.newFixedThreadPool(
        4, new ThreadFactoryBuilder().setNameFormat(String.format("aggregator-%s", aggregatorJobType) + "-%d").build());
    this.persistentLocker = persistentLocker;
    this.changeEventFailureHandler = changeEventFailureHandler;
    this.hostSelectorIndex = new AtomicLong(-1);
    this.accessControlAdminService = accessControlAdminService;
  }

  protected DebeziumEngine<ChangeEvent<String, String>> getEngine(
      DebeziumConfig debeziumConfig, AccessControlDebeziumChangeConsumer changeConsumer) {
    Properties props = new Properties();
    String offsetCollection = getOffsetStorageCollection();
    props.setProperty(CONNECTOR_NAME, debeziumConfig.getConnectorName());
    props.setProperty(OFFSET_STORAGE, MongoOffsetBackingStore.class.getName());
    props.setProperty(OFFSET_STORAGE_FILE_FILENAME, debeziumConfig.getOffsetStorageFileName());
    props.setProperty(OFFSET_STORAGE_COLLECTION, offsetCollection);
    props.setProperty(KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    props.setProperty(VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    props.setProperty(OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    props.setProperty(CONNECTOR_CLASS, MONGO_DB_CONNECTOR);
    String[] mongoDbHosts = debeziumConfig.getMongodbHosts().split(MONGODB_HOSTS_DELIMITER);
    int hostSelector = (int) (hostSelectorIndex.incrementAndGet() % mongoDbHosts.length);
    props.setProperty(MONGODB_HOSTS, mongoDbHosts[hostSelector]);
    props.setProperty(MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_PASSWORD, x));
    props.setProperty(CONNECT_BACKOFF_INITIAL_DELAY_MS, debeziumConfig.getConnectBackoffInitialDelayMillis());
    props.setProperty(CONNECT_BACKOFF_MAX_DELAY_MS, debeziumConfig.getConnectBackoffMaxDelayMillis());
    props.setProperty(CONNECT_MAX_ATTEMPTS, debeziumConfig.getConnectMaxAttempts());
    props.setProperty(MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    props.setProperty(DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    props.setProperty(COLLECTION_INCLUDE_LIST, debeziumConfig.getCollectionIncludeList());
    props.setProperty(TRANSFORMS, "unwrap");
    props.setProperty(TRANSFORMS_UNWRAP_TYPE, DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    props.setProperty(TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    props.setProperty(TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    props.setProperty(SNAPSHOT_FETCH_SIZE, debeziumConfig.getSnapshotFetchSize());
    props.setProperty(MAX_STREAM_BATCH_SIZE, debeziumConfig.getMaxStreamBatchSize());

    return DebeziumEngine.create(Json.class).using(props).notifying(changeConsumer).build();
  }

  protected AcquiredLock<?> acquireLock(boolean retryIndefinitely) throws InterruptedException {
    AcquiredLock<?> aggregatorLock = null;
    String lockIdentifier = getLockName();
    do {
      try {
        log.info("Trying to acquire {} lock with 5 seconds timeout", lockIdentifier);
        aggregatorLock =
            persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(lockIdentifier, Duration.ofSeconds(5));
      } catch (Exception ex) {
        log.warn("Unable to get {} lock, due to the exception. Will retry again", lockIdentifier, ex);
      }
      if (aggregatorLock == null) {
        TimeUnit.SECONDS.sleep(120);
      }
    } while (aggregatorLock == null && retryIndefinitely);
    return aggregatorLock;
  }

  protected AccessControlDebeziumChangeConsumer buildDebeziumChangeConsumer() {
    Map<String, Deserializer<? extends AccessControlEntity>> collectionToDeserializerMap = new HashMap<>();
    // configuring id deserializer
    Serde<String> idSerde = DebeziumSerdes.payloadJson(String.class);
    idSerde.configure(Maps.newHashMap(ImmutableMap.of("from.field", "id")), true);
    Deserializer<String> idDeserializer = idSerde.deserializer();

    Map<String, String> valueDeserializerConfig = Maps.newHashMap(ImmutableMap.of(UNKNOWN_PROPERTIES_IGNORED, "true"));

    // configuring role assignment deserializer
    Serde<RoleAssignmentDBO> roleAssignmentSerde = DebeziumSerdes.payloadJson(RoleAssignmentDBO.class);
    roleAssignmentSerde.configure(valueDeserializerConfig, false);
    collectionToDeserializerMap.put(ROLE_ASSIGNMENTS, roleAssignmentSerde.deserializer());

    // configuring role deserializer
    Serde<RoleDBO> roleSerde = DebeziumSerdes.payloadJson(RoleDBO.class);
    roleSerde.configure(valueDeserializerConfig, false);
    collectionToDeserializerMap.put(ROLES, roleSerde.deserializer());

    // configuring resource group deserializer
    Serde<ResourceGroupDBO> resourceGroupSerde = DebeziumSerdes.payloadJson(ResourceGroupDBO.class);
    resourceGroupSerde.configure(valueDeserializerConfig, false);
    collectionToDeserializerMap.put(RESOURCE_GROUPS, resourceGroupSerde.deserializer());

    // configuring resource group deserializer
    Serde<UserGroupDBO> userGroupSerde = DebeziumSerdes.payloadJson(UserGroupDBO.class);
    userGroupSerde.configure(valueDeserializerConfig, false);
    collectionToDeserializerMap.put(USER_GROUPS, userGroupSerde.deserializer());

    // configuring debezium
    return new AccessControlDebeziumChangeConsumer(idDeserializer, collectionToDeserializerMap, collectionToConsumerMap,
        changeEventFailureHandler, accessControlAdminService);
  }

  public abstract String getLockName();

  protected abstract String getOffsetStorageCollection();
}
