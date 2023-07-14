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
import io.harness.artifacts.beans.BuildDetailsInternal;

import java.util.Comparator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
public class BuildDetailsInternalComparatorDateDescending implements Comparator<BuildDetailsInternal> {
  @Override
  public int compare(BuildDetailsInternal o1, BuildDetailsInternal o2) {
    if (o1 == null && o2 == null) {
      return 0;
    }
    if (o1 == null) {
      return -1;
    }
    if (o2 == null) {
      return 1;
    }

    if (o1.getImagePushedAt() == null && o2.getImagePushedAt() == null) {
      return 0;
    }
    if (o1.getImagePushedAt() == null) {
      return -1;
    }
    if (o2.getImagePushedAt() == null) {
      return 1;
    }

    return Comparator.comparing(BuildDetailsInternal::getImagePushedAt)
        .thenComparing(BuildDetailsInternal::getNumber)
        .compare(o2, o1);
  }
}
