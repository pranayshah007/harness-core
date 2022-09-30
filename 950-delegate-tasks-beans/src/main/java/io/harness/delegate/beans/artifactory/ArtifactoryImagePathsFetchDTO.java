package io.harness.delegate.beans.artifactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactoryImagePathsFetchDTO {
  List<ArtifactoryImagePathFetchDTO> imagePaths;
}
