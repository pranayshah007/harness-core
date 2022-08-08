/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.encryption.EncryptionType.CUSTOM;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.command.CommandType.START;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.common.TemplateConstants.COMMAND_PATH;
import static software.wings.common.TemplateConstants.HARNESS_GALLERY;
import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerUtils.obtainSSHSettingAttributeConfig;
import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerUtils.obtainTestVariables;
import static software.wings.service.impl.security.customsecretsmanager.CustomSecretsManagerUtils.obtainWinRmSettingAttributeConfig;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_CUSTOM_KEYWORD;
import static software.wings.utils.TemplateTestConstants.TEMPLATE_DESC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.UnexpectedException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.shell.ScriptType;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.CustomSecretsManagerService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.template.TemplateService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
@OwnedBy(HarnessTeam.PL)
@TargetModule(_360_CG_MANAGER)
public class CustomSecretsManagerServiceImplTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private CustomEncryptorsRegistry customEncryptorsRegistry;
  @Mock private CustomEncryptor customEncryptor;
  @Inject @InjectMocks private TemplateService templateService;
  @Inject @InjectMocks private TemplateGalleryService templateGalleryService;
  @Inject @InjectMocks private CustomSecretsManagerService customSecretsManagerService;
  @Inject @InjectMocks private CustomSecretsManagerServiceImpl customSecretsManagerServiceImpl;
  @Inject private HPersistence persistence;

  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    Account account = getAccount(AccountType.PAID);
    account.setUuid(GLOBAL_ACCOUNT_ID);
    when(accountService.get(GLOBAL_ACCOUNT_ID)).thenReturn(account);
    templateGalleryService.loadHarnessGallery();
    when(customEncryptorsRegistry.getCustomEncryptor(CUSTOM)).thenReturn(customEncryptor);
    FieldUtils.writeField(customSecretsManagerService, "customEncryptorsRegistry", customEncryptorsRegistry, true);
  }

  private String obtainTemplate(ScriptType scriptType) {
    Template template = CustomSecretsManagerUtils.obtainTemplateConfig(scriptType);
    return templateService.save(template).getUuid();
  }

  private String obtainCommandTemplate() {
    SshCommandTemplate sshCommandTemplate =
        SshCommandTemplate.builder()
            .commandType(START)
            .commandUnits(Collections.singletonList(anExecCommandUnit()
                                                        .withName("Start")
                                                        .withCommandPath("/home/xxx/tomcat")
                                                        .withCommandString("bin/startup.sh")
                                                        .build()))
            .build();

    Template template = Template.builder()
                            .templateObject(sshCommandTemplate)
                            .name("name")
                            .description(TEMPLATE_DESC)
                            .folderPath("Harness/Tomcat Commands")
                            .keywords(ImmutableSet.of(TEMPLATE_CUSTOM_KEYWORD))
                            .gallery(HARNESS_GALLERY)
                            .appId(GLOBAL_APP_ID)
                            .accountId(GLOBAL_ACCOUNT_ID)
                            .build();
    return templateService.save(template).getUuid();
  }

  private String obtainSshSettingAttribute() {
    SettingAttribute settingAttribute = obtainSSHSettingAttributeConfig();
    return persistence.save(settingAttribute);
  }

  private String obtainWinrmSettingAttribute() {
    SettingAttribute settingAttribute = obtainWinRmSettingAttributeConfig();
    return persistence.save(settingAttribute);
  }

  private Set<String> obtainDelegateSelectors() {
    Set<String> set = new HashSet<>();
    set.add("test1");
    set.add("test2");
    return set;
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldPass() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isNull();
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("/tmp");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldFail_TestParamsMissing() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = new HashSet<>();

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(new HashSet<>())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldFail_MissingTemplate() {
    Set<EncryptedDataParams> testVariables = new HashSet<>();

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .delegateSelectors(new HashSet<>())
                                            .templateId(generateUuid())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldFail_IncorrectTemplate() {
    String templateId = obtainCommandTemplate();
    Set<EncryptedDataParams> testVariables = new HashSet<>();

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .delegateSelectors(new HashSet<>())
                                            .templateId(templateId)
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldFail_IncorrectVariables() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = new HashSet<>();
    testVariables.add(EncryptedDataParams.builder().name("incorrectKey").value("value").build());

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .delegateSelectors(new HashSet<>())
                                            .templateId(templateId)
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerExecuteOnDelegate_shouldFail_IncorrectName() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager@")
                                            .delegateSelectors(new HashSet<>())
                                            .templateId(templateId)
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithSSHConnectorNotTemplatized_shouldSucceed() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isEqualTo(config.getHost());
    assertThat(savedConfig.getConnectorId()).isEqualTo(config.getConnectorId());
    assertThat(savedConfig.getCommandPath()).isEqualTo("/tmp");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithWinRMConnectorNotTemplatized_shouldSucceed() {
    String connectorId = obtainWinrmSettingAttribute();
    String templateId = obtainTemplate(ScriptType.POWERSHELL);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isEqualTo(config.getHost());
    assertThat(savedConfig.getConnectorId()).isEqualTo(config.getConnectorId());
    assertThat(savedConfig.getCommandPath()).isEqualTo("%TEMP%");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorNotTemplatized_shouldFail_ConnectorNotFound() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(new HashSet<>())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(generateUuid())
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorNotTemplatized_shouldFail_WrongConnectorType1() {
    String connectorId = obtainWinrmSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(new HashSet<>())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorNotTemplatized_shouldFail_WrongConnectorType2() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.POWERSHELL);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(new HashSet<>())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorNotTemplatized_shouldFail_MissingTargetHost() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(new HashSet<>())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorTemplatized_shouldSucceed() {
    String connectorId = obtainWinrmSettingAttribute();
    String templateId = obtainTemplate(ScriptType.POWERSHELL);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(connectorId);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(true)
                                            .host("app.harness.io")
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isEqualTo(config.getHost());
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("%TEMP%");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void saveSecretsManagerWithConnectorTemplatized_shouldFail_missingConnectorInParams() {
    String templateId = obtainTemplate(ScriptType.POWERSHELL);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(true)
                                            .host("app.harness.io")
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isEqualTo(config.getHost());
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("%TEMP%");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void updateSecretsManager_shouldSucceed() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isNull();
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("/tmp");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();

    config.setUuid(savedConfig.getUuid());
    config.setName("Edited name");

    configId = customSecretsManagerService.updateSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    savedConfig = persistence.get(CustomSecretsManagerConfig.class, configId);
    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isNull();
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("/tmp");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test(expected = NoResultFoundException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void updateSecretsManager_shouldFail_ConfigNotFound() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    config.setUuid(generateUuid());
    customSecretsManagerService.updateSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void updateSecretsManager_shouldFail_IdisNull() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    customSecretsManagerService.updateSecretsManager(GLOBAL_ACCOUNT_ID, config);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void getSecretsManager_shouldPass() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    CustomSecretsManagerConfig savedConfig = customSecretsManagerService.getSecretsManager(GLOBAL_ACCOUNT_ID, configId);

    assertThat(savedConfig.getName()).isEqualTo(config.getName());
    assertThat(savedConfig.getTemplateId()).isEqualTo(config.getTemplateId());
    assertThat(savedConfig.getDelegateSelectors()).isEqualTo(config.getDelegateSelectors());
    assertThat(savedConfig.isExecuteOnDelegate()).isEqualTo(config.isExecuteOnDelegate());
    assertThat(savedConfig.isConnectorTemplatized()).isEqualTo(config.isConnectorTemplatized());
    assertThat(savedConfig.getHost()).isNull();
    assertThat(savedConfig.getConnectorId()).isNull();
    assertThat(savedConfig.getCommandPath()).isEqualTo("/tmp");
    assertThat(savedConfig.getTestVariables()).isEqualTo(testVariables);
    assertThat(savedConfig.getCustomSecretsManagerShellScript()).isNotNull();
    assertThat(savedConfig.getRemoteHostConnector()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void deleteSecretsManager_shouldPass() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    boolean isDeleted = customSecretsManagerService.deleteSecretsManager(GLOBAL_ACCOUNT_ID, configId);
    assertThat(isDeleted).isTrue();
  }

  @Test(expected = SecretManagementException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void deleteSecretsManager_shouldFail_SecretsFound() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    String configId = customSecretsManagerService.saveSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(configId).isNotNull();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .name("name")
                                      .accountId(GLOBAL_ACCOUNT_ID)
                                      .kmsId(configId)
                                      .encryptionType(CUSTOM)
                                      .build();
    persistence.save(encryptedData);
    customSecretsManagerService.deleteSecretsManager(GLOBAL_ACCOUNT_ID, configId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void validateSecretsManagerExecuteOnDelegate_shouldPass() {
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(true)
                                            .isConnectorTemplatized(false)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    boolean valid = customSecretsManagerService.validateSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(valid).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void validateSecretsManagerWithConnector_shouldPass() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);

    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .templateId(templateId)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();
    config.setDefault(false);
    boolean valid = customSecretsManagerService.validateSecretsManager(GLOBAL_ACCOUNT_ID, config);
    assertThat(valid).isTrue();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void validateInternalWithSkipValidation() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);
    CustomSecretsManagerShellScript customSecretsManagerShellScript =
        CustomSecretsManagerShellScript.builder()
            .variables(
                new ArrayList<>(testVariables.stream().map(EncryptedDataParams::getName).collect(Collectors.toSet())))
            .scriptType(CustomSecretsManagerShellScript.ScriptType.BASH)
            .scriptString(COMMAND_PATH)
            .build();
    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .accountId(GLOBAL_ACCOUNT_ID)
                                            .skipValidation(true)
                                            .templateId(templateId)
                                            .commandPath(COMMAND_PATH)
                                            .customSecretsManagerShellScript(customSecretsManagerShellScript)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();

    customSecretsManagerServiceImpl.validateInternal(config, testVariables, true);
    verify(customEncryptor, never()).validateReference(any(), any(), any());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void validateInternalWithoutSkipValidation() {
    String connectorId = obtainSshSettingAttribute();
    String templateId = obtainTemplate(ScriptType.BASH);
    Set<EncryptedDataParams> testVariables = obtainTestVariables(null);
    CustomSecretsManagerShellScript customSecretsManagerShellScript =
        CustomSecretsManagerShellScript.builder()
            .variables(
                new ArrayList<>(testVariables.stream().map(EncryptedDataParams::getName).collect(Collectors.toSet())))
            .scriptType(CustomSecretsManagerShellScript.ScriptType.BASH)
            .scriptString(COMMAND_PATH)
            .build();
    CustomSecretsManagerConfig config = CustomSecretsManagerConfig.builder()
                                            .name("CustomSecretsManager")
                                            .accountId(GLOBAL_ACCOUNT_ID)
                                            .skipValidation(false)
                                            .templateId(templateId)
                                            .commandPath(COMMAND_PATH)
                                            .customSecretsManagerShellScript(customSecretsManagerShellScript)
                                            .delegateSelectors(obtainDelegateSelectors())
                                            .executeOnDelegate(false)
                                            .isConnectorTemplatized(false)
                                            .host("app.harness.io")
                                            .connectorId(connectorId)
                                            .testVariables(testVariables)
                                            .build();

    customSecretsManagerServiceImpl.validateInternal(config, testVariables, false);
    verify(customEncryptor, times(1)).validateReference(any(), any(), any());
  }
}
