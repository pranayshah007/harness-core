/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.loginSettings.events;

import static software.wings.beans.loginSettings.LoginSettingsConstants.HARNESS_USERNAME_PASSWORD_UPDATED;
import static software.wings.beans.loginSettings.LoginSettingsConstants.RESOURCE_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.PL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginSettingsHarnessUsernamePasswordUpdateEvent implements Event {
  private String accountIdentifier;
  private String loginSettingsId;
  private LoginSettingsYamlDTO oldLoginSettingsYamlDTO;
  private LoginSettingsYamlDTO newLoginSettingsYamlDTO;

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, RESOURCE_NAME);
    return Resource.builder()
        .identifier(loginSettingsId)
        .labels(labels)
        .type(ResourceTypeConstants.NG_LOGIN_SETTINGS)
        .build();
  }

  @Override
  public String getEventType() {
    return HARNESS_USERNAME_PASSWORD_UPDATED;
  }
}
