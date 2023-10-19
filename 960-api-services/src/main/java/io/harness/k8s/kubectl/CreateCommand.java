/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public class CreateCommand extends AbstractExecutable {
  private final Kubectl client;
  private final String createdManifestName;

  public CreateCommand(Kubectl client, String createdManifestName) {
    this.client = client;
    this.createdManifestName = createdManifestName;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder(128);
    command.append(client.command())
        .append("create -f ")
        .append(this.createdManifestName)
        .append(" -o=yaml ")
        .append(Kubectl.flag(Flag.dryrun.getForVersion(client.getVersion(), client.getClientType())));
    return command.toString().trim();
  }
}
