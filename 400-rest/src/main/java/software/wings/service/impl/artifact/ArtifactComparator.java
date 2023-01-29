/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ComparatorUtils;

import software.wings.persistence.artifact.Artifact;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Compares build number in descending order
 */
@OwnedBy(CDC)
public class ArtifactComparator implements Comparator<Artifact>, Serializable {
  @Override
  public int compare(Artifact artifact1, Artifact artifact2) {
    return ComparatorUtils.compareDescending(artifact1.getBuildNo(), artifact2.getBuildNo());
  }
}
