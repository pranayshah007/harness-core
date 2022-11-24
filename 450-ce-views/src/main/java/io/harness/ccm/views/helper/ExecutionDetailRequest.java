package io.harness.ccm.views.helper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ExecutionDetailRequest", description = "This has the query for ExecutionDetailRequest")

public class ExecutionDetailRequest {
  @Schema(description = "EnforcementIds") List<String> enforcementIds;

  @Builder
  public ExecutionDetailRequest(List<String> enforcementIds) {
    this.enforcementIds = enforcementIds;
  }
}
