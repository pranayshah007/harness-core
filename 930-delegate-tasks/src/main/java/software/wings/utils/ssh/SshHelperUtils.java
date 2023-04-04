/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionConfig.Builder;

import software.wings.beans.SSHExecutionCredential;
import software.wings.beans.command.CommandExecutionContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 2/23/17.
 */

@Slf4j
@OwnedBy(CDP)
public class SshHelperUtils {
  public static SshSessionConfig createSshSessionConfig(String commandName, CommandExecutionContext context) {
    SSHExecutionCredential sshExecutionCredential = (SSHExecutionCredential) context.getExecutionCredential();

    String hostName = context.getHost().getPublicDns();
    Builder builder = aSshSessionConfig()
                          .withAccountId(context.getAccountId())
                          .withAppId(context.getAppId())
                          .withExecutionId(context.getActivityId())
                          .withHost(hostName)
                          .withCommandUnitName(commandName);

    // TODO: The following can be removed as we do not support username and password from context anymore
    if (sshExecutionCredential != null) {
      builder.withUserName(sshExecutionCredential.getSshUser())
          .withPassword(sshExecutionCredential.getSshPassword())
          .withSudoAppName(sshExecutionCredential.getAppAccount())
          .withSudoAppPassword(sshExecutionCredential.getAppAccountPassword());
    }

    SshDelegateHelperUtils.populateBuilderWithCredentials(
        builder, context.getHostConnectionAttributes(), context.getBastionConnectionAttributes());
    return builder.build();
  }
}
