

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
@FieldNameConstants(innerTypeName = "FilterConfigKeys")
@OwnedBy(PL)
public class ScimFilterConfig {
  private boolean supported;

  private int maxResults;

  @JsonCreator
  public ScimFilterConfig(@JsonProperty(value = "supported", required = true) final boolean supported,
      @JsonProperty(value = "maxResults", required = true) final int maxResults) {
    this.supported = supported;
    this.maxResults = maxResults;
  }
}
