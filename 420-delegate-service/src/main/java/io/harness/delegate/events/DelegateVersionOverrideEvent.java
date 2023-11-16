/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.events;

import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_VERSION_OVERRIDE_EVENT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.delegate.beans.VersionOverride;
import io.harness.ng.core.Resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.DEL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegateVersionOverrideEvent extends AbstractDelegateConfigurationEvent {
  private VersionOverride versionOverride;
  private VersionOverride versionOverrideOld;
  private static final String versionOverrideIdentifier = "Delegate Version Override";

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(versionOverrideIdentifier).type(ResourceTypeConstants.DELEGATE).build();
  }

  @Override
  public String getEventType() {
    return DELEGATE_VERSION_OVERRIDE_EVENT;
  }
}
