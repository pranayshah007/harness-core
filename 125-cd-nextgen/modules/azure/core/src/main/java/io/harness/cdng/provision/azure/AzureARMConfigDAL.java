/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static java.util.Objects.requireNonNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.azure.beans.AzureARMConfig.AzureARMConfigKeys;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureARMConfigDAL {
  private static final String NOT_NULL_MESSAGE = "%s must not be null";

  @Inject private HPersistence persistence;

  public void saveAzureARMConfig(@Nonnull AzureARMConfig azureARMConfig) {
    persistence.save(azureARMConfig);
  }

  public AzureARMConfig getAzureARMConfig(@Nonnull Ambiance ambiance, String provisionerIdentifier) {
    Query<AzureARMConfig> query =
        persistence.createQuery(AzureARMConfig.class)
            .filter(AzureARMConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(AzureARMConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(AzureARMConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(AzureARMConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(AzureARMConfigKeys.stageExecutionId, AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
            .order(Sort.ascending(AzureARMConfigKeys.createdAt));
    return query.get();
  }

  public void clearAzureARMConfig(@Nonnull Ambiance ambiance, @Nonnull String provisionerIdentifier) {
    persistence.delete(
        persistence.createQuery(AzureARMConfig.class)
            .filter(AzureARMConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(AzureARMConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(AzureARMConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(AzureARMConfigKeys.provisionerIdentifier, provisionerIdentifier)
            .filter(AzureARMConfigKeys.stageExecutionId, AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance)));
  }

  public void deleteForAccount(String accountId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));

    persistence.delete(persistence.createQuery(AzureARMConfig.class).filter(AzureARMConfigKeys.accountId, accountId));
  }

  public void deleteForOrganization(String accountId, String orgId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));
    requireNonNull(orgId, String.format(NOT_NULL_MESSAGE, orgId));

    persistence.delete(persistence.createQuery(AzureARMConfig.class)
                           .filter(AzureARMConfigKeys.accountId, accountId)
                           .filter(AzureARMConfigKeys.orgId, orgId));
  }

  public void deleteForProject(String accountId, String orgId, String projectId) {
    requireNonNull(accountId, String.format(NOT_NULL_MESSAGE, accountId));
    requireNonNull(orgId, String.format(NOT_NULL_MESSAGE, orgId));
    requireNonNull(projectId, String.format(NOT_NULL_MESSAGE, projectId));

    persistence.delete(persistence.createQuery(AzureARMConfig.class)
                           .filter(AzureARMConfigKeys.accountId, accountId)
                           .filter(AzureARMConfigKeys.orgId, orgId)
                           .filter(AzureARMConfigKeys.projectId, projectId));
  }
}
