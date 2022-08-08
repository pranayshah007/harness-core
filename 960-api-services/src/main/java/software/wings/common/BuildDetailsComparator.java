/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ComparatorUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.Serializable;
import java.util.Comparator;

@OwnedBy(HarnessTeam.CDC)
public class BuildDetailsComparator implements Comparator<BuildDetails>, Serializable {
  @Override
  public int compare(BuildDetails bd1, BuildDetails bd2) {
    return ComparatorUtils.compareDescending(bd1.getNumber(), bd2.getNumber());
  }
}
