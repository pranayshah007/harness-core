/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(DEL)
public class DelegateListResponse {
  private String type;
  private String name;
  private String description;
  private Set<String> tags;
  private long lastHeartBeat;
  private boolean connected;
  private List<DelegateReplica> delegateReplicas;
  private AutoUpgrade autoUpgrade;
  private boolean legacy;

  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  public static class DelegateReplica {
    private String uuid;
    private long lastHeartbeat;
    private boolean connected;
    private String hostName;
    private String version;
    private long expiringAt;
  }
}
