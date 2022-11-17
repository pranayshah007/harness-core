package io.harness.delegate.beans.connector.tasconnector.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialSpecDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
public class TasCredentialOutcomeDTO {
  @NotNull @JsonProperty("type") TasCredentialType type;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  TasCredentialSpecDTO spec;
  @Builder
  public TasCredentialOutcomeDTO(TasCredentialType type, TasCredentialSpecDTO spec) {
    this.type = type;
    this.spec = spec;
  }
}
