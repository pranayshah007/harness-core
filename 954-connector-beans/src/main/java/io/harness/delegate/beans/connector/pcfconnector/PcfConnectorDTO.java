package io.harness.delegate.beans.connector.pcfconnector;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("PcfConnector")
@Schema(name = "PcfConnector", description = "This contains details of the Pcf connector")
public class PcfConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @Valid PcfCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getType() == PcfCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(credential.getSpec());
    }
    return null;
  }

  @Override
  public void validate() {
    PcfManualDetailsDTO pcfManualDetailsDTO = (PcfManualDetailsDTO) credential.getSpec();
    if (isNull(pcfManualDetailsDTO.getUsername()) && isNull(pcfManualDetailsDTO.getUsernameRef())) {
      throw new InvalidRequestException("Username Can not be null for pcf Connector");
    }
  }
}
