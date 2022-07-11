/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.skip.factory;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.skip.skipper.VertexSkipper;
import io.harness.skip.skipper.impl.NoOpSkipper;
import io.harness.skip.skipper.impl.SkipNodeSkipper;
import io.harness.skip.skipper.impl.SkipTreeSkipper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDC)
@Singleton
public class VertexSkipperFactory {
  @Inject private SkipNodeSkipper skipNodeSkipper;
  @Inject private SkipTreeSkipper skipTreeSkipper;
  @Inject private NoOpSkipper noOpSkipper;

  public VertexSkipper obtainVertexSkipper(SkipType skipType) {
    if (SkipType.SKIP_NODE == skipType) {
      return skipNodeSkipper;
    } else if (SkipType.SKIP_TREE == skipType) {
      return skipTreeSkipper;
    } else if (SkipType.NOOP == skipType) {
      return noOpSkipper;
    } else {
      throw new UnsupportedOperationException(format("Unsupported skipper type : [%s]", skipType));
    }
  }
}
