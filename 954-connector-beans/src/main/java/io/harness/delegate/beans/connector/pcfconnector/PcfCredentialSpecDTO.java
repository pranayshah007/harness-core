package io.harness.delegate.beans.connector.pcfconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonSubTypes({ @JsonSubTypes.Type(value = PcfManualDetailsDTO.class, name = PcfConstants.MANUAL_CONFIG) })
@ApiModel("PcfCredentialSpec")
@Schema(name = "PcfCredentialSpec", description = "This contains Pcf connector credentials spec")
public interface PcfCredentialSpecDTO extends DecryptableEntity {}
