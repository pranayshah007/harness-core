/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateGroupListing {
  List<DelegateGroupDetails> delegateGroupDetails;

  @JsonInclude(Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Value
  @Builder
  public static class DelegateInner {
    private String uuid;
    // lastHeartbeat, activelyConnected and hostName is used only in case of NG.
    private long lastHeartbeat;
    private boolean activelyConnected;
    private String hostName;
    private boolean tokenActive;
    private String version;
    private long delegateExpirationTime;
    List<DelegateConnectionDetails> connections;
  }
}
