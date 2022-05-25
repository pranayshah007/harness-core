/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KubernetesChangeEventMetadata extends ChangeEventMetadata {
  String oldYaml;
  String newYaml;
  Instant timestamp;
  String workload;
  String namespace;
  String kind;
  KubernetesResourceType resourceType;
  Action action;
  String reason;
  String message;
  String resourceVersion;
  public enum Action { Add, Update, Delete }
  public enum KubernetesResourceType { Deployment, ReplicaSet, Secret, Pod, ConfigMap, StatefulSet }

  @Override
  public ChangeSourceType getType() {
    return ChangeSourceType.KUBERNETES;
  }
}
