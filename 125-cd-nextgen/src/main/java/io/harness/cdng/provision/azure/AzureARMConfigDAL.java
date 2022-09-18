/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.azure.beans.AzureARMConfig.AzureARMConfigKeys;
import io.harness.expression.EngineExpressionSecretUtils;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureARMConfigDAL {
  @Inject private HPersistence persistence;

  public void saveAzureARMConfig(@NonNull AzureARMConfig config) {
    AzureARMConfig secretsRevertedConfig = (AzureARMConfig) EngineExpressionSecretUtils.revertSecrets(config);
    persistence.save(secretsRevertedConfig);
  }

  public AzureARMConfig getRollbackAzureARMConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<AzureARMConfig> query =
        persistence.createQuery(AzureARMConfig.class)
            .filter(AzureARMConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(AzureARMConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(AzureARMConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(AzureARMConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(AzureARMConfigKeys.stageExecutionId, ambiance.getStageExecutionId())
            .order(Sort.descending(AzureARMConfigKeys.createdAt));
    return query.get();
  }

  public void clearStoredAzureARMConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<AzureARMConfig> query =
        persistence.createQuery(AzureARMConfig.class)
            .filter(AzureARMConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(AzureARMConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(AzureARMConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(AzureARMConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(AzureARMConfigKeys.stageExecutionId, ambiance.getStageExecutionId());

    persistence.delete(query);
  }
}
