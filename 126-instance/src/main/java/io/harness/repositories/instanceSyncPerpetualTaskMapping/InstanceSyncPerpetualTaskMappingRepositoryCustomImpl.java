/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instanceSyncPerpetualTaskMapping;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InstanceSyncPerpetualTaskMapping;
import io.harness.entities.InstanceSyncPerpetualTaskMapping.InstanceSyncPerpetualTaskMappingKey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.DX)
public class InstanceSyncPerpetualTaskMappingRepositoryCustomImpl
    implements InstanceSyncPerpetualTaskMappingRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Optional<InstanceSyncPerpetualTaskMapping> findByConnectorRefAndDeploymentType(
      String accountId, String orgId, String projectId, String connectorRef, String deploymentType) {
    Criteria criteria = Criteria.where(InstanceSyncPerpetualTaskMappingKey.connectorIdentifier).is(connectorRef);
    criteria.and(InstanceSyncPerpetualTaskMappingKey.deploymentType).is(deploymentType);
    criteria.and(InstanceSyncPerpetualTaskMappingKey.accountId).is(accountId);

    if (isNotBlank(orgId)) {
      criteria.and(InstanceSyncPerpetualTaskMappingKey.orgId).is(orgId);
    }
    if (isNotBlank(projectId)) {
      criteria.and(InstanceSyncPerpetualTaskMappingKey.projectId).is(projectId);
    }

    Query query = new Query(criteria);
    return Optional.ofNullable(mongoTemplate.findOne(query, InstanceSyncPerpetualTaskMapping.class));
  }
}
