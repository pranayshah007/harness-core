/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.intfc;

import io.harness.delegate.beans.DelegateEntityOwner;

import java.io.IOException;

public interface DelegateInstallationCommandService {
  String getCommand(
      String commandType, String managerUrl, String accountId, DelegateEntityOwner owner, String os, String arch);
  String getTerraformExampleModuleFile(String managerUrl, String accountId, DelegateEntityOwner owner)
      throws IOException;

  String getHelmRepoUrl(String commandType, String managerUrl);
}
