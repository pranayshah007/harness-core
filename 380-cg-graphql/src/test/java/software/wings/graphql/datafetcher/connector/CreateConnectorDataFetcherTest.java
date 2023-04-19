/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector;

import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.ACCOUNT_ID;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.AUTHOR;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.BRANCH;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.EMAIL;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.MESSAGE;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.PASSWORD;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.SSH;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.URL;
import static software.wings.graphql.datafetcher.connector.utils.ConnectorConstants.USERNAME;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlAmazonS3PlatformInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlDockerConnectorInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlGCSPlatformInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlGitConnectorInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlHelmConnectorInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlHttpServerPlatformInputBuilder;
import static software.wings.graphql.datafetcher.connector.utils.Utility.getQlNexusConnectorInputBuilder;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.beans.DockerConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.connector.utils.Utility;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.connector.input.QLConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.docker.QLDockerConnectorInput.QLDockerConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.git.QLCustomCommitDetailsInput;
import software.wings.graphql.schema.mutation.connector.input.git.QLGitConnectorInput.QLGitConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHelmConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.nexus.QLNexusConnectorInput.QLNexusConnectorInputBuilder;
import software.wings.graphql.schema.mutation.connector.input.nexus.QLNexusVersion;
import software.wings.graphql.schema.mutation.connector.payload.QLCreateConnectorPayload;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.graphql.schema.type.connector.QLAmazonS3Connector;
import software.wings.graphql.schema.type.connector.QLDockerConnector;
import software.wings.graphql.schema.type.connector.QLGCSHelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLGitConnector;
import software.wings.graphql.schema.type.connector.QLHttpHelmRepoConnector;
import software.wings.graphql.schema.type.connector.QLNexusConnector;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import javax.validation.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateConnectorDataFetcherTest extends CategoryTest {
  @Mock private SettingsService settingsService;
  @Mock private SettingServiceHelper settingServiceHelper;
  @Mock private software.wings.graphql.datafetcher.connector.ConnectorsController connectorsController;
  @Mock private SecretManager secretManager;
  @Mock private UsageScopeController usageScopeController;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks
  private software.wings.graphql.datafetcher.connector.CreateConnectorDataFetcher dataFetcher =
      new software.wings.graphql.datafetcher.connector.CreateConnectorDataFetcher();

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void checkIfPermissionCorrect() throws NoSuchMethodException {
    Method method = software.wings.graphql.datafetcher.connector.CreateConnectorDataFetcher.class.getDeclaredMethod(
        "mutateAndFetch", QLConnectorInput.class, MutationContext.class);
    AuthRule annotation = method.getAnnotation(AuthRule.class);
    assertThat(annotation.permissionType()).isEqualTo(MANAGE_CONNECTORS);
  }

  // CREATE GIT CONNECTOR TESTS
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createGitConnector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GitConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GIT)
            .gitConnector(
                getQlGitConnectorInputBuilder()
                    .branch(RequestField.ofNullable(BRANCH))
                    .generateWebhookUrl(RequestField.ofNullable(true))
                    .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
                    .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                                     .authorName(RequestField.ofNullable(AUTHOR))
                                                                     .authorEmailId(RequestField.ofNullable(EMAIL))
                                                                     .commitMessage(RequestField.ofNullable(MESSAGE))
                                                                     .build()))
                    .passwordSecretId(RequestField.ofNullable(PASSWORD))
                    .delegateSelectors(RequestField.ofNull())
                    .disableUserGitConfig(RequestField.ofNull())
                    .providerType(RequestField.ofNull())
                    .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(usageScopeController, times(0)).populateUsageRestrictions(any(), any());
    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLGitConnector.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createShhGitConnector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GitConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLGitConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGitConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, SSH);

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GIT)
            .gitConnector(
                getQlGitConnectorInputBuilder()
                    .branch(RequestField.ofNullable(BRANCH))
                    .generateWebhookUrl(RequestField.ofNullable(true))
                    .urlType(RequestField.ofNullable(GitConfig.UrlType.REPO))
                    .customCommitDetails(RequestField.ofNullable(QLCustomCommitDetailsInput.builder()
                                                                     .authorName(RequestField.ofNullable(AUTHOR))
                                                                     .authorEmailId(RequestField.ofNullable(EMAIL))
                                                                     .commitMessage(RequestField.ofNullable(MESSAGE))
                                                                     .build()))
                    .sshSettingId(RequestField.ofNullable(SSH))
                    .usageScope(RequestField.ofNullable(QLUsageScope.builder().build()))
                    .delegateSelectors(RequestField.ofNull())
                    .disableUserGitConfig(RequestField.ofNull())
                    .providerType(RequestField.ofNull())
                    .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(usageScopeController, times(1)).populateUsageRestrictions(any(), any());
    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLGitConnector.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createGitConnectorSpecifyingBothSecrets() {
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.GIT)
                                 .gitConnector(getQlGitConnectorInputBuilder()
                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                   .sshSettingId(RequestField.ofNullable(SSH))
                                                   .build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);
    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Just one secretId should be specified");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createGitConnectorNotSpecifyingSecrets() {
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.GIT)
                                 .gitConnector(getQlGitConnectorInputBuilder().build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No secretId provided with the request for connector");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createGitConnectorWithPasswordNotSpecifyingUsername() {
    QLGitConnectorInputBuilder gitConnectorInputBuilder = getQlGitConnectorInputBuilder();
    gitConnectorInputBuilder.userName(RequestField.absent()).passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.GIT)
                                 .gitConnector(gitConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void createGitConnectorWitNonExistentSecretId() {
    QLGitConnectorInputBuilder gitConnectorInputBuilder = getQlGitConnectorInputBuilder();
    gitConnectorInputBuilder.userName(RequestField.ofNullable(USERNAME))
        .passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.GIT)
                                 .gitConnector(gitConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(null).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Secret does not exist");
  }

  // CREATE DOCKER CONNECTOR TESTS
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createDockerConnector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(DockerConfig.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .dockerRegistryUrl(URL)
                                                  .delegateSelectors(Collections.singletonList("delegateSelector"))
                                                  .build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLDockerConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLDockerConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.DOCKER)
            .dockerConnector(
                getQlDockerConnectorInputBuilder().passwordSecretId(RequestField.ofNullable(PASSWORD)).build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLDockerConnector.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createDockerConnectorWithoutUsername() {
    QLDockerConnectorInputBuilder dockerConnectorInputBuilder = getQlDockerConnectorInputBuilder();
    dockerConnectorInputBuilder.userName(RequestField.absent()).passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.DOCKER)
                                 .dockerConnector(dockerConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createDockerConnectorWithEmptyUsername() {
    QLDockerConnectorInputBuilder dockerConnectorInputBuilder = getQlDockerConnectorInputBuilder();
    dockerConnectorInputBuilder.userName(RequestField.ofNullable(" "))
        .passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.DOCKER)
                                 .dockerConnector(dockerConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createDockerConnectorWithEmptyURL() {
    QLDockerConnectorInputBuilder dockerConnectorInputBuilder = getQlDockerConnectorInputBuilder();
    dockerConnectorInputBuilder.userName(RequestField.ofNullable(USERNAME))
        .URL(RequestField.ofNullable(" "))
        .passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.DOCKER)
                                 .dockerConnector(dockerConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("URL should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createDockerConnectorWithoutConnectorType() {
    QLDockerConnectorInputBuilder dockerConnectorInputBuilder = getQlDockerConnectorInputBuilder();
    dockerConnectorInputBuilder.userName(RequestField.ofNullable(USERNAME))
        .passwordSecretId(RequestField.ofNullable(PASSWORD));
    QLConnectorInput input =
        QLConnectorInput.builder().connectorType(null).dockerConnector(dockerConnectorInputBuilder.build()).build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid connector type provided");
  }

  // CREATE NEXUS CONNECTOR TESTS
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createNexusConnector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(NexusConfig.builder().accountId(ACCOUNT_ID).nexusUrl(URL).build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLNexusConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLNexusConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    QLCreateConnectorPayload payload =
        dataFetcher.mutateAndFetch(QLConnectorInput.builder()
                                       .connectorType(QLConnectorType.NEXUS)
                                       .nexusConnector(getQlNexusConnectorInputBuilder()
                                                           .delegateSelectors(RequestField.ofNull())
                                                           .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                           .version(RequestField.ofNullable(QLNexusVersion.V2))
                                                           .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLNexusConnector.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createNexusConnectorWithoutUsername() {
    QLNexusConnectorInputBuilder nexusConnectorInputBuilder = getQlNexusConnectorInputBuilder();
    nexusConnectorInputBuilder.userName(RequestField.absent())
        .passwordSecretId(RequestField.ofNullable(PASSWORD))
        .version(RequestField.ofNullable(QLNexusVersion.V2));

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.NEXUS)
                                 .nexusConnector(nexusConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createNexusConnectorWithEmptyUsername() {
    QLNexusConnectorInputBuilder nexusConnectorInputBuilder = getQlNexusConnectorInputBuilder();
    nexusConnectorInputBuilder.userName(RequestField.ofNullable(" "))
        .passwordSecretId(RequestField.ofNullable(PASSWORD))
        .version(RequestField.ofNullable(QLNexusVersion.V2));

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.NEXUS)
                                 .nexusConnector(nexusConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createNexusConnectorWithEmptyURL() {
    QLNexusConnectorInputBuilder nexusConnectorInputBuilder = getQlNexusConnectorInputBuilder();
    nexusConnectorInputBuilder.userName(RequestField.ofNullable(USERNAME))
        .passwordSecretId(RequestField.ofNullable(PASSWORD))
        .URL(RequestField.ofNullable(" "))
        .version(RequestField.ofNullable(QLNexusVersion.V2));

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.NEXUS)
                                 .nexusConnector(nexusConnectorInputBuilder.build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("URL should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createNexusConnectorWithoutConnectorType() {
    QLNexusConnectorInputBuilder nexusConnectorInputBuilder = getQlNexusConnectorInputBuilder();
    nexusConnectorInputBuilder.userName(RequestField.ofNullable(USERNAME))
        .passwordSecretId(RequestField.ofNullable(PASSWORD))
        .version(RequestField.ofNullable(QLNexusVersion.V2));
    QLConnectorInput input =
        QLConnectorInput.builder().connectorType(null).nexusConnector(nexusConnectorInputBuilder.build()).build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid connector type provided");
  }

  // CREATE HELM CONNECTOR TESTS
  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmConnectorWithoutHostingPlatform() {
    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.HTTP_HELM_REPO)
                                 .helmConnector(QLHelmConnectorInput.builder()
                                                    .name(RequestField.ofNullable("NAME"))
                                                    .httpServerPlatformDetails(RequestField.absent())
                                                    .gcsPlatformDetails(RequestField.absent())
                                                    .amazonS3PlatformDetails(RequestField.absent())
                                                    .ociPlatformDetails(RequestField.absent())
                                                    .build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Hosting platform details should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmConnectorWithTwoHostingPlatforms() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(
                QLHelmConnectorInput.builder()
                    .name(RequestField.ofNullable("NAME"))
                    .httpServerPlatformDetails(RequestField.ofNullable(getQlHttpServerPlatformInputBuilder().build()))
                    .gcsPlatformDetails(RequestField.ofNullable(getQlGCSPlatformInputBuilder().build()))
                    .amazonS3PlatformDetails(RequestField.absent())
                    .ociPlatformDetails(RequestField.absent())
                    .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Only one hosting platform details should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmConnectorWithWrongHostingPlatform() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(QLHelmConnectorInput.builder()
                               .name(RequestField.ofNullable("NAME"))
                               .httpServerPlatformDetails(RequestField.absent())
                               .gcsPlatformDetails(RequestField.ofNullable(getQlGCSPlatformInputBuilder().build()))
                               .amazonS3PlatformDetails(RequestField.absent())
                               .ociPlatformDetails(RequestField.absent())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Wrong hosting platform provided with the request for HTTP_HELM_REPO connector");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmHttpServerConnector() {
    SettingAttribute setting =
        SettingAttribute.Builder.aSettingAttribute()
            .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
            .withValue(HttpHelmRepoConfig.builder().accountId(ACCOUNT_ID).chartRepoUrl(URL).build())
            .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLHttpHelmRepoConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLHttpHelmRepoConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(getQlHttpServerPlatformInputBuilder()
                                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                                   .build())
                               .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLHttpHelmRepoConnector.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmHttpServerConnectorWithoutUsername() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(getQlHttpServerPlatformInputBuilder()
                                                                   .userName(RequestField.absent())
                                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                                   .build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName is not specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmHttpServerConnectorWithEmptyUsername() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(getQlHttpServerPlatformInputBuilder()
                                                                   .userName(RequestField.ofNullable(""))
                                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                                   .build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("userName should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmHttpServerConnectorWithEmptyURL() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.HTTP_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(getQlHttpServerPlatformInputBuilder()
                                                                   .URL(RequestField.ofNullable(""))
                                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                                   .build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("URL should be specified");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmHttpServerConnectorWithoutConnectorType() {
    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(null)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(getQlHttpServerPlatformInputBuilder()
                                                                   .passwordSecretId(RequestField.ofNullable(PASSWORD))
                                                                   .build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(new SettingAttribute()).when(settingsService).getByAccount(ACCOUNT_ID, PASSWORD);
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid connector type provided");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLGCSHelmRepoConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGCSHelmRepoConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "GCP");

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(Utility.getQlHelmConnectorInputBuilder(getQlGCSPlatformInputBuilder().build()).build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLGCSHelmRepoConnector.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnectorWithoutProvider() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(
                                   getQlGCSPlatformInputBuilder().googleCloudProvider(RequestField.absent()).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getSettingAttributeByName(ACCOUNT_ID, "GCP");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Google Cloud provider is not specified for GCS hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnectorWithEmptyProvider() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(
                Utility
                    .getQlHelmConnectorInputBuilder(
                        getQlGCSPlatformInputBuilder().googleCloudProvider(RequestField.ofNullable("")).build())
                    .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getSettingAttributeByName(ACCOUNT_ID, "GCP");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Google Cloud provider should be specified for GCS hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnectorWithoutBucketName() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(
                                   getQlGCSPlatformInputBuilder().bucketName(RequestField.absent()).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "GCP");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bucket name is not specified for GCS hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnectorWithEmptyBucketName() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(Utility
                               .getQlHelmConnectorInputBuilder(
                                   getQlGCSPlatformInputBuilder().bucketName(RequestField.ofNullable("")).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "GCP");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bucket name should be specified for GCS hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3Connector() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLAmazonS3Connector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLAmazonS3Connector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "AWS");

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
            .helmConnector(getQlHelmConnectorInputBuilder(getQlAmazonS3PlatformInputBuilder().build()).build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    assertThat(payload.getConnector()).isNotNull();
    assertThat(payload.getConnector()).isInstanceOf(QLAmazonS3Connector.class);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithoutProvider() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
            .helmConnector(getQlHelmConnectorInputBuilder(
                getQlAmazonS3PlatformInputBuilder().awsCloudProvider(RequestField.absent()).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getSettingAttributeByName(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AWS Cloud provider is not specified for Amazon S3 hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithEmptyProvider() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
            .helmConnector(getQlHelmConnectorInputBuilder(
                getQlAmazonS3PlatformInputBuilder().awsCloudProvider(RequestField.ofNullable("")).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getSettingAttributeByName(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("AWS Cloud provider should be specified for Amazon S3 hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithoutBucketName() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
                                 .helmConnector(getQlHelmConnectorInputBuilder(
                                     getQlAmazonS3PlatformInputBuilder().bucketName(RequestField.absent()).build())
                                                    .build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bucket name is not specified for Amazon S3 hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithEmptyBucketName() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input =
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
            .helmConnector(getQlHelmConnectorInputBuilder(
                getQlAmazonS3PlatformInputBuilder().bucketName(RequestField.ofNullable("")).build())
                               .build())
            .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bucket name should be specified for Amazon S3 hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithoutRegion() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
                                 .helmConnector(getQlHelmConnectorInputBuilder(
                                     getQlAmazonS3PlatformInputBuilder().region(RequestField.absent()).build())
                                                    .build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Region is not specified for Amazon S3 hosting platform");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmAmazonS3ConnectorWithEmptyRegion() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(AmazonS3HelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    QLConnectorInput input = QLConnectorInput.builder()
                                 .connectorType(QLConnectorType.AMAZON_S3_HELM_REPO)
                                 .helmConnector(getQlHelmConnectorInputBuilder(
                                     getQlAmazonS3PlatformInputBuilder().region(RequestField.ofNullable("")).build())
                                                    .build())
                                 .build();
    MutationContext mutationContext = MutationContext.builder().accountId(ACCOUNT_ID).build();
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "AWS");

    assertThatThrownBy(() -> dataFetcher.mutateAndFetch(input, mutationContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Region should be specified for Amazon S3 hosting platform");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void createHelmGCSConnectorThrowsException() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                   .withValue(GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doThrow(new ConstraintViolationException(new HashSet<>()))
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));
    doReturn(QLGCSHelmRepoConnector.builder()).when(connectorsController).getConnectorBuilder(any());
    doReturn(QLGCSHelmRepoConnector.builder()).when(connectorsController).populateConnector(any(), any());
    doReturn(new EncryptedData()).when(secretManager).getSecretById(ACCOUNT_ID, PASSWORD);
    doReturn(setting).when(settingsService).getByAccountAndId(ACCOUNT_ID, "GCP");

    QLCreateConnectorPayload payload = dataFetcher.mutateAndFetch(
        QLConnectorInput.builder()
            .connectorType(QLConnectorType.GCS_HELM_REPO)
            .helmConnector(Utility.getQlHelmConnectorInputBuilder(getQlGCSPlatformInputBuilder().build()).build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());
  }
}
