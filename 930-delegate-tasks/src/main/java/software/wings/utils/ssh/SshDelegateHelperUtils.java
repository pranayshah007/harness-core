/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.shell.AccessType.KEY_SUDO_APP_USER;
import static io.harness.shell.AccessType.KEY_SU_APP_USER;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.KERBEROS;
import static io.harness.shell.ExecutorType.BASTION_HOST;
import static io.harness.shell.ExecutorType.KEY_AUTH;
import static io.harness.shell.ExecutorType.PASSWORD_AUTH;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import io.harness.shell.AccessType;
import io.harness.shell.AuthenticationScheme;
import io.harness.shell.ExecutorType;
import io.harness.shell.KerberosConfig;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.BastionConnectionAttributes;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.dto.SettingAttribute;

public class SshDelegateHelperUtils {
  private static ExecutorType getExecutorType(
      SettingAttribute hostConnectionSetting, SettingAttribute bastionHostConnectionSetting) {
    ExecutorType executorType;
    if (bastionHostConnectionSetting != null) {
      executorType = BASTION_HOST;
    } else {
      HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) hostConnectionSetting.getValue();
      AccessType accessType = hostConnectionAttributes.getAccessType();
      if (accessType == AccessType.KEY || accessType == KEY_SU_APP_USER || accessType == KEY_SUDO_APP_USER) {
        executorType = KEY_AUTH;
      } else {
        executorType = PASSWORD_AUTH;
      }
    }
    return executorType;
  }

  public static SshSessionConfig createSshSessionConfig(SettingAttribute settingAttribute, String hostName) {
    SshSessionConfig.Builder builder =
        aSshSessionConfig().withAccountId(settingAttribute.getAccountId()).withHost(hostName);
    populateBuilderWithCredentials(builder, settingAttribute, null);
    return builder.build();
  }

  public static void populateBuilderWithCredentials(SshSessionConfig.Builder builder,
      SettingAttribute hostConnectionSetting, SettingAttribute bastionHostConnectionSetting) {
    ExecutorType executorType = getExecutorType(hostConnectionSetting, bastionHostConnectionSetting);

    builder.withExecutorType(executorType);
    HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) hostConnectionSetting.getValue();

    if (executorType == KEY_AUTH) {
      if (isNotEmpty(hostConnectionAttributes.getKey())) {
        builder.withKey(new String(hostConnectionAttributes.getKey()).toCharArray());
      }

      if (isNotEmpty(hostConnectionAttributes.getPassphrase())) {
        builder.withKeyPassphrase(new String(hostConnectionAttributes.getPassphrase()).toCharArray());
      }

      builder.withUserName(hostConnectionAttributes.getUserName())
          .withPort(hostConnectionAttributes.getSshPort())
          .withKeyName(hostConnectionSetting.getUuid())
          .withPassword(null)
          .withKeyLess(hostConnectionAttributes.isKeyless())
          .withKeyPath(hostConnectionAttributes.getKeyPath())
          .withVaultSSH(hostConnectionAttributes.isVaultSSH())
          .withPublicKey(hostConnectionAttributes.getPublicKey())
          .withSignedPublicKey(hostConnectionAttributes.getSignedPublicKey());
    } else if (KERBEROS == hostConnectionAttributes.getAuthenticationScheme()) {
      KerberosConfig kerberosConfig = hostConnectionAttributes.getKerberosConfig();

      if (isNotEmpty(hostConnectionAttributes.getKerberosPassword())) {
        builder.withPassword(new String(hostConnectionAttributes.getKerberosPassword()).toCharArray());
      }

      builder.withAuthenticationScheme(KERBEROS)
          .withKerberosConfig(kerberosConfig)
          .withPort(hostConnectionAttributes.getSshPort());
    } else if (USER_PASSWORD == hostConnectionAttributes.getAccessType()) {
      if (isNotEmpty(hostConnectionAttributes.getSshPassword())) {
        builder.withSshPassword(new String(hostConnectionAttributes.getSshPassword()).toCharArray());
      }

      builder.withAuthenticationScheme(AuthenticationScheme.SSH_KEY)
          .withAccessType(hostConnectionAttributes.getAccessType())
          .withUserName(hostConnectionAttributes.getUserName())
          .withPort(hostConnectionAttributes.getSshPort());
    }

    if (bastionHostConnectionSetting != null) {
      BastionConnectionAttributes bastionAttrs = (BastionConnectionAttributes) bastionHostConnectionSetting.getValue();
      SshSessionConfig.Builder sshSessionConfig = aSshSessionConfig()
                                                      .withHost(bastionAttrs.getHostName())
                                                      .withKeyName(bastionHostConnectionSetting.getUuid())
                                                      .withUserName(bastionAttrs.getUserName())
                                                      .withPort(bastionAttrs.getSshPort());

      if (isNotEmpty(bastionAttrs.getKey())) {
        sshSessionConfig.withKey(new String(bastionAttrs.getKey()).toCharArray());
      }

      if (isNotEmpty(bastionAttrs.getPassphrase())) {
        sshSessionConfig.withKeyPassphrase(new String(bastionAttrs.getPassphrase()).toCharArray());
      }

      builder.withBastionHostConfig(sshSessionConfig.build());
    }
  }
}
