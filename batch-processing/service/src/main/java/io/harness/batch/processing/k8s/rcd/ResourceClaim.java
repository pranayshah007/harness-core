/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResourceClaim {
  public static final ResourceClaim EMPTY = ResourceClaim.builder().build();

  long cpuNano;
  long memBytes;

  public ResourceClaim scale(int n) {
    return ResourceClaim.builder().cpuNano(cpuNano * n).memBytes(memBytes * n).build();
  }

  public ResourceClaim minus(@Nonnull ResourceClaim that) {
    return ResourceClaim.builder().cpuNano(cpuNano - that.cpuNano).memBytes(memBytes - that.memBytes).build();
  }
}
