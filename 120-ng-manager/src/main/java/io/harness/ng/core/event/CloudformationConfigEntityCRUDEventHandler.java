/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.CloudformationConfigDAL;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class CloudformationConfigEntityCRUDEventHandler {
  private final CloudformationConfigDAL cloudformationConfigDAL;

  @Inject
  public CloudformationConfigEntityCRUDEventHandler(CloudformationConfigDAL cloudformationConfigDAL) {
    this.cloudformationConfigDAL = cloudformationConfigDAL;
  }

  public boolean deleteAssociatedCloudformationConfigForAccount(String accountIdentifier) {
    cloudformationConfigDAL.deleteForAccount(accountIdentifier);
    return true;
  }

  public boolean deleteAssociatedCloudformationConfigForOrganization(String accountIdentifier, String orgIdentifier) {
    cloudformationConfigDAL.deleteForOrganization(accountIdentifier, orgIdentifier);
    return true;
  }

  public boolean deleteAssociatedCloudformationConfigForProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    cloudformationConfigDAL.deleteForProject(accountIdentifier, orgIdentifier, projectIdentifier);
    return true;
  }
}
