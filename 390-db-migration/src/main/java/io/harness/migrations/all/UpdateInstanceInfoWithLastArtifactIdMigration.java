/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateInstanceInfoWithLastArtifactIdMigration implements Migration {
  String debugLog = "InstanceInfo_LastArtifactId_Migration: ";

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info(debugLog + "Starting UpdateInstanceInfoWithLastArtifactId migration");

    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        if (account == null) {
          log.info(debugLog + "Account is null,  continuing");
          continue;
        }

        log.info(debugLog + "Starting migration  for account {}", account.getAccountName());
        try (HIterator<Application> applications =
                 new HIterator<>(wingsPersistence.createQuery(Application.class)
                                     .filter(ApplicationKeys.accountId, account.getUuid())
                                     .fetch())) {
          while (applications.hasNext()) {
            Application application = applications.next();
            if (application == null) {
              log.info(debugLog + "Application is null, skipping");
              continue;
            }

            log.info(debugLog + "Starting migration for  application {}", application.getName());
            try (HIterator<Instance> instances = new HIterator<>(wingsPersistence.createQuery(Instance.class)
                                                                     .filter(InstanceKeys.appId, application.getUuid())
                                                                     .filter(InstanceKeys.isDeleted, false)
                                                                     .fetch())) {
              while (instances.hasNext()) {
                Instance instance = instances.next();
                InstanceInfo instanceInfo = instance.getInstanceInfo();

                if (instanceInfo instanceof K8sPodInfo) {
                  List<K8sContainerInfo> containers = ((K8sPodInfo) instanceInfo).getContainers();

                  if (isEmpty(containers)) {
                    continue;
                  }

                  for (K8sContainerInfo k8sContainerInfo : containers) {
                    Artifact artifact = wingsPersistence.createQuery(Artifact.class)
                                            .filter(ArtifactKeys.artifactStreamId, instance.getLastArtifactStreamId())
                                            .filter(ArtifactKeys.appId, instance.getAppId())
                                            .filter("metadata.image", k8sContainerInfo.getImage())
                                            .disableValidation()
                                            .get();
                    if (artifact != null) {
                      String artifactUuid = artifact.getUuid();

                      if (!artifactUuid.equals(instance.getLastArtifactId())) {
                        instance.setLastArtifactId(artifactUuid);
                        wingsPersistence.save(instance);
                        log.info(debugLog + "Updated instance {}", instance.getUuid());
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }
        log.info(debugLog + "Migration done for account {}", account.getAccountName());
      }
    }
    log.info(debugLog + "Completed UpdateInstanceInfoWithLastArtifactId migration");
  }
}
