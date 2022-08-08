/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE;

import io.harness.ccm.views.entities.CEView;
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
public abstract class PerspectiveEvent implements Event {
  private CEView perspectiveDTO;
  private String accountIdentifier;

  public PerspectiveEvent(String accountIdentifier, CEView perspectiveDTO) {
    this.accountIdentifier = accountIdentifier;
    this.perspectiveDTO = perspectiveDTO;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, perspectiveDTO.getName());
    return Resource.builder().identifier(perspectiveDTO.getUuid()).type(PERSPECTIVE).labels(labels).build();
  }
}
