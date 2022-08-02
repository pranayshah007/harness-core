/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.SETTING;
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
import io.harness.ngsettings.dto.SettingDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class SettingUpdateEvent implements Event {
  public static final String SETTING_UPDATED = "SettingUpdated";
  private String accountIdentifier;
  private SettingDTO currentSettingDTO;
  private SettingDTO updatedSettingDTO;

  public SettingUpdateEvent(String accountIdentifier, SettingDTO currentSettingDTO, SettingDTO updatedSettingDTO) {
    this.accountIdentifier = accountIdentifier;
    this.currentSettingDTO = currentSettingDTO;
    this.updatedSettingDTO = updatedSettingDTO;
  }
  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isNotEmpty(updatedSettingDTO.getOrgIdentifier())) {
      if (isEmpty(updatedSettingDTO.getProjectIdentifier())) {
        return new OrgScope(accountIdentifier, updatedSettingDTO.getOrgIdentifier());
      } else {
        return new ProjectScope(
            accountIdentifier, updatedSettingDTO.getOrgIdentifier(), updatedSettingDTO.getProjectIdentifier());
      }
    }
    return new AccountScope(accountIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, updatedSettingDTO.getName());
    return Resource.builder().identifier(updatedSettingDTO.getIdentifier()).type(SETTING).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return SETTING_UPDATED;
  }
}
