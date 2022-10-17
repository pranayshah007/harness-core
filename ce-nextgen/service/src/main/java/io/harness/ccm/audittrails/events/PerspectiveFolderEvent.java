/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE_FOLDER;

import io.harness.ccm.views.entities.CEViewFolder;
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
public abstract class PerspectiveFolderEvent implements Event {
  private CEViewFolder perspectiveFolderDTO;
  private String accountIdentifier;

  public PerspectiveFolderEvent(String accountIdentifier, CEViewFolder perspectiveFolderDTO) {
    this.accountIdentifier = accountIdentifier;
    this.perspectiveFolderDTO = perspectiveFolderDTO;
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
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, perspectiveFolderDTO.getName());
    return Resource.builder()
        .identifier(perspectiveFolderDTO.getUuid())
        .type(PERSPECTIVE_FOLDER)
        .labels(labels)
        .build();
  }
}
