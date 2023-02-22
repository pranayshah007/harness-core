/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terragrunt.TerragruntConfigDAL;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class TerragruntConfigEntityCRUDEventHandler {
  private final TerragruntConfigDAL terragruntConfigDAL;

  @Inject
  public TerragruntConfigEntityCRUDEventHandler(TerragruntConfigDAL terragruntConfigDAL) {
    this.terragruntConfigDAL = terragruntConfigDAL;
  }

  public boolean deleteAssociatedTerragruntConfigForAccount(String accountIdentifier) {
    terragruntConfigDAL.deleteForAccount(accountIdentifier);
    return true;
  }

  public boolean deleteAssociatedTerragruntConfigForOrganization(String accountIdentifier, String orgIdentifier) {
    terragruntConfigDAL.deleteForOrganization(accountIdentifier, orgIdentifier);
    return true;
  }

  public boolean deleteAssociatedTerragruntConfigForProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    terragruntConfigDAL.deleteForProject(accountIdentifier, orgIdentifier, projectIdentifier);
    return true;
  }
}
