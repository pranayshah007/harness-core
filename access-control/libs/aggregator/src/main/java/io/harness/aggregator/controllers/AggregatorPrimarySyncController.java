/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.controllers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.AggregatorConfiguration;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.aggregator.consumers.AccessControlDebeziumChangeConsumer;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.aggregator.models.MongoReconciliationOffset;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

@OwnedBy(PL)
@Singleton
@Slf4j
public class AggregatorPrimarySyncController extends AggregatorBaseSyncController implements Runnable {
  @Inject
  public AggregatorPrimarySyncController(@Named(ACL.PRIMARY_COLLECTION) ACLRepository primaryAclRepository,
      RoleAssignmentRepository roleAssignmentRepository, RoleRepository roleRepository,
      ResourceGroupRepository resourceGroupRepository, UserGroupRepository userGroupRepository,
      AggregatorConfiguration aggregatorConfiguration, PersistentLocker persistentLocker,
      ChangeEventFailureHandler changeEventFailureHandler, ACLGeneratorService aclGeneratorService,
      RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler,
      UserGroupCRUDEventHandler userGroupCRUDEventHandler, ScopeService scopeService,
      AccessControlAdminService accessControlAdminService) {
    super(primaryAclRepository, roleAssignmentRepository, roleRepository, resourceGroupRepository, userGroupRepository,
        aggregatorConfiguration, persistentLocker, changeEventFailureHandler, AggregatorJobType.PRIMARY,
        aclGeneratorService, roleAssignmentCRUDEventHandler, userGroupCRUDEventHandler, scopeService,
        accessControlAdminService);
  }

  @Override
  public void run() {
    DebeziumEngine<ChangeEvent<String, String>> debeziumEngine = null;
    try (AcquiredLock<?> aggregatorLock = acquireLock(true)) {
      if (aggregatorLock == null) {
        return;
      }
      log.info("Acquired lock, initiating primary sync.");
      RLock rLock = (RLock) aggregatorLock.getLock();
      AccessControlDebeziumChangeConsumer accessControlDebeziumChangeConsumer = buildDebeziumChangeConsumer();
      debeziumEngine = getEngine(aggregatorConfiguration.getDebeziumConfig(), accessControlDebeziumChangeConsumer);
      Future<?> debeziumEngineFuture = executorService.submit(debeziumEngine);

      while (!debeziumEngineFuture.isDone() && rLock.isHeldByCurrentThread()) {
        log.info("primary lock remaining ttl {}, isHeldByCurrentThread {}, holdCount {}, name {}",
            rLock.remainTimeToLive(), rLock.isHeldByCurrentThread(), rLock.getHoldCount(), rLock.getName());
        TimeUnit.SECONDS.sleep(30);
      }
      log.warn("The primary sync debezium engine has unexpectedly stopped or the lock is no longer held");

    } catch (InterruptedException e) {
      log.warn("Thread interrupted, stopping primary aggregator sync", e);
    } catch (Exception e) {
      log.error("Primary sync stopped due to exception", e);
    } finally {
      try {
        if (debeziumEngine != null) {
          debeziumEngine.close();
          TimeUnit.SECONDS.sleep(10);
        }
      } catch (IOException e) {
        log.error("Failed to close debezium engine due to IO exception", e);
      } catch (InterruptedException e) {
        log.warn("Interrupted while waiting for debezium engine to close", e);
      } catch (Exception e) {
        log.error("Failed to close debezium engine due to unexpected exception", e);
      }
    }
  }

  public String getLockName() {
    return String.format("%s_%s", ACCESS_CONTROL_AGGREGATOR_LOCK, AggregatorJobType.PRIMARY);
  }

  @Override
  protected String getOffsetStorageCollection() {
    return MongoReconciliationOffset.PRIMARY_COLLECTION;
  }
}
