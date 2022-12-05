/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.winrm.AuthenticationScheme.NTLM;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript.ScriptType.BASH;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;
import static software.wings.utils.WingsTestConstants.DOMAIN;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import io.harness.data.structure.UUIDGenerator;
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.shell.AccessType;
import io.harness.shell.ScriptType;

import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig.CustomSecretsManagerConfigBuilder;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript;
import software.wings.settings.SettingVariableTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class CustomSecretsManagerUtils {
  static CustomSecretsManagerConfig obtainConfig(SettingVariableTypes type) {
    CustomSecretsManagerShellScript script = CustomSecretsManagerShellScript.builder()
                                                 .scriptType(BASH)
                                                 .scriptString(UUIDGenerator.generateUuid())
                                                 .variables(new ArrayList<>())
                                                 .build();
    CustomSecretsManagerConfigBuilder configBuilder = CustomSecretsManagerConfig.builder()
                                                          .name("CustomSecretsManager")
                                                          .templateId(UUIDGenerator.generateUuid())
                                                          .delegateSelectors(new HashSet<>())
                                                          .executeOnDelegate(true)
                                                          .isConnectorTemplatized(false)
                                                          .testVariables(obtainTestVariables(null))
                                                          .customSecretsManagerShellScript(script);

    if (type == HOST_CONNECTION_ATTRIBUTES) {
      SettingAttribute settingAttribute = obtainSSHSettingAttributeConfig();
      configBuilder.executeOnDelegate(false);
      configBuilder.remoteHostConnector((HostConnectionAttributes) settingAttribute.getValue());
    } else if (type == WINRM_CONNECTION_ATTRIBUTES) {
      SettingAttribute settingAttribute = obtainWinRmSettingAttributeConfig();
      configBuilder.executeOnDelegate(false);
      configBuilder.remoteHostConnector((WinRmConnectionAttributes) settingAttribute.getValue());
    }
    return configBuilder.build();
  }

  static Template obtainTemplateConfig(ScriptType scriptType) {
    ShellScriptTemplate shellScriptTemplate = ShellScriptTemplate.builder()
                                                  .scriptType(scriptType.name())
                                                  .scriptString("echo ${var1}\n"
                                                      + "export A=\"aaa\"\n"
                                                      + "export B=\"bbb\"")
                                                  .outputVars("A,B")
                                                  .build();
    return Template.builder()
        .templateObject(shellScriptTemplate)
        .folderPath("Harness/Tomcat Commands")
        .gallery(HARNESS_GALLERY)
        .appId(GLOBAL_APP_ID)
        .accountId(GLOBAL_ACCOUNT_ID)
        .name("Sample Script")
        .variables(Collections.singletonList(aVariable().type(TEXT).name("var1").mandatory(true).build()))
        .build();
  }

  static SettingAttribute obtainSSHSettingAttributeConfig() {
    return aSettingAttribute()
        .withCategory(SettingAttribute.SettingCategory.SETTING)
        .withName("hostConnectionAttrs")
        .withAccountId(GLOBAL_ACCOUNT_ID)
        .withValue(aHostConnectionAttributes()
                       .withAccessType(AccessType.USER_PASSWORD)
                       .withAccountId(GLOBAL_ACCOUNT_ID)
                       .build())
        .build();
  }

  static SettingAttribute obtainWinRmSettingAttributeConfig() {
    return aSettingAttribute()
        .withCategory(SettingAttribute.SettingCategory.SETTING)
        .withName("winrmConnectionAttr")
        .withAccountId(GLOBAL_ACCOUNT_ID)
        .withValue(WinRmConnectionAttributes.builder()
                       .accountId(GLOBAL_ACCOUNT_ID)
                       .authenticationScheme(NTLM)
                       .username(USER_NAME)
                       .password(PASSWORD)
                       .domain(DOMAIN)
                       .port(22)
                       .useSSL(true)
                       .skipCertChecks(true)
                       .build())
        .build();
  }

  static Set<EncryptedDataParams> obtainTestVariables(String connectorId) {
    Set<EncryptedDataParams> testVariables = new HashSet<>();
    testVariables.add(EncryptedDataParams.builder().name("var1").value("value").build());
    if (!isEmpty(connectorId)) {
      testVariables.add(EncryptedDataParams.builder().name("connectorId").value(connectorId).build());
    }
    return testVariables;
  }
}
