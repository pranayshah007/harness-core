/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SelectorType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.DEL)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Value
@Builder
public class DelegateGroupDetails {
  private String groupId;
  private String delegateGroupIdentifier;
  private String delegateType;
  private String groupName;
  private String delegateDescription;
  private String delegateConfigurationId;
  private Map<String, SelectorType> groupImplicitSelectors;
  private Set<String> groupCustomSelectors;
  private long lastHeartBeat;
  private String connectivityStatus;
  private boolean activelyConnected;
  private boolean grpcActive;
  private List<DelegateGroupListing.DelegateInner> delegateInstanceDetails;
  private boolean tokenActive;
}
