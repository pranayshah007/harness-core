package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonSubTypes({ @JsonSubTypes.Type(value = TasManualDetailsDTO.class, name = TasConstants.MANUAL_CONFIG) })
@ApiModel("TasCredentialSpec")
@Schema(name = "TasCredentialSpec", description = "This contains Tas connector credentials spec")
public interface TasCredentialSpecDTO extends DecryptableEntity {}
