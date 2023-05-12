/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.EntityReferenceHelper;

import java.util.Arrays;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetReference implements EntityReference {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  // inputSet identifier
  String identifier;
  String repoIdentifier;
  String branch;
  Boolean isDefault;

  @Override
  public String getFullyQualifiedName() {
    return EntityReferenceHelper.createFQN(
        Arrays.asList(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier));
  }

  @Override
  public Boolean isDefault() {
    return isDefault;
  }

  @Override
  public void setBranch(String branch) {
    this.branch = branch;
  }

  @Override
  public void setRepoIdentifier(String repoIdentifier) {
    this.repoIdentifier = repoIdentifier;
  }

  @Override
  public void setIsDefault(Boolean isDefault) {
    this.isDefault = isDefault;
  }
}
