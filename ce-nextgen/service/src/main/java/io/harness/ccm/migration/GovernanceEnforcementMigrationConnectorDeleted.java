/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.ccm.views.helper.RuleCloudProviderType.AWS;
import static io.harness.ccm.views.helper.RuleCloudProviderType.AZURE;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleEnforcement.RuleEnforcementId;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class GovernanceEnforcementMigrationConnectorDeleted implements NGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private GovernanceRuleService governanceRuleService;
  @Override
  public void migrate() {
    try {
      log.info("Starting migration GovernanceEnforcementMigrationConnectorDeleted");
      final List<RuleEnforcement> ruleEnforcementList =
          hPersistence.createQuery(RuleEnforcement.class, excludeAuthority).asList();
      for (final RuleEnforcement ruleEnforcement : ruleEnforcementList) {
        // For every ruleEnforcement we get target accounts and delete target account which do not exist
        try {
          migrateRuleEnforcementForTargetDeleted(ruleEnforcement.getAccountId(), ruleEnforcement.getUuid(),
              new HashSet<>(ruleEnforcement.getTargetAccounts()), ruleEnforcement.getCloudProvider());
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, ruleId {}, {}", ruleEnforcement.getAccountId(),
              ruleEnforcement.getUuid(), e);
        }
        log.info("GovernanceEnforcementMigrationConnectorDeleted has been completed");
      }
    } catch (final Exception e) {
      log.error("Failure occurred in GovernanceEnforcementMigrationConnectorDeleted", e);
    }
  }

  private void migrateRuleEnforcementForTargetDeleted(
      String accountId, String uuid, Set<String> targetAccounts, RuleCloudProviderType ruleCloudProviderType) {
    Set<String> updatedTargetAccounts = getUpdatedTargetAccounts(accountId, targetAccounts, ruleCloudProviderType);
    Query query = hPersistence.createQuery(RuleEnforcement.class)
                      .field(RuleEnforcementId.accountId)
                      .equal(accountId)
                      .field(RuleEnforcementId.uuid)
                      .equal(uuid);
    UpdateOperations<RuleEnforcement> updateOperations = hPersistence.createUpdateOperations(RuleEnforcement.class);

    updateOperations.set(RuleEnforcementId.targetAccounts, updatedTargetAccounts);
    hPersistence.update(query, updateOperations);
  }

  private Set<String> getUpdatedTargetAccounts(
      String accountId, Set<String> targetAccounts, RuleCloudProviderType ruleCloudProviderType) {
    Set<String> updatedTargetAccounts = new HashSet<>();
    Set<ConnectorInfoDTO> connectorInfoDTOSet =
        governanceRuleService.getConnectorResponse(accountId, targetAccounts, ruleCloudProviderType);
    if (ruleCloudProviderType == AWS) {
      for (ConnectorInfoDTO connectorInfo : connectorInfoDTOSet) {
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
        updatedTargetAccounts.add(ceAwsConnectorDTO.getAwsAccountId());
      }
    } else if (ruleCloudProviderType == AZURE) {
      for (ConnectorInfoDTO connectorInfo : connectorInfoDTOSet) {
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfo.getConnectorConfig();
        updatedTargetAccounts.add(ceAzureConnectorDTO.getSubscriptionId());
      }
    }
    return updatedTargetAccounts;
  }
}
