/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.expression.Expression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@OwnedBy(CDP)
public abstract class CommandTaskParameters implements TaskParameters {
  String accountId;
  String executionId;
  @Default @Expression(ALLOW_SECRETS) Map<String, String> environmentVariables = new HashMap<>();
  boolean executeOnDelegate;
  List<NgCommandUnit> commandUnits;
  SshWinRmArtifactDelegateConfig artifactDelegateConfig;
  FileDelegateConfig fileDelegateConfig;
}
