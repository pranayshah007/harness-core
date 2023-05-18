/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.SAINATH;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.security.UsageRestrictions;
import software.wings.service.impl.yaml.handler.setting.cloudprovider.GcpConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.service.impl.yaml.handler.usagerestrictions.UsageRestrictionsYamlHandler;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GcpConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private GcpConfigYamlHandler gcpConfigYamlHandler;
  @Mock SecretManager secretManager;
  @Mock UsageRestrictionsYamlHandler usageRestrictionsYamlHandler;

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testToYaml() {
    String accountId = "accountId";
    String encryptedServiceAccountKeyFileContent = "encryptedServiceAccountKeyFileContent";
    boolean useDelegate = true;
    String delegateSelector = "delegateSelector";
    boolean skipValidation = true;
    GcpConfig gcpConfig = GcpConfig.builder()
                              .accountId(accountId)
                              .encryptedServiceAccountKeyFileContent(encryptedServiceAccountKeyFileContent)
                              .useDelegateSelectors(useDelegate)
                              .delegateSelectors(Collections.singletonList(delegateSelector))
                              .skipValidation(skipValidation)
                              .build();

    SettingAttribute settingAttribute = aSettingAttribute().withValue(gcpConfig).build();
    String appId = "appId";

    String encryptedYamlRef = "encryptedYamlRef";
    UsageRestrictions.Yaml usageRestrictionsYaml = UsageRestrictions.Yaml.builder().build();

    when(secretManager.getEncryptedYamlRef(
             gcpConfig.getAccountId(), gcpConfig.getEncryptedServiceAccountKeyFileContent()))
        .thenReturn(encryptedYamlRef);
    when(usageRestrictionsYamlHandler.toYaml(any(), any())).thenReturn(usageRestrictionsYaml);

    GcpConfig.Yaml yaml = gcpConfigYamlHandler.toYaml(settingAttribute, appId);

    assertThat(yaml.getType()).isEqualTo(gcpConfig.getType());
    assertThat(yaml.getServiceAccountKeyFileContent()).isEqualTo(encryptedYamlRef);
    assertThat(yaml.isUseDelegateSelectors()).isEqualTo(useDelegate);
    assertThat(yaml.getDelegateSelectors()).isEqualTo(Collections.singletonList(delegateSelector));
    assertThat(yaml.isSkipValidation()).isEqualTo(skipValidation);
    assertThat(yaml.getUsageRestrictions()).isEqualTo(usageRestrictionsYaml);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testToBean() {
    String uuid = "uuid";
    SettingAttribute previous = aSettingAttribute().withUuid(uuid).build();

    String accountId = "accountId";
    String filePath = "filePath";
    Change change = Change.Builder.aFileChange().withAccountId(accountId).withFilePath(filePath).build();

    String serviceAccountKeyFileContent = "serviceAccountKeyFileContent";
    boolean useDelegate = true;
    String delegateSelector = "delegateSelector";
    boolean skipValidation = true;
    GcpConfig.Yaml yaml = GcpConfig.Yaml.builder()
                              .serviceAccountKeyFileContent(serviceAccountKeyFileContent)
                              .useDelegateSelectors(useDelegate)
                              .delegateSelectors(Collections.singletonList(delegateSelector))
                              .skipValidation(skipValidation)
                              .build();

    ChangeContext<GcpConfig.Yaml> changeContext =
        ChangeContext.Builder.aChangeContext().withChange(change).withYaml(yaml).build();

    SettingAttribute settingAttribute = gcpConfigYamlHandler.toBean(previous, changeContext, null);

    assertThat(settingAttribute.getAccountId()).isEqualTo(accountId);
    assertThat(settingAttribute.getUuid()).isEqualTo(uuid);

    GcpConfig gcpConfig = (GcpConfig) settingAttribute.getValue();

    assertThat(gcpConfig.getAccountId()).isEqualTo(accountId);
    assertThat(gcpConfig.getEncryptedServiceAccountKeyFileContent()).isEqualTo(serviceAccountKeyFileContent);
    assertThat(gcpConfig.isUseDelegateSelectors()).isEqualTo(useDelegate);
    assertThat(gcpConfig.getDelegateSelectors()).isEqualTo(Collections.singletonList(delegateSelector));
    assertThat(gcpConfig.isSkipValidation()).isEqualTo(skipValidation);
  }
}
