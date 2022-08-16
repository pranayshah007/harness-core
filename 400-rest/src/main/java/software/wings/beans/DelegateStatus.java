/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateConnectionDetails;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateScope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.DEL)
@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Value
@Builder
@TargetModule(HarnessModule._920_DELEGATE_SERVICE_BEANS)
public class DelegateStatus {
  List<String> publishedVersions;
  List<DelegateInner> delegates;
  List<DelegateScalingGroup> scalingGroups;

  @JsonInclude(Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Value
  @Builder
  public static class DelegateInner {
    private String uuid;
    private String ip;
    private String hostName;
    private String delegateName;
    private String delegateGroupName;
    private String description;
    private DelegateInstanceStatus status;
    private long lastHeartBeat;
    private boolean activelyConnected;
    private String delegateProfileId;
    private String delegateType;
    private boolean polllingModeEnabled;
    private boolean proxy;
    private boolean ceEnabled;
    private List<DelegateScope> includeScopes;
    private List<DelegateScope> excludeScopes;
    private List<String> tags;
    private Map<String, SelectorType> implicitSelectors;
    private long profileExecutedAt;
    private boolean profileError;
    private boolean sampleDelegate;
    private boolean grpcActive;
    private boolean tokenActive;
    private long delegateExpirationTime;
    List<DelegateConnectionDetails> connections;
  }
}
