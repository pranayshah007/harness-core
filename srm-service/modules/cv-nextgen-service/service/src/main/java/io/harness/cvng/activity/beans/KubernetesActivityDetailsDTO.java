/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.beans;

import io.harness.cvng.beans.activity.KubernetesActivityDTO.KubernetesEventType;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class KubernetesActivityDetailsDTO {
  String sourceName;
  String connectorIdentifier;
  String workload;
  String kind;
  String namespace;
  @Singular List<KubernetesActivityDetail> details;

  @Value
  @Builder
  public static class KubernetesActivityDetail {
    long timeStamp;
    KubernetesEventType eventType;
    String reason;
    String message;
    String eventJson;
  }
}
