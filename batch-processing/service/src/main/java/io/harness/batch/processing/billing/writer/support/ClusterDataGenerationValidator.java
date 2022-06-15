/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer.support;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Get decide if need to generate billing data or not.
 */
@Component
@Slf4j
public class ClusterDataGenerationValidator {
  public boolean shouldGenerateClusterData(String accountId, String clusterId) {
    if (accountId.equals("hW63Ny6rQaaGsKkVjE0pJACBD")
        && ImmutableSet
               .of("5ee15b482aa4186d1c9c1ef6", "5ee1584f2aa4186d1c1852de", "5ee158392aa4186d1c13e6b0",
                   "5ee158b22aa4186d1c2e927e", "5ee157962aa4186d1cf6c5fe", "5ee0eeaa2aa4186d1c1b01cd",
                   "5ee0ee912aa4186d1c185979", "5e2f329e1e057d4bc6594cb0", "5e3481ec1e057d4bc630a2b0",
                   "5ece950be3084c95a0fc2222", "5e28dcc31e057d4bc69c8720", "5e3451421e057d4bc65f1ca2",
                   "5ef439dd2aa4186d1c0170d7", "5ee151302aa4186d1c02fbf0", "5e28dd851e057d4bc6afa17a",
                   "5ee13d452aa4186d1cfce5a0")
               .contains(clusterId)) {
      return false;
    }
    return true;
  }
}
