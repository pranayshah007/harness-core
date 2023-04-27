/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.controllers;

import static io.harness.aggregator.AggregatorConfiguration.ACCESS_CONTROL_SERVICE;
import static io.harness.aggregator.models.MongoReconciliationOffset.SECONDARY_COLLECTION;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.aggregator.api.SecondarySyncStatus;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.AggregatorConfiguration;
import io.harness.aggregator.consumers.AccessControlDebeziumChangeConsumer;
import io.harness.aggregator.consumers.ChangeConsumerService;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.aggregator.models.AggregatorSecondarySyncState;
import io.harness.aggregator.repositories.AggregatorSecondarySyncStateRepository;
import io.harness.aggregator.repositories.MongoReconciliationOffsetRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

@OwnedBy(PL)
@Singleton
@Slf4j
public class AggregatorSecondarySyncController extends AggregatorBaseSyncController implements Runnable {
  private final AggregatorSecondarySyncStateRepository aggregatorSecondarySyncStateRepository;
  private final ACLRepository aclRepository;
  private final MongoReconciliationOffsetRepository mongoReconciliationOffsetRepository;

  @Inject
  public AggregatorSecondarySyncController(@Named(ACL.SECONDARY_COLLECTION) ACLRepository aclRepository,
      AggregatorSecondarySyncStateRepository aggregatorSecondarySyncStateRepository,
      RoleAssignmentRepository roleAssignmentRepository, RoleRepository roleRepository,
      ResourceGroupRepository resourceGroupRepository, UserGroupRepository userGroupRepository,
      AggregatorConfiguration aggregatorConfiguration, PersistentLocker persistentLocker,
      ChangeEventFailureHandler changeEventFailureHandler,
      MongoReconciliationOffsetRepository mongoReconciliationOffsetRepository,
      ChangeConsumerService changeConsumerService, RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler,
      UserGroupCRUDEventHandler userGroupCRUDEventHandler, ScopeService scopeService,
      AccessControlAdminService accessControlAdminService) {
    super(aclRepository, roleAssignmentRepository, roleRepository, resourceGroupRepository, userGroupRepository,
        aggregatorConfiguration, persistentLocker, changeEventFailureHandler, AggregatorJobType.SECONDARY,
        changeConsumerService, roleAssignmentCRUDEventHandler, userGroupCRUDEventHandler, scopeService,
        accessControlAdminService);
    this.aggregatorSecondarySyncStateRepository = aggregatorSecondarySyncStateRepository;
    this.aclRepository = aclRepository;
    this.mongoReconciliationOffsetRepository = mongoReconciliationOffsetRepository;
  }

  @Override
  public void run() {
    DebeziumEngine<ChangeEvent<String, String>> debeziumEngine = null;
    Future<?> debeziumEngineFuture = null;
    try (AcquiredLock<?> aggregatorLock = acquireLock(true)) {
      if (aggregatorLock == null) {
        log.info("Could not get lock for secondary sync. Exiting.");
        return;
      }
      log.info("Acquired lock, initiating secondary sync.");
      RLock rLock = (RLock) aggregatorLock.getLock();
      while (true) {
        if (isDebeziumEngineNotRunning(debeziumEngineFuture)) {
          if (isDebeziumEngineCrashed(debeziumEngine, debeziumEngineFuture) || !rLock.isHeldByCurrentThread()) {
            log.warn("Secondary sync debezium engine has crashed or lock has been lost. Exiting");
            return;
          } else {
            Optional<AggregatorSecondarySyncState> syncStateOpt =
                aggregatorSecondarySyncStateRepository.findByIdentifier(ACCESS_CONTROL_SERVICE);
            if (syncStateOpt.isPresent() && isSecondarySyncRequested(syncStateOpt.get())) {
              cleanUpAndBootstrapForBulkSync();
              aggregatorSecondarySyncStateRepository.updateStatus(
                  syncStateOpt.get(), SecondarySyncStatus.SECONDARY_SYNC_RUNNING);
              AccessControlDebeziumChangeConsumer accessControlDebeziumChangeConsumer = buildDebeziumChangeConsumer();
              debeziumEngine =
                  getEngine(aggregatorConfiguration.getDebeziumConfig(), accessControlDebeziumChangeConsumer);
              debeziumEngineFuture = executorService.submit(debeziumEngine);
            } else if (syncStateOpt.isPresent() && isSecondarySyncRunning(syncStateOpt.get())) {
              AccessControlDebeziumChangeConsumer accessControlDebeziumChangeConsumer = buildDebeziumChangeConsumer();
              debeziumEngine =
                  getEngine(aggregatorConfiguration.getDebeziumConfig(), accessControlDebeziumChangeConsumer);
              debeziumEngineFuture = executorService.submit(debeziumEngine);
            }
          }
        }
        log.info("secondary lock remaining ttl {}, isHeldByCurrentThread {}, holdCount {}, name {}",
            rLock.remainTimeToLive(), rLock.isHeldByCurrentThread(), rLock.getHoldCount(), rLock.getName());
        TimeUnit.SECONDS.sleep(30);
      }
    } catch (InterruptedException e) {
      log.warn("Secondary sync has been interrupted. Exiting", e);
    } catch (Exception e) {
      log.error("Secondary sync failed due to exception ", e);
    } finally {
      if (debeziumEngine != null) {
        stopDebeziumEngine(debeziumEngine);
      }
    }
  }

  private boolean isSecondarySyncRequested(AggregatorSecondarySyncState aggregatorSecondarySyncState) {
    return SecondarySyncStatus.SECONDARY_SYNC_REQUESTED.equals(aggregatorSecondarySyncState.getSecondarySyncStatus());
  }

  private boolean isSecondarySyncRunning(AggregatorSecondarySyncState aggregatorSecondarySyncState) {
    return SecondarySyncStatus.SECONDARY_SYNC_RUNNING.equals(aggregatorSecondarySyncState.getSecondarySyncStatus());
  }

  private void cleanUpAndBootstrapForBulkSync() {
    aclRepository.cleanCollection();
    mongoReconciliationOffsetRepository.cleanCollection(SECONDARY_COLLECTION);
  }

  private boolean isDebeziumEngineCrashed(
      DebeziumEngine<ChangeEvent<String, String>> debeziumEngine, Future<?> debeziumFuture) {
    return debeziumEngine != null && debeziumFuture != null && debeziumFuture.isDone();
  }

  private boolean isDebeziumEngineNotRunning(Future<?> debeziumFuture) {
    return debeziumFuture == null || debeziumFuture.isDone();
  }

  private void stopDebeziumEngine(DebeziumEngine<ChangeEvent<String, String>> debeziumEngine) {
    try {
      debeziumEngine.close();
      TimeUnit.SECONDS.sleep(10);
    } catch (IOException exception) {
      log.error("Failed to close debezium engine", exception);
    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting for debezium engine to close", e);
    }
  }

  @Override
  public String getLockName() {
    return String.format("%s_%s", ACCESS_CONTROL_AGGREGATOR_LOCK, AggregatorJobType.SECONDARY);
  }

  @Override
  protected String getOffsetStorageCollection() {
    return SECONDARY_COLLECTION;
  }
}
