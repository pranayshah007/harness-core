/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.comparator;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifact.ComparatorUtils;
import io.harness.artifacts.beans.BuildDetailsInternal;

import java.util.Comparator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
public class BuildDetailsInternalTimeComparator implements Comparator<BuildDetailsInternal> {
  @Override
  public int compare(BuildDetailsInternal o1, BuildDetailsInternal o2) {
    if (o1.getImagePushedAt() == null || o2.getImagePushedAt() == null
        || o1.getImagePushedAt().compareTo(o2.getImagePushedAt()) == 0) {
      return ComparatorUtils.compareDescending(o1.getNumber(), o2.getNumber());
    }
    return ComparatorUtils.compareDescending(o1.getImagePushedAt(), o2.getImagePushedAt());
  }
}