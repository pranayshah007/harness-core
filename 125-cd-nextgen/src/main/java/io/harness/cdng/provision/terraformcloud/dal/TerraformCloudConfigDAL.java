/*

 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terraformcloud.dal;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig.TerraformCloudConfigKeys;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudConfigDAL {
  @Inject private HPersistence persistence;
  @Inject private EngineExpressionService engineExpressionService;

  public void saveTerraformCloudConfig(@Nonnull TerraformCloudConfig terraformCloudConfig) {
    persistence.save(terraformCloudConfig);
  }

  TerraformCloudConfig getTerraformCloudConfig(@Nonnull Query<TerraformCloudConfig> query, @Nonnull Ambiance ambiance) {
    TerraformCloudConfig terraformCloudConfig = query.get();

    if (terraformCloudConfig == null) {
      return null;
    }
    ExpressionEvaluatorUtils.updateExpressions(
        terraformCloudConfig, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    return terraformCloudConfig;
  }

  public void clearTerraformCloudConfig(@Nonnull Ambiance ambiance, @Nonnull String entityId) {
    persistence.delete(persistence.createQuery(TerraformCloudConfig.class)
                           .filter(TerraformCloudConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                           .filter(TerraformCloudConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                           .filter(TerraformCloudConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                           .filter(TerraformCloudConfigKeys.entityId, entityId));
  }

  public HIterator<TerraformCloudConfig> getIterator(Ambiance ambiance, String entityId) {
    return new HIterator(persistence.createQuery(TerraformCloudConfig.class)
                             .filter(TerraformCloudConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                             .filter(TerraformCloudConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                             .filter(TerraformCloudConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                             .filter(TerraformCloudConfigKeys.entityId, entityId)
                             .order(Sort.descending(TerraformCloudConfigKeys.createdAt))
                             .fetch());
  }
}
