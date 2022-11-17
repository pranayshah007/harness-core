package io.harness.delegate.beans.connector.tasconnector.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TasConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @Valid TasCredentialOutcomeDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getType() == TasCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(credential.getSpec());
    }
    return null;
  }
}
