/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.NGAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
@ApiModel(value = "EntityReference",
    subTypes = {IdentifierRef.class, InputSetReference.class, NGTemplateReference.class, TriggerReference.class},
    discriminator = "type")
public interface EntityReference extends NGAccess {
  @JsonIgnore String getFullyQualifiedName();
  String getBranch();
  String getRepoIdentifier();
  Boolean isDefault();

  void setBranch(String branch);
  void setRepoIdentifier(String repoIdentifier);
  void setIsDefault(Boolean isDefault);
  default Map<String, String> getMetadata() {
    return Map.of();
  }
}
