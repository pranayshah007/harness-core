package io.harness.ng.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApiResponseDTO {
  private String error;
  @JsonInclude(JsonInclude.Include.NON_NULL) private List<ApiResponseDetailDTO> detail;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class ApiResponseDetailDTO {
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL) private String field;
  }
}
