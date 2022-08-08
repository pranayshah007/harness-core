/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

public class RolloutCommand extends AbstractExecutable {
  private final Kubectl client;

  public RolloutCommand(Kubectl client) {
    this.client = client;
  }

  public RolloutStatusCommand status() {
    return new RolloutStatusCommand(this);
  }

  public RolloutHistoryCommand history() {
    return new RolloutHistoryCommand(this);
  }

  public RolloutUndoCommand undo() {
    return new RolloutUndoCommand(this);
  }

  @Override
  public String command() {
    StringBuilder command = new StringBuilder();
    command.append(client.command()).append("rollout ");

    return command.toString();
  }
}
