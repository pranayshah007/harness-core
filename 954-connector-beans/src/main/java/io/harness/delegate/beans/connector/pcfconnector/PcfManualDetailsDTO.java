package io.harness.delegate.beans.connector.pcfconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(PcfConstants.MANUAL_CONFIG)
@ApiModel("PcfManualDetails")
@Schema(name = "PcfManualDetails", description = "This contains Pcf manual credentials connector details")
public class PcfManualDetailsDTO implements PcfCredentialSpecDTO {
  @Schema(description = "Endpoint URL of the PCF Cluster.") @JsonProperty("endpointUrl") @NotNull String endpointUrl;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData userName;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData passwordRef;
}
