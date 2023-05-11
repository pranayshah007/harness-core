/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class KubernetesReleaseDetails {
  int releaseNumber;
  String color;

  public Map<String, String> toContextMap() {
    Map<String, String> context = new HashMap<>();
    context.put(KubernetesPlaceholder.REVISION_NUMBER.getPlaceholder(), String.valueOf(releaseNumber));
    if (isNotEmpty(color)) {
      context.put(KubernetesPlaceholder.RELEASE_COLOR.getPlaceholder(), color);
    }

    return context;
  }
}
