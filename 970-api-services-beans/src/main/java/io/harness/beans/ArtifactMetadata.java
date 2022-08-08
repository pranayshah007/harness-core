/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactMetadata extends HashMap<String, String> {
  public ArtifactMetadata() {
    super(new HashMap<>());
  }

  public ArtifactMetadata(Map<String, String> map) {
    super(EmptyPredicate.isEmpty(map) ? new HashMap<>() : map);
  }

  public String getSHA() {
    return null;
  }
}
