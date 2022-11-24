/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script to add inframapping name to instance data
 * @author rktummala on 04/03/19
 */
@Slf4j
public class AddInfraMappingNameToInstanceData implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public void migrate() {
    PageRequest<Account> accountPageRequest = aPageRequest().addFieldsIncluded("_id").build();
    List<Account> accounts = accountService.list(accountPageRequest);
    accounts.forEach(account -> {
      List<String> appIds = appService.getAppIdsByAccountId(account.getUuid());
      appIds.forEach(appId -> {
        try {
          log.info("Adding infraMapping name to instances for appId:" + appId);
          PageRequest<InfrastructureMapping> pageRequest = new PageRequest<>();
          pageRequest.addFilter("appId", Operator.EQ, appId);
          PageResponse<InfrastructureMapping> response = infraMappingService.list(pageRequest);
          // Response only contains id
          List<InfrastructureMapping> infraMappingList = response.getResponse();

          infraMappingList.forEach(infraMapping -> {
            String infraMappingId = infraMapping.getUuid();
            try (AcquiredLock lock = persistentLocker.tryToAcquireLock(
                     InfrastructureMapping.class, infraMappingId, Duration.ofSeconds(120))) {
              if (lock == null) {
                return;
              }

              try {
                List<Instance> instances = wingsPersistence.createQuery(Instance.class)
                                               .field("isDeleted")
                                               .equal(false)
                                               .field("infraMappingId")
                                               .equal(infraMappingId)
                                               .field("appId")
                                               .equal(appId)
                                               .asList();

                instances.forEach(instance
                    -> wingsPersistence.updateField(
                        Instance.class, instance.getUuid(), "infraMappingName", infraMapping.getName()));
              } catch (Exception ex) {
                log.warn("Adding infraMappingName failed for infraMappingId [{}]", infraMappingId, ex);
              }
            } catch (Exception e) {
              log.warn("Failed to acquire lock for infraMappingId [{}] of appId [{}]", infraMappingId, appId);
            }
          });

          log.info("Adding infraMappingName done for appId:" + appId);
        } catch (WingsException exception) {
          ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
        } catch (Exception ex) {
          log.warn("Error while setting infraMappingName to instances for app: {}", appId, ex);
        }
      });
    });
  }
}
