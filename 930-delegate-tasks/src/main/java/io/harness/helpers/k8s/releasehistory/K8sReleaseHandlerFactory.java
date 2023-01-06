/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class K8sReleaseHandlerFactory {
  @Inject private final K8sLegacyReleaseHandlerImpl legacyReleaseHandler;
  @Inject private final K8sReleaseHandlerImpl releaseHandler;

  public K8sReleaseHandler getK8sReleaseHandler(boolean useDeclarativeRollback) {
    if (useDeclarativeRollback) {
      return releaseHandler;
    }
    return legacyReleaseHandler;
  }
}
