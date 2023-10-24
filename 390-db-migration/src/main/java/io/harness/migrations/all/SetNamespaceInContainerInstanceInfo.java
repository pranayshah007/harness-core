/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.PodInstanceKey;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script to set namespace for kubernetes instances.
 * @author bzane on 5/10/19
 */
@Slf4j
public class SetNamespaceInContainerInstanceInfo implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public void migrate() {
    for (Account account :
        wingsPersistence.createAnalyticsQuery(Account.class, excludeAuthority).project(Account.ID_KEY2, true)) {
      for (Application app : wingsPersistence.createQuery(Application.class)
                                 .filter(Application.ACCOUNT_ID_KEY2, account.getUuid())
                                 .project(Application.ID_KEY2, true)) {
        String appId = app.getUuid();
        try {
          log.info("Fixing instances for appId:" + appId);
          PageRequest<InfrastructureMapping> pageRequest = new PageRequest<>();
          pageRequest.addFilter("appId", Operator.EQ, appId);
          PageResponse<InfrastructureMapping> response = infraMappingService.list(pageRequest);
          // Response only contains id
          List<InfrastructureMapping> infraMappingList = response.getResponse();

          for (InfrastructureMapping infraMapping : infraMappingList) {
            String infraMappingId = infraMapping.getUuid();
            log.info("Fixing kubernetes instances for infra mappingId:" + infraMappingId);
            try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(
                     InfrastructureMapping.class, infraMappingId, Duration.ofSeconds(120), Duration.ofSeconds(120))) {
              try {
                List<Instance> instances =
                    wingsPersistence.createQuery(Instance.class)
                        .filter("infraMappingId", infraMappingId)
                        .filter("appId", appId)
                        .filter("instanceType", InstanceType.KUBERNETES_CONTAINER_INSTANCE.name())
                        .filter("isDeleted", false)
                        .asList();

                for (Instance instance : instances) {
                  InstanceInfo instanceInfo = instance.getInstanceInfo();
                  if (instanceInfo == null) {
                    log.error("instanceInfo is null for instance {}", instance.getUuid());
                    continue;
                  }

                  if (!(instanceInfo instanceof KubernetesContainerInfo) && !(instanceInfo instanceof K8sPodInfo)) {
                    log.error("instanceInfo is not of type KubernetesContainerInfo or K8sPodInfo for instance {}",
                        instance.getUuid());
                    continue;
                  }

                  if (instanceInfo instanceof KubernetesContainerInfo) {
                    KubernetesContainerInfo kubernetesContainerInfo = (KubernetesContainerInfo) instanceInfo;

                    if (isBlank(kubernetesContainerInfo.getNamespace())) {
                      log.error("namespace is blank in container info for {}", instance.getUuid());
                      continue;
                    }

                    if (instance.getContainerInstanceKey() == null) {
                      log.error("container key not found for {}", instance.getUuid());
                      continue;
                    }

                    ContainerInstanceKey containerInstanceKey = instance.getContainerInstanceKey();
                    containerInstanceKey.setNamespace(kubernetesContainerInfo.getNamespace());
                    wingsPersistence.updateField(
                        Instance.class, instance.getUuid(), InstanceKeys.containerInstanceKey, containerInstanceKey);
                  } else {
                    K8sPodInfo podInfo = (K8sPodInfo) instanceInfo;

                    if (isBlank(podInfo.getNamespace())) {
                      log.error("namespace is blank in container info for {}", instance.getUuid());
                      continue;
                    }

                    if (instance.getPodInstanceKey() == null) {
                      log.error("pod key not found for {}", instance.getUuid());
                      continue;
                    }

                    PodInstanceKey podInstanceKey = instance.getPodInstanceKey();
                    podInstanceKey.setNamespace(podInfo.getNamespace());
                    wingsPersistence.updateField(
                        Instance.class, instance.getUuid(), InstanceKeys.podInstanceKey, podInstanceKey);
                  }
                }
                log.info("Instance fix completed for Kubernetes instances for infra mapping [{}]", infraMappingId);
              } catch (Exception ex) {
                log.warn("Kubernetes Instance fix failed for infraMappingId [{}]", infraMappingId, ex);
              }
            }
          }
          log.info("Kubernetes Instance fix done for appId:" + appId);
        } catch (Exception ex) {
          log.warn("Error while fixing Kubernetes instances for app: {}", appId, ex);
        }
      }
    }
  }
}
