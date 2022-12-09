

package io.harness.scim.system;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "BulkConfigKeys")
@OwnedBy(PL)
public class ScimBulkConfigResource {
  private boolean supported;

  private int maxOperations;

  private int maxPayloadSize;

  @JsonCreator
  public ScimBulkConfigResource(@JsonProperty(value = "supported", required = true) final boolean supported,
      @JsonProperty(value = "maxOperations", required = true) final int maxOperations,
      @JsonProperty(value = "maxPayloadSize", required = true) final int maxPayloadSize) {
    this.supported = supported;
    this.maxOperations = maxOperations;
    this.maxPayloadSize = maxPayloadSize;
  }
}
