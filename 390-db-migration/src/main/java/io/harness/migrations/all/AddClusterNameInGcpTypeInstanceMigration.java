/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;

import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping.ContainerInfrastructureMappingKeys;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AddClusterNameInGcpTypeInstanceMigration implements Migration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private AccountService accountService;

  private static final String DEBUG_LINE = "ADD_CLUSTER_NAME_IN_GCP_TYPE_INSTANCES : ";
  private static final long START_TIMESTAMP = 1620691200000L;

  @Override
  public void migrate() {
    log.info("Running AddClusterNameInGcpTypeInstanceMigration");
    List<String> firstReleaseConsideredAccount = Collections.singletonList("8e_GD3EARMmLSjFpszuwzw");
    /*
    Considering below account for the first release
    8e_GD3EARMmLSjFpszuwzw : LightSpeed
     */

    for (String accountId : firstReleaseConsideredAccount) {
      log.info(StringUtils.join(DEBUG_LINE, "Starting adding clusterName to Instances for accountId:", accountId));

      try (HIterator<InfrastructureMapping> infraMappingHIterator =
               new HIterator<>(mongoPersistence.createQuery(InfrastructureMapping.class)
                                   .disableValidation()
                                   .filter(InfrastructureMappingKeys.accountId, accountId)
                                   .filter(InfrastructureMappingKeys.infraMappingType, "GCP_KUBERNETES")
                                   .field(ContainerInfrastructureMappingKeys.clusterName)
                                   .exists()
                                   .project(InfrastructureMappingKeys.uuid, true)
                                   .project(ContainerInfrastructureMappingKeys.clusterName, true)
                                   .project(InfrastructureMappingKeys.appId, true)
                                   .project(InfrastructureMappingKeys.name, true)
                                   .fetch())) {
        while (infraMappingHIterator.hasNext()) {
          ContainerInfrastructureMapping infrastructureMapping =
              (ContainerInfrastructureMapping) infraMappingHIterator.next();
          try {
            BulkWriteOperation instanceWriteOperation =
                mongoPersistence.getCollection(Instance.class).initializeUnorderedBulkOperation();
            BasicDBObject basicDBObject = new BasicDBObject()
                                              .append(InstanceKeys.infraMappingId, infrastructureMapping.getUuid())
                                              .append(InstanceKeys.appId, infrastructureMapping.getAppId())
                                              .append(InstanceKeys.createdAt, new BasicDBObject("$gt", START_TIMESTAMP))
                                              .append(InstanceKeys.isDeleted, false);
            instanceWriteOperation.find(basicDBObject)
                .update(new BasicDBObject(
                    "$set", new Document("instanceInfo.clusterName", infrastructureMapping.getClusterName())));

            log.info("Added clusterName to instances for infraMapping : {} with id : {} infra mappings: {}",
                infrastructureMapping.getName(), infrastructureMapping.getUuid(), instanceWriteOperation.execute());
          } catch (Exception e) {
            log.error(StringUtils.join(DEBUG_LINE, "Failed to add cluster name to instances for infraMappingId: ",
                          infrastructureMapping.getUuid()),
                e);
          }
        }

      } catch (Exception e) {
        log.error(
            StringUtils.join(DEBUG_LINE, "Failed to add cluster names to instances for accountId: ", accountId), e);
      }
      log.info(StringUtils.join(DEBUG_LINE, "Finished adding clusterName to Instance for accountId: ", accountId));
    }
  }
}
