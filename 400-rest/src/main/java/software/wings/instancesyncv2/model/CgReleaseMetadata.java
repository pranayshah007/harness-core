package software.wings.instancesyncv2.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CgReleaseMetadata {
  long deleteAfter;
}
