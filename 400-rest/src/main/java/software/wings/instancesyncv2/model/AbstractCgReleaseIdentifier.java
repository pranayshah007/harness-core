/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.instancesyncv2.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@RequiredArgsConstructor
@SuperBuilder
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
