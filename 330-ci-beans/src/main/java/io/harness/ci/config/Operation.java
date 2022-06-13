package io.harness.ci.config;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@NoArgsConstructor
public class Operation {
  @JsonProperty("field") @NotNull String field;
  @JsonProperty("value") String value;
}
