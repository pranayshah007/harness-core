/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.VARIABLE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.variable.dto.VariableDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class VariableCreateEvent implements Event {
  public static final String VARIABLE_CREATED = "VariableCreated";
  private String accountIdentifier;
  private VariableDTO variableDTO;

  public VariableCreateEvent(String accountIdentifier, VariableDTO variableDTO) {
    this.accountIdentifier = accountIdentifier;
    this.variableDTO = variableDTO;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isNotEmpty(variableDTO.getOrgIdentifier())) {
      if (isEmpty(variableDTO.getProjectIdentifier())) {
        return new OrgScope(accountIdentifier, variableDTO.getOrgIdentifier());
      } else {
        return new ProjectScope(accountIdentifier, variableDTO.getOrgIdentifier(), variableDTO.getProjectIdentifier());
      }
    }
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, variableDTO.getName());
    return Resource.builder().identifier(variableDTO.getIdentifier()).type(VARIABLE).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return VARIABLE_CREATED;
  }
}