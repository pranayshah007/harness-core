/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.terraform.TerraformConfigDAL;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class TerraformConfigEntityCRUDEventHandler {
  private final TerraformConfigDAL terraformConfigDAL;

  @Inject
  public TerraformConfigEntityCRUDEventHandler(TerraformConfigDAL terraformConfigDAL) {
    this.terraformConfigDAL = terraformConfigDAL;
  }

  public boolean deleteAssociatedTerraformConfigForAccount(String accountIdentifier) {
    terraformConfigDAL.deleteForAccount(accountIdentifier);
    return true;
  }

  public boolean deleteAssociatedTerraformConfigForOrganization(String accountIdentifier, String orgIdentifier) {
    terraformConfigDAL.deleteForOrganization(accountIdentifier, orgIdentifier);
    return true;
  }

  public boolean deleteAssociatedTerraformConfigForProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    terraformConfigDAL.deleteForProject(accountIdentifier, orgIdentifier, projectIdentifier);
    return true;
  }
}
