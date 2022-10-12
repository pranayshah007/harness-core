package io.harness.ccm.remote.resources.policies;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ListDTO {
  @JsonProperty("query") @Valid QueryFeild queryFeild;
}
