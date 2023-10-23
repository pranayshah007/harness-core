/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.migration;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.encryption.Scope;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.InfrastructureMapping.InfrastructureMappingNGKeys;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DeleteInstanceOfDeletedConnectors implements NGMigration {
  private MongoTemplate mongoTemplate;
  private CDMigrationUtils cdMigrationUtils;
  private final List<String> accountIdsToMigrate = Collections.singletonList("RMlrArpFS-mkF-ZsDADN5w");
  @Override
  public void migrate() {
    log.info("Migrating instances belonging to deleted connectors");
    try {
      for (String accountId : accountIdsToMigrate) {
        List<InfrastructureMapping> infraMappings = getInfrastructureMappingsForAccount(accountId);
        for (InfrastructureMapping infraMapping : infraMappings) {
          Optional<Connector> connectorOpt = getConnector(infraMapping);
          if (!connectorOpt.isPresent()) {
            log.info("Deleting orphan entities for account {} belonging to deleted ConnectorDisconnectHandler {} and "
                    + "infrastructure mapping {}",
                infraMapping.getAccountIdentifier(), infraMapping.getConnectorRef(), infraMapping.getId());
            cdMigrationUtils.deleteOrphanInstance(infraMapping);
            cdMigrationUtils.deleteRelatedOrphanEntities(infraMapping);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Unexpected error occurred while migrating instances for deleted connectors", ex);
    }
  }

  private Optional<Connector> getConnector(InfrastructureMapping infraMapping) {
    // Account Scope
    Criteria criteria = Criteria.where(ConnectorKeys.accountIdentifier).is(infraMapping.getAccountIdentifier());

    String[] connectorRefSplit = infraMapping.getConnectorRef().split("\\.");
    Scope scope;
    if (connectorRefSplit.length > 1) {
      scope = Scope.fromString(connectorRefSplit[0]);
      if (scope.equals(Scope.ORG)) {
        // Org Scope
        criteria.and(ConnectorKeys.orgIdentifier).is(infraMapping.getOrgIdentifier());
      }
      criteria.and(ConnectorKeys.identifier).is(connectorRefSplit[1]);
    } else {
      // Project Scope
      scope = Scope.fromString("project");
      criteria.and(ConnectorKeys.orgIdentifier).is(infraMapping.getOrgIdentifier());
      criteria.and(ConnectorKeys.projectIdentifier).is(infraMapping.getProjectIdentifier());
      criteria.and(ConnectorKeys.identifier).is(connectorRefSplit[0]);
    }
    criteria.and(ConnectorKeys.scope).is(scope);

    Connector connector = mongoTemplate.findOne(new Query(criteria), Connector.class);
    return connector != null ? Optional.of(connector) : Optional.empty();
  }

  private List<InfrastructureMapping> getInfrastructureMappingsForAccount(String accountIdentifier) {
    Query query = new Query(Criteria.where(InfrastructureMappingNGKeys.accountIdentifier).is(accountIdentifier));
    return mongoTemplate.find(query, InfrastructureMapping.class);
  }
}
