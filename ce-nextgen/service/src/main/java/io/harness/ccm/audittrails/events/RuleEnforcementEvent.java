/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import static io.harness.audit.ResourceTypeConstants.CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT;

import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor

public abstract class RuleEnforcementEvent implements Event {
  private RuleEnforcement ruleEnforcement;
  private String accountIdentifier;

  protected RuleEnforcementEvent(String accountIdentifier, RuleEnforcement ruleEnforcement) {
    this.accountIdentifier = accountIdentifier;
    this.ruleEnforcement = ruleEnforcement;
  }

  @Override
  @JsonIgnore
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  @JsonIgnore
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, ruleEnforcement.getName());
    return Resource.builder()
        .identifier(ruleEnforcement.getUuid())
        .type(CLOUD_ASSET_GOVERNANCE_RULE_ENFORCEMENT)
        .labels(labels)
        .build();
  }
}
