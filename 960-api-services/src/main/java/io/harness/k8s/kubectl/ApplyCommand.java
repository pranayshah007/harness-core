/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class ApplyCommand extends AbstractExecutable {
  private Kubectl client;
  private String filename;
  private String namespace;
  private boolean dryrun;
  private boolean record;
  private String output;
  private String commandFlags;
  public ApplyCommand(Kubectl client) {
    this.client = client;
  }
  public ApplyCommand filename(String filename) {
    this.filename = filename;
    return this;
  }

  public ApplyCommand namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public ApplyCommand dryrun(boolean dryrun) {
    this.dryrun = dryrun;
    return this;
  }

  public ApplyCommand record(boolean record) {
    this.record = record;
    return this;
  }

  public ApplyCommand output(String output) {
    this.output = output;
    return this;
  }

  public ApplyCommand commandFlags(String commandFlags) {
    this.commandFlags = commandFlags;
    return this;
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("apply ");

    if (StringUtils.isNotBlank(this.filename)) {
      command.append(Kubectl.option(Option.filename, encloseWithQuotesIfNeeded(this.filename)));
    }

    if (StringUtils.isNotBlank(this.namespace)) {
      command.append(Kubectl.option(Option.namespace, this.namespace));
    }

    if (this.dryrun) {
      command.append(Kubectl.flag(Flag.dryrun.getForVersion(client.getVersion(), client.getClientType())));
    }

    if (this.record) {
      command.append(Kubectl.flag(Flag.record.getForVersion(client.getVersion(), client.getClientType())));
    }
    if (isNotEmpty(this.commandFlags)) {
      command.append(Kubectl.flag(this.commandFlags));
    }
    if (this.output != null) {
      command.append(Kubectl.option(Option.output, output));
    }
    return command.toString().trim();
  }
}
