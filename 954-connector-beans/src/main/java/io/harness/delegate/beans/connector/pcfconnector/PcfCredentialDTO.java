package io.harness.delegate.beans.connector.pcfconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("PcfCredential")
@JsonDeserialize(using = PcfCredentialDTODeserializer.class)
@Schema(name = "PcfCredential", description = "This contains Pcf connector credentials")
public class PcfCredentialDTO {
  @NotNull @JsonProperty("type") PcfCredentialType type;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  PcfCredentialSpecDTO spec;

  @Builder
  public PcfCredentialDTO(PcfCredentialType type, PcfCredentialSpecDTO spec) {
    this.type = type;
    this.spec = spec;
  }
}
