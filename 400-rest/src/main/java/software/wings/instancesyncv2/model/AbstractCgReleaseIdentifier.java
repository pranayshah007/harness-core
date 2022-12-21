package software.wings.instancesyncv2.model;

import lombok.EqualsAndHashCode;
@EqualsAndHashCode
public abstract class AbstractCgReleaseIdentifier implements CgReleaseIdentifiers {
  @EqualsAndHashCode.Exclude private CgReleaseMetadata metadata;

  @Override
  public long getDeleteAfter() {
    return this.metadata.getDeleteAfter();
  }
  @Override
  public void setDeleteAfter(long timestamp) {
    this.metadata.setDeleteAfter(timestamp);
  }
}
