/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector.types;

import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PARDHA;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.ACCOUNT_ID;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.AUTHOR;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.BRANCH;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.EMAIL;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.MESSAGE;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.PASSWORD;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.SSH;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlGitConnectorInputBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.beans.GitConfig;
import software.wings.beans.GitConfig.ProviderType;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.connector.ConnectorsController;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.connector.input.QLConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.git.QLCustomCommitDetailsInput;
import software.wings.graphql.schema.mutation.connector.input.git.QLGitConnectorInput;
import software.wings.graphql.schema.type.connector.QLGitConnector;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import java.sql.SQLException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitConnectorTest extends CategoryTest {
  @Mock private SecretManager secretManager;
  @Mock private SettingsService settingsService;
  @Mock private ConnectorsController connectorsController;
  @Mock private UsageScopeController usageScopeController;

  @InjectMocks private GitConnector gitConnector = new GitConnector();

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSettingAttributeWithSshSettingId() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute().build();
    doReturn(setting).when(settingsService).getByAccount(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());

    QLGitConnectorInput qlGitConnectorInput =
        getQlGitConnectorInputBuilder()
            .branch(RequestField.ofNullable(BRANCH))
            .generateWebhookUrl(RequestField.ofNullable(true))
            .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
            .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                             .authorName(RequestField.ofNullable(AUTHOR))
                                                             .authorEmailId(RequestField.ofNullable(EMAIL))
                                                             .commitMessage(RequestField.ofNullable(MESSAGE))
                                                             .build()))
            .delegateSelectors(RequestField.ofNull())
            .sshSettingId(RequestField.ofNullable(SSH))
            .usageScope(RequestField.ofNullable(QLUsageScope.builder().build()))
            .passwordSecretId(RequestField.ofNull())
            .providerType(RequestField.ofNull())
            .disableUserGitConfig(RequestField.ofNull())
            .build();

    SettingAttribute settingAttribute = gitConnector.getSettingAttribute(
        QLConnectorInput.builder().gitConnector(qlGitConnectorInput).build(), ACCOUNT_ID);

    verify(usageScopeController, times(1)).populateUsageRestrictions(any(), any());
    assertThat(((GitConfig) settingAttribute.getValue()).getSshSettingId()).isEqualTo(SSH);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSettingAttributeWithPasswordSecretId() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute().build();
    doReturn(setting).when(settingsService).getByAccount(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());

    QLGitConnectorInput qlGitConnectorInput =
        getQlGitConnectorInputBuilder()
            .branch(RequestField.ofNullable(BRANCH))
            .generateWebhookUrl(RequestField.ofNullable(true))
            .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
            .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                             .authorName(RequestField.ofNullable(AUTHOR))
                                                             .authorEmailId(RequestField.ofNullable(EMAIL))
                                                             .commitMessage(RequestField.ofNullable(MESSAGE))
                                                             .build()))
            .delegateSelectors(RequestField.ofNull())
            .sshSettingId(RequestField.ofNull())
            .passwordSecretId(RequestField.ofNullable(PASSWORD))
            .disableUserGitConfig(RequestField.ofNull())
            .providerType(RequestField.ofNull())
            .build();

    SettingAttribute settingAttribute = gitConnector.getSettingAttribute(
        QLConnectorInput.builder().gitConnector(qlGitConnectorInput).build(), ACCOUNT_ID);

    assertThat(((GitConfig) settingAttribute.getValue()).getEncryptedPassword()).isEqualTo(PASSWORD);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void testGetSettingAttributeHasCorrectDelegateSelectors() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute().build();
    doReturn(setting).when(settingsService).getByAccount(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());

    QLGitConnectorInput qlGitConnectorInput =
        getQlGitConnectorInputBuilder()
            .branch(RequestField.ofNullable(BRANCH))
            .generateWebhookUrl(RequestField.ofNullable(true))
            .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
            .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                             .authorName(RequestField.ofNullable(AUTHOR))
                                                             .authorEmailId(RequestField.ofNullable(EMAIL))
                                                             .commitMessage(RequestField.ofNullable(MESSAGE))
                                                             .build()))
            .delegateSelectors(RequestField.ofNullable(Collections.singletonList("primary")))
            .sshSettingId(RequestField.ofNull())
            .passwordSecretId(RequestField.ofNullable(PASSWORD))
            .disableUserGitConfig(RequestField.ofNull())
            .providerType(RequestField.ofNull())
            .build();

    SettingAttribute settingAttribute = gitConnector.getSettingAttribute(
        QLConnectorInput.builder().gitConnector(qlGitConnectorInput).build(), ACCOUNT_ID);

    assertThat(((GitConfig) settingAttribute.getValue()).getEncryptedPassword()).isEqualTo(PASSWORD);
    assertThat(((GitConfig) settingAttribute.getValue()).getDelegateSelectors())
        .isEqualTo(Collections.singletonList("primary"));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGetSettingAttributeWithProviderType() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute().build();
    doReturn(setting).when(settingsService).getByAccount(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());

    QLGitConnectorInput qlGitConnectorInput =
        getQlGitConnectorInputBuilder()
            .branch(RequestField.ofNullable(BRANCH))
            .generateWebhookUrl(RequestField.ofNullable(true))
            .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
            .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                             .authorName(RequestField.ofNullable(AUTHOR))
                                                             .authorEmailId(RequestField.ofNullable(EMAIL))
                                                             .commitMessage(RequestField.ofNullable(MESSAGE))
                                                             .build()))
            .delegateSelectors(RequestField.ofNullable(Collections.singletonList("primary")))
            .sshSettingId(RequestField.ofNull())
            .passwordSecretId(RequestField.ofNullable(PASSWORD))
            .disableUserGitConfig(RequestField.ofNull())
            .providerType(RequestField.ofNullable(ProviderType.GITLAB))
            .build();

    SettingAttribute settingAttribute = gitConnector.getSettingAttribute(
        QLConnectorInput.builder().gitConnector(qlGitConnectorInput).build(), ACCOUNT_ID);

    assertThat(((GitConfig) settingAttribute.getValue()).getEncryptedPassword()).isEqualTo(PASSWORD);
    assertThat(((GitConfig) settingAttribute.getValue()).getDelegateSelectors())
        .isEqualTo(Collections.singletonList("primary"));
    assertThat(((GitConfig) settingAttribute.getValue()).getProviderType()).isEqualTo(ProviderType.GITLAB);
  }
}
