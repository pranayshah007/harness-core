/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.Kind;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sLogStreamerFactory {
  @Inject private JobApiLogStreamer jobApiLogStreamer;

  public K8sLogStreamer getWorkloadLogStreamer(String kind) {
    Kind workloadKind = Kind.valueOf(kind);
    EnumMap<Kind, K8sLogStreamer> apiWorkloadLogStreamerMap = getApiK8sLogStreamerMap();
    return apiWorkloadLogStreamerMap.get(workloadKind);
  }

  private EnumMap<Kind, K8sLogStreamer> getApiK8sLogStreamerMap() {
    return new EnumMap<>(Map.of(Kind.Job, jobApiLogStreamer));
  }
}
