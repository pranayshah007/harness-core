/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.IP_ALLOWLIST_CONFIG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class IPAllowlistConfigUpdateEvent implements Event {
  public static final String IP_ALLOWLIST_CONFIG_UPDATED = "IPAllowlistConfigUpdated";
  String accountIdentifier;
  IPAllowlistConfig newIpAllowlistConfig;
  IPAllowlistConfig oldIpAllowlistConfig;

  public IPAllowlistConfigUpdateEvent(
      String accountIdentifier, IPAllowlistConfig newIpAllowlistConfig, IPAllowlistConfig oldIpAllowlistConfig) {
    this.accountIdentifier = accountIdentifier;
    this.newIpAllowlistConfig = newIpAllowlistConfig;
    this.oldIpAllowlistConfig = oldIpAllowlistConfig;
  }
  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }
  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, newIpAllowlistConfig.getName());
    return Resource.builder()
        .identifier(newIpAllowlistConfig.getIdentifier())
        .type(IP_ALLOWLIST_CONFIG)
        .labels(labels)
        .build();
  }
  @JsonIgnore
  @Override
  public String getEventType() {
    return IP_ALLOWLIST_CONFIG_UPDATED;
  }
}
