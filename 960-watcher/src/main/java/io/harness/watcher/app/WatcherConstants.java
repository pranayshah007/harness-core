/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.app;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.version.VersionInfoManager;

import lombok.Data;

@Data
public class WatcherConstants {
  private final boolean isNg = isNotBlank(System.getenv().get("DELEGATE_SESSION_IDENTIFIER"))
      || (isNotBlank(System.getenv().get("NEXT_GEN")) && Boolean.parseBoolean(System.getenv().get("NEXT_GEN")));
  private final String deployMode = System.getenv().get("DEPLOY_MODE");
  private final boolean multiversion = isEmpty(deployMode) || !deployMode.equals("KUBERNETES_ONPREM");
  private final String delegateName =
      isNotBlank(System.getenv().get("DELEGATE_NAME")) ? System.getenv().get("DELEGATE_NAME") : "";
  private final String watcherJreVersion = System.getProperty("java.version");
  private final String version = new VersionInfoManager().getVersionInfo().getVersion();

  private String delegateId;
}
