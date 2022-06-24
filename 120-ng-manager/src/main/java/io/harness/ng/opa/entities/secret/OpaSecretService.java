package io.harness.ng.opa.entities.secret;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.pms.contracts.governance.GovernanceMetadata;

import static io.harness.annotations.dev.HarnessTeam.PL;

@OwnedBy(PL)
public interface OpaSecretService {
    GovernanceMetadata evaluatePoliciesWithEntity(String accountId, SecretDTOV2 secretDTO, String orgIdentifier,
                                                  String projectIdentifier, String action, String identifier);
}
