package io.harness.ng.core.dto;

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
public class BadRequestDTO {
  private String error;
  private List<BadRequestDetailDTO> detail;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class BadRequestDetailDTO {
    private String error;
    private String field;
  }
}
