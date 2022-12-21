package software.wings.instancesyncv2.model;

import lombok.EqualsAndHashCode;
@EqualsAndHashCode
public abstract class AbstractCgReleaseIdentifier implements CgReleaseIdentifiers {
  @EqualsAndHashCode.Exclude private CgReleaseMetadata metadata;
}
