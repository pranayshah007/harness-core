/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class CfRenameRequest extends CfRequestConfig {
  private String guid;
  private String name;
  private String newName;

  public CfRenameRequest(CfRequestConfig requestConfig, String guid, String name, String newName) {
    super(requestConfig.getOrgName(), requestConfig.getSpaceName(), requestConfig.getUserName(),
        requestConfig.getPassword(), requestConfig.getEndpointUrl(), requestConfig.getApplicationName(),
        requestConfig.getManifestYaml(), requestConfig.getDesiredCount(), requestConfig.getRouteMaps(),
        requestConfig.getServiceVariables(), requestConfig.getSafeDisplayServiceVariables(),
        requestConfig.getTimeOutIntervalInMins(), requestConfig.isUseCFCLI(), requestConfig.getCfCliPath(),
        requestConfig.getCfCliVersion(), requestConfig.getCfHomeDirPath(), requestConfig.getTrailingLogsDirPath(),
        requestConfig.isLoggedin(), requestConfig.isLimitPcfThreads(), requestConfig.isUseNumbering());
    this.guid = guid;
    this.name = name;
    this.newName = newName;
  }
}
