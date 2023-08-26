/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class CfRequestConfig {
  private String orgName;
  private String spaceName;
  private String userName;
  private String password;
  private String endpointUrl;
  private String applicationName;
  private String manifestYaml;
  private int desiredCount;
  private List<String> routeMaps;
  private Map<String, String> serviceVariables;
  Map<String, String> safeDisplayServiceVariables;
  private int timeOutIntervalInMins;
  private boolean useCFCLI;
  private String cfCliPath;
  private CfCliVersion cfCliVersion;
  private String cfHomeDirPath;
  private String trailingLogsDirPath;
  private boolean loggedin;
  private boolean limitPcfThreads;
  private boolean useNumbering;
}
