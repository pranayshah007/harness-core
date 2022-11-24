package io.harness.ccm.views.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a ExecutionFilterValues")

public class FilterValues {
  @Schema(description = "policy ids and list of enforcement") HashMap<String, String> ruleIds;
  @Schema(description = "policy pack ids and list of enforcement") HashMap<String, String> ruleSetIds;

  public FilterValues toDTO() {
    return FilterValues.builder().ruleIds(getRuleIds()).ruleSetIds(getRuleSetIds()).build();
  }
}
