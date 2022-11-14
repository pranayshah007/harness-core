package io.harness.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CountByOrgIdProjectIdAndServiceId {
  private String orgIdentifier;
  private String projectIdentifier;
  private String serviceIdentifier;
  private Long count;
}
