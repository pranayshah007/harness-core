/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import software.wings.delegatetasks.terraform.TerraformConfigInspectClient;
import software.wings.service.intfc.TerraformConfigInspectService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@OwnedBy(CDP)
public class TerraformConfigInspectServiceImpl implements TerraformConfigInspectService {
  @Inject private TerraformConfigInspectClient terraformConfigInspectClient;

  @Override
  public List<String> parseFieldsUnderCategory(String directory, String category, boolean useLatestVersion) {
    return new ArrayList<>(terraformConfigInspectClient.parseFieldsUnderBlock(directory, category, useLatestVersion));
  }
}
