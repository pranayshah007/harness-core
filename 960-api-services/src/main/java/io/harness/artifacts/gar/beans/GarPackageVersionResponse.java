package io.harness.artifacts.gar.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GarPackageVersionResponse {
  private List<GarTags> Tags;
  String nextPageToken;
}
