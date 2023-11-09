/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sPod {
  private String uid;
  private String name;
  private String namespace;
  private String releaseName;
  private String podIP;
  private List<K8sContainer> containerList;
  private boolean newPod;
  private Map<String, String> labels;

  @JsonIgnore
  public String getColor() {
    if (labels != null) {
      return labels.get(HarnessLabels.color);
    }

    return null;
  }

  @JsonIgnore
  public boolean isCanary() {
    if (labels == null) {
      return false;
    }

    return HarnessLabelValues.trackCanary.equals(
        labels.getOrDefault(HarnessLabels.track, HarnessLabelValues.trackStable));
  }
}
