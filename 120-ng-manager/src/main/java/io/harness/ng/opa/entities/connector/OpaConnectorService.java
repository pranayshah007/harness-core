package io.harness.ng.opa.entities.connector;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.pms.contracts.governance.GovernanceMetadata;

@OwnedBy(PL)
public interface OpaConnectorService {
  void evaluatePoliciesWithJson(String accountId, String expandedJson, String orgIdentifier, String projectIdentifier,
      String action, String identifier);
  GovernanceMetadata evaluatePoliciesWithEntity(String accountId, ConnectorDTO connectorDTO, String orgIdentifier,
      String projectIdentifier, String action, String identifier);
}
