/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class KustomizeCommand extends AbstractExecutable {
  private final Kubectl client;
  private final String kustomizeDirPath;
  private Map<String, String> commandFlags;
  private String pluginDir;
  public KustomizeCommand(Kubectl client, String kustomizeDirPath) {
    this.client = client;
    this.kustomizeDirPath = kustomizeDirPath;
  }

  public KustomizeCommand commandFlags(Map<String, String> commandFlags) {
    this.commandFlags = commandFlags;
    return this;
  }

  public KustomizeCommand withPlugin(String pluginDir) {
    this.pluginDir = pluginDir;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("kustomize ").append(this.kustomizeDirPath).append(' ');

    if (isNotEmpty(pluginDir)) {
      command.insert(0, format("XDG_CONFIG_HOME=\"%s\" ", pluginDir));
      command.append(Kubectl.flag(Flag.enableAlphaPlugins));
    }

    if (isNotEmpty(this.commandFlags)) {
      command.append(this.commandFlags.getOrDefault("BUILD", ""));
    }
    return command.toString().trim();
  }
}
