package io.harness.ccm.views.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a ExecutionDetails")

public final class ExecutionDetails {
  @Schema(description = "list of enforcement ids and details")
  List<HashMap<String, ExecutionEnforcementDetails> > enforcementIds;

  public ExecutionDetails toDTO() {
    return ExecutionDetails.builder().enforcementIds(getEnforcementIds()).build();
  }
}
