/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;
import static java.util.Objects.requireNonNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.provision.terragrunt.TerragruntConfig.TerragruntConfigKeys;
import io.harness.expression.EngineExpressionSecretUtils;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerragruntConfigDAL {
  private static final String NOT_NULL_MESSAGE = "%s must not be null";

  @Inject private HPersistence persistence;
  @Inject private CDExpressionResolver cdExpressionResolver;

  void saveTerragruntConfig(@Nonnull TerragruntConfig terragruntConfig) {
    // NG Secret Manager Functor is reversed to secret.getValue form so that token is not saved inside DB
    TerragruntConfig secretsRevertedConfig =
        (TerragruntConfig) EngineExpressionSecretUtils.revertSecrets(terragruntConfig);
    persistence.save(secretsRevertedConfig);
  }

  TerragruntConfig getTerragruntConfig(@Nonnull Query<TerragruntConfig> query, @Nonnull Ambiance ambiance) {
    TerragruntConfig terragruntConfig = query.get();

    if (terragruntConfig == null) {
      return null;
    }
    cdExpressionResolver.updateExpressions(ambiance, terragruntConfig);
    return terragruntConfig;
  }

  public void clearTerragruntConfig(@Nonnull Ambiance ambiance, @Nonnull String entityId) {
    persistence.delete(persistence.createQuery(TerragruntConfig.class)
                           .filter(TerragruntConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                           .filter(TerragruntConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                           .filter(TerragruntConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                           .filter(TerragruntConfigKeys.entityId, entityId));
  }

  public HIterator<TerragruntConfig> getIterator(Ambiance ambiance, String entityId) {
    return new HIterator(persistence.createQuery(TerragruntConfig.class)
                             .filter(TerragruntConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                             .filter(TerragruntConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                             .filter(TerragruntConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                             .filter(TerragruntConfigKeys.entityId, entityId)
                             .order(Sort.descending(TerragruntConfigKeys.createdAt))
                             .fetch());
  }

  public void deleteForAccount(String accountId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));

    persistence.delete(
        persistence.createQuery(TerragruntConfig.class).filter(TerragruntConfigKeys.accountId, accountId));
  }

  public void deleteForOrganization(String accountId, String orgId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));
    requireNonNull(orgId, String.format(NOT_NULL_MESSAGE, orgId));

    persistence.delete(persistence.createQuery(TerragruntConfig.class)
                           .filter(TerragruntConfigKeys.accountId, accountId)
                           .filter(TerragruntConfigKeys.orgId, orgId));
  }

  public void deleteForProject(String accountId, String orgId, String projectId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));
    requireNonNull(orgId, String.format(NOT_NULL_MESSAGE, orgId));
    requireNonNull(projectId, String.format(NOT_NULL_MESSAGE, projectId));

    persistence.delete(persistence.createQuery(TerragruntConfig.class)
                           .filter(TerragruntConfigKeys.accountId, accountId)
                           .filter(TerragruntConfigKeys.orgId, orgId)
                           .filter(TerragruntConfigKeys.projectId, projectId));
  }
}
