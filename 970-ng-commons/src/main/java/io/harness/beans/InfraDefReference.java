/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.EntityReference;
import io.harness.common.EntityReferenceHelper;

import java.util.Arrays;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class InfraDefReference implements EntityReference {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String envIdentifier;
  String identifier;

  String repoIdentifier;
  String branch;
  Boolean isDefault;

  @Override
  public String getFullyQualifiedName() {
    return EntityReferenceHelper.createFQN(
        Arrays.asList(accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, identifier));
  }

  @Override
  public Boolean isDefault() {
    return isDefault;
  }
}
