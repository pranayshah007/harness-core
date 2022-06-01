/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig.CloudformationConfigKeys;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionSecretUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CloudformationConfigDAL {
  @Inject private HPersistence persistence;

  public void saveCloudformationConfig(@NonNull CloudformationConfig config) {
    CloudformationConfig secretsRevertedConfig =
        (CloudformationConfig) EngineExpressionSecretUtils.revertSecrets(config);
    persistence.save(secretsRevertedConfig);
  }

  public CloudformationConfig getRollbackCloudformationConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<CloudformationConfig> query =
        persistence.createQuery(CloudformationConfig.class)
            .filter(CloudformationConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(CloudformationConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(CloudformationConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(CloudformationConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .order(Sort.descending(CloudformationConfigKeys.createdAt));
    query.and(query.criteria(CloudformationConfigKeys.stageExecutionId).notEqual(ambiance.getStageExecutionId()));
    return query.get();
  }

  public void clearStoredCloudformationConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<CloudformationConfig> query =
        persistence.createQuery(CloudformationConfig.class)
            .filter(CloudformationConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(CloudformationConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(CloudformationConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(CloudformationConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(CloudformationConfigKeys.stageExecutionId, ambiance.getStageExecutionId());

    persistence.delete(query);
  }
}
