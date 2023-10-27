/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig.AwsCdkConfigKeys;
import io.harness.expression.EngineExpressionSecretUtils;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsCdkConfigDAL {
  @Inject private HPersistence persistence;

  @Inject private CDExpressionResolver cdExpressionResolver;

  public void saveAwsCdkConfig(@NonNull AwsCdkConfig config) {
    AwsCdkConfig secretsRevertedConfig = (AwsCdkConfig) EngineExpressionSecretUtils.revertSecrets(config);
    persistence.save(secretsRevertedConfig);
  }

  public AwsCdkConfig getRollbackAwsCdkConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<AwsCdkConfig> query = persistence.createQuery(AwsCdkConfig.class)
                                    .filter(AwsCdkConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                                    .filter(AwsCdkConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                                    .filter(AwsCdkConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                                    .filter(AwsCdkConfigKeys.provisionerIdentifier, provisionerIdentifier)
                                    .order(Sort.descending(AwsCdkConfigKeys.createdAt));
    query.and(query.criteria(AwsCdkConfigKeys.stageExecutionId)
                  .notEqual(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance)));
    AwsCdkConfig awsCdkConfig = query.get();
    cdExpressionResolver.updateExpressions(ambiance, awsCdkConfig);
    return awsCdkConfig;
  }

  public void deleteAwsCdkConfig(Ambiance ambiance, String provisionerIdentifier) {
    Query<AwsCdkConfig> query =
        persistence.createQuery(AwsCdkConfig.class)
            .filter(AwsCdkConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(AwsCdkConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(AwsCdkConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(AwsCdkConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(AwsCdkConfigKeys.stageExecutionId, AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance));
    persistence.delete(query);
  }
}
