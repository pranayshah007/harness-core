package software.wings.instancesyncv2.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public abstract class AbstractCgReleaseIdentifier implements CgReleaseIdentifiers {
  @Builder.Default
  @EqualsAndHashCode.Exclude
  private CgReleaseMetadata metadata = CgReleaseMetadata.builder().deleteAfter(0).build();
  @Override
  public long getDeleteAfter() {
    return this.metadata.getDeleteAfter();
  }
  @Override
  public void setDeleteAfter(long timestamp) {
    this.metadata.setDeleteAfter(timestamp);
  }
}
