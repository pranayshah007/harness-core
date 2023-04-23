/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservice.manifest;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.JELENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConfiguration;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureAppServiceConnectionStringType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.azure.appservices.manifest.AzureAppServiceManifestUtils;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.collect.ImmutableList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureAppServiceManifestUtilsTest extends WingsBaseTest {
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private ApplicationManifestService applicationManifestService;

  @InjectMocks private AzureAppServiceManifestUtils azureAppServiceManifestUtils;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAzureAppServiceConfiguration() {
    ExecutionContext mockExecutionContext = mock(ExecutionContext.class);
    mockGetManifestFilesByAppManifestId(mockExecutionContext);

    AzureAppServiceConfiguration azureAppServiceConfiguration =
        azureAppServiceManifestUtils.getAzureAppServiceConfiguration(mockExecutionContext);

    List<AzureAppServiceApplicationSetting> azureAppServiceApplicationSettings =
        azureAppServiceConfiguration.getAppSettings();
    Map<String, AzureAppServiceApplicationSetting> appSettings = azureAppServiceApplicationSettings.stream().collect(
        Collectors.toMap(AzureAppServiceApplicationSetting::getName, Function.identity()));

    List<AzureAppServiceConnectionString> azureAppServiceConnectionStrings =
        azureAppServiceConfiguration.getConnStrings();
    Map<String, AzureAppServiceConnectionString> connStrings = azureAppServiceConnectionStrings.stream().collect(
        Collectors.toMap(AzureAppServiceConnectionString::getName, Function.identity()));

    assertThat(appSettings.size()).isEqualTo(6);
    assertThat(appSettings.get("SERVICE_VARIABLE").getValue()).isEqualTo("${serviceVariable.service_variable}");
    assertThat(appSettings.get("DOCKER_REGISTRY_SERVER_PASSWORD").getValue())
        .isEqualTo("${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}");
    assertThat(appSettings.get("DOCKER_REGISTRY_SERVER_USERNAME").getValue()).isEqualTo("automationuser");
    assertThat(appSettings.get("CONFIG_FILE").getValue()).isEqualTo("configFile.getAsString('fileName')");

    assertThat(connStrings.size()).isEqualTo(3);
    assertThat(connStrings.get("SECRET_CONN_STRING").getValue())
        .isEqualTo("${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}");
    assertThat(connStrings.get("SERVICE_VARIABLE").getValue()).isEqualTo("${serviceVariable.service_variable}");
    assertThat(connStrings.get("CONN_STRING").getValue()).isEqualTo("value");
  }

  @NotNull
  private ApplicationManifest mockGetManifestFilesByAppManifestId(ExecutionContext mockExecutionContext) {
    Service mockService = mock(Service.class);
    doReturn(mockService).when(applicationManifestUtils).fetchServiceFromContext(mockExecutionContext);

    ApplicationManifest serviceAppManifest = ApplicationManifest.builder().storeType(StoreType.Local).build();
    serviceAppManifest.setAppId("APP_ID");
    serviceAppManifest.setUuid("SERVICE_UUID");
    doReturn(serviceAppManifest).when(applicationManifestService).getByServiceId(any(), any(), any());

    doReturn(ImmutableList.of(
                 ManifestFile.builder().fileName("appsettings").fileContent(appSettingsServiceAppManifest).build(),
                 ManifestFile.builder().fileName("connstrings").fileContent(connStringsServiceAppManifest).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(eq("APP_ID"), eq("SERVICE_UUID"));

    return serviceAppManifest;
  }

  String appSettingsServiceAppManifest = "[\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_PASSWORD\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_URL\",\n"
      + "      \"value\": \"https://harness.jfrog-ui.io\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_USERNAME\",\n"
      + "      \"value\": \"automationuser\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SERVICE_VARIABLE\",\n"
      + "      \"value\": \"${serviceVariable.service_variable}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"WEBSITES_ENABLE_APP_SERVICE_STORAGE\",\n"
      + "      \"value\": \"false\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"CONFIG_FILE\",\n"
      + "      \"value\": \"configFile.getAsString('fileName')\",\n"
      + "      \"slotSetting\": false\n"
      + "    }\n"
      + "  ]";

  String connStringsServiceAppManifest = "[\n"
      + "    {\n"
      + "      \"name\": \"CONN_STRING\",\n"
      + "      \"value\": \"value\",\n"
      + "      \"type\": \"MySQL\",\n"
      + "      \"slotSetting\": true\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SECRET_CONN_STRING\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"type\": \"PostgreSQL\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "        \"name\": \"SERVICE_VARIABLE\",\n"
      + "        \"value\": \"${serviceVariable.service_variable}\",\n"
      + "        \"type\": \"Custom\",\n"
      + "        \"slotSetting\": true\n"
      + "      }\n"
      + "  ]";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAzureAppServiceConfigurationEnvServiceOverrides() {
    ExecutionContext mockExecutionContext = mock(ExecutionContext.class);
    mockGetManifestFilesByAppManifestId(mockExecutionContext);

    // appsettings env service override
    Map<K8sValuesLocation, ApplicationManifest> appSettingsEnvServiceOverride = getAppSettingsEnvServiceOverride();
    doReturn(appSettingsEnvServiceOverride)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    // connstrings env service override
    Map<K8sValuesLocation, ApplicationManifest> connStringsEnvServiceOverride = getConnStringsEnvServiceOverride();
    doReturn(connStringsEnvServiceOverride)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    AzureAppServiceConfiguration azureAppServiceConfiguration =
        azureAppServiceManifestUtils.getAzureAppServiceConfiguration(mockExecutionContext);

    List<AzureAppServiceApplicationSetting> azureAppServiceApplicationSettings =
        azureAppServiceConfiguration.getAppSettings();
    Map<String, AzureAppServiceApplicationSetting> appSettings = azureAppServiceApplicationSettings.stream().collect(
        Collectors.toMap(AzureAppServiceApplicationSetting::getName, Function.identity()));

    List<AzureAppServiceConnectionString> azureAppServiceConnectionStrings =
        azureAppServiceConfiguration.getConnStrings();
    Map<String, AzureAppServiceConnectionString> connStrings = azureAppServiceConnectionStrings.stream().collect(
        Collectors.toMap(AzureAppServiceConnectionString::getName, Function.identity()));

    assertThat(appSettings.size()).isEqualTo(6);
    assertThat(appSettings.get("SERVICE_VARIABLE_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("${serviceVariable.service_variable}");
    assertThat(appSettings.get("DOCKER_REGISTRY_SERVER_PASSWORD_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}");
    assertThat(appSettings.get("DOCKER_REGISTRY_SERVER_USERNAME_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("automationuser");
    assertThat(appSettings.get("CONFIG_FILE_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("configFile.getAsString('fileName')");

    assertThat(connStrings.size()).isEqualTo(3);
    assertThat(connStrings.get("SECRET_CONN_STRING_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}");
    assertThat(connStrings.get("SERVICE_VARIABLE_SERVICE_ENV_OVERRIDE").getValue())
        .isEqualTo("${serviceVariable.service_variable}");
    assertThat(connStrings.get("CONN_STRING_SERVICE_ENV_OVERRIDE").getValue()).isEqualTo("value");
  }

  private Map<K8sValuesLocation, ApplicationManifest> getAppSettingsEnvServiceOverride() {
    Map<K8sValuesLocation, ApplicationManifest> appSettingsEnvServiceOverride = new EnumMap<>(K8sValuesLocation.class);

    ApplicationManifest appSettingsServiceEnvOverride =
        ApplicationManifest.builder().storeType(StoreType.Local).build();
    appSettingsServiceEnvOverride.setAppId("APP_ID");
    appSettingsServiceEnvOverride.setUuid("APP_SETTINGS_ENV_SERVICE_OVERRIDE_UUID");
    appSettingsEnvServiceOverride.put(K8sValuesLocation.Environment, appSettingsServiceEnvOverride);
    doReturn(ImmutableList.of(
                 ManifestFile.builder().fileName("appsettings").fileContent(appSettingsEnvServiceOverrides).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(eq("APP_ID"), eq("APP_SETTINGS_ENV_SERVICE_OVERRIDE_UUID"));

    ApplicationManifest appSettingsEnvOverride = ApplicationManifest.builder().storeType(StoreType.Local).build();
    appSettingsEnvOverride.setAppId("APP_ID");
    appSettingsEnvOverride.setUuid("APP_SETTINGS_ENV_OVERRIDE_UUID");
    appSettingsEnvServiceOverride.put(K8sValuesLocation.EnvironmentGlobal, appSettingsEnvOverride);
    doReturn(
        ImmutableList.of(ManifestFile.builder().fileName("appsettings").fileContent(appSettingsEnvOverrides).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(eq("APP_ID"), eq("APP_SETTINGS_ENV_OVERRIDE_UUID"));

    return appSettingsEnvServiceOverride;
  }

  @NotNull
  private Map<K8sValuesLocation, ApplicationManifest> getConnStringsEnvServiceOverride() {
    Map<K8sValuesLocation, ApplicationManifest> connStringsEnvServiceOverride = new EnumMap<>(K8sValuesLocation.class);

    ApplicationManifest connStringsServiceEnvOverride =
        ApplicationManifest.builder().storeType(StoreType.Local).build();
    connStringsServiceEnvOverride.setAppId("APP_ID");
    connStringsServiceEnvOverride.setUuid("CONN_STRINGS_ENV_SERVICE_OVERRIDE_UUID");
    connStringsEnvServiceOverride.put(K8sValuesLocation.Environment, connStringsServiceEnvOverride);
    doReturn(ImmutableList.of(
                 ManifestFile.builder().fileName("connstrings").fileContent(connStringsEnvServiceOverrides).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(eq("APP_ID"), eq("CONN_STRINGS_ENV_SERVICE_OVERRIDE_UUID"));

    ApplicationManifest connStringsEnvOverride = ApplicationManifest.builder().storeType(StoreType.Local).build();
    connStringsEnvOverride.setAppId("APP_ID");
    connStringsEnvOverride.setUuid("CONN_STRINGS_ENV_OVERRIDE_UUID");
    connStringsEnvServiceOverride.put(K8sValuesLocation.EnvironmentGlobal, connStringsEnvOverride);
    doReturn(
        ImmutableList.of(ManifestFile.builder().fileName("connstrings").fileContent(connStringsEnvOverrides).build()))
        .when(applicationManifestService)
        .getManifestFilesByAppManifestId(eq("APP_ID"), eq("CONN_STRINGS_ENV_OVERRIDE_UUID"));

    return connStringsEnvServiceOverride;
  }

  String appSettingsEnvServiceOverrides = "[\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_PASSWORD_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_URL_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"https://harness.jfrog-ui.io\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_USERNAME_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"automationuser\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SERVICE_VARIABLE_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${serviceVariable.service_variable}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"WEBSITES_ENABLE_APP_SERVICE_STORAGE_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"false\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"CONFIG_FILE_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"configFile.getAsString('fileName')\",\n"
      + "      \"slotSetting\": false\n"
      + "    }\n"
      + "  ]";

  String connStringsEnvServiceOverrides = "[\n"
      + "    {\n"
      + "      \"name\": \"CONN_STRING_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"value\",\n"
      + "      \"type\": \"MySQL\",\n"
      + "      \"slotSetting\": true\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SECRET_CONN_STRING_SERVICE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"type\": \"PostgreSQL\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "        \"name\": \"SERVICE_VARIABLE_SERVICE_ENV_OVERRIDE\",\n"
      + "        \"value\": \"${serviceVariable.service_variable}\",\n"
      + "        \"type\": \"Custom\",\n"
      + "        \"slotSetting\": true\n"
      + "      }\n"
      + "  ]";

  String appSettingsEnvOverrides = "[\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_PASSWORD_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_URL_ENV_OVERRIDE\",\n"
      + "      \"value\": \"https://harness.jfrog-ui.io\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"DOCKER_REGISTRY_SERVER_USERNAME_ENV_OVERRIDE\",\n"
      + "      \"value\": \"automationuser\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SERVICE_VARIABLE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${serviceVariable.service_variable}\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"WEBSITES_ENABLE_APP_SERVICE_STORAGE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"false\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"CONFIG_FILE_ENV_OVERRIDE\",\n"
      + "      \"value\": \"configFile.getAsString('fileName')\",\n"
      + "      \"slotSetting\": false\n"
      + "    }\n"
      + "  ]";

  String connStringsEnvOverrides = "[\n"
      + "    {\n"
      + "      \"name\": \"CONN_STRING_ENV_OVERRIDE\",\n"
      + "      \"value\": \"value\",\n"
      + "      \"type\": \"MySql\",\n"
      + "      \"slotSetting\": true\n"
      + "    },\n"
      + "    {\n"
      + "      \"name\": \"SECRET_CONN_STRING_ENV_OVERRIDE\",\n"
      + "      \"value\": \"${secrets.getValue('Artifact Azure_ARTIFACTORY_password')}\",\n"
      + "      \"type\": \"PostgreSQL\",\n"
      + "      \"slotSetting\": false\n"
      + "    },\n"
      + "    {\n"
      + "        \"name\": \"SERVICE_VARIABLE_ENV_OVERRIDE\",\n"
      + "        \"value\": \"${serviceVariable.service_variable}\",\n"
      + "        \"type\": \"Custom\",\n"
      + "        \"slotSetting\": true\n"
      + "      }\n"
      + "  ]";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAppSettingSecretsImmutableList() {
    ImmutableList<String> appSettingSecretsImmutableList =
        azureAppServiceManifestUtils.getAppSettingSecretsImmutableList(
            ImmutableList.of(AzureAppServiceApplicationSetting.builder()
                                 .name("SECRET_APP_SETTING")
                                 .value("${secrets.getValue(\"secret_name\")}")
                                 .sticky(true)
                                 .build(),
                AzureAppServiceApplicationSetting.builder()
                    .name("APP_SETTING")
                    .value("APP_SETTING_VALUE")
                    .sticky(true)
                    .build()));

    assertThat(appSettingSecretsImmutableList).isNotEmpty();
    assertThat(appSettingSecretsImmutableList.size()).isEqualTo(1);
    assertThat(appSettingSecretsImmutableList.get(0)).isEqualTo("SECRET_APP_SETTING");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetConnStringSecretsImmutableList() {
    ImmutableList<String> connStringSecretsImmutableList =
        azureAppServiceManifestUtils.getConnStringSecretsImmutableList(
            ImmutableList.of(AzureAppServiceConnectionString.builder()
                                 .name("SECRET_CONN_STRINGS")
                                 .value("${secrets.getValue(\"secret_name\")}")
                                 .type(AzureAppServiceConnectionStringType.CUSTOM)
                                 .sticky(true)
                                 .build(),
                AzureAppServiceConnectionString.builder()
                    .name("CONN_STRINGS")
                    .value("CONN_STRINGS_VALUE")
                    .type(AzureAppServiceConnectionStringType.MYSQL)
                    .sticky(true)
                    .build()));

    assertThat(connStringSecretsImmutableList).isNotEmpty();
    assertThat(connStringSecretsImmutableList.size()).isEqualTo(1);
    assertThat(connStringSecretsImmutableList.get(0)).isEqualTo("SECRET_CONN_STRINGS");
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testGetAppServiceConfigurationManifests() {
    ExecutionContext mockExecutionContext = mock(ExecutionContext.class);
    mockGetManifestFilesByAppManifestId(mockExecutionContext);

    Map<K8sValuesLocation, ApplicationManifest> appSettingsManifestOverrides = new EnumMap<>(K8sValuesLocation.class);

    // remote service level manifest
    ApplicationManifest appSettingsServiceOverride = ApplicationManifest.builder().storeType(StoreType.Remote).build();
    appSettingsServiceOverride.setAppId("APP_ID");
    appSettingsServiceOverride.setUuid("APP_SETTINGS_SERVICE_OVERRIDE_UUID");
    appSettingsServiceOverride.setGitFileConfig(
        GitFileConfig.builder().branch("main").connectorId("CONNECTOR_ID").filePath("appsettings").build());
    appSettingsManifestOverrides.put(K8sValuesLocation.Service, appSettingsServiceOverride);

    // local env service overrides
    appSettingsManifestOverrides.putAll(getAppSettingsEnvServiceOverride());
    doReturn(appSettingsManifestOverrides)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    Map<K8sValuesLocation, ApplicationManifest> connStringManifestOverrides = new EnumMap<>(K8sValuesLocation.class);

    // remote service override
    ApplicationManifest connStringsServiceEnvOverride =
        ApplicationManifest.builder().storeType(StoreType.Remote).build();
    connStringsServiceEnvOverride.setAppId("APP_ID");
    connStringsServiceEnvOverride.setUuid("CONN_STRINGS_SERVICE_OVERRIDE_UUID");
    connStringsServiceEnvOverride.setGitFileConfig(
        GitFileConfig.builder().branch("main").connectorId("CONNECTOR_ID").filePath("connsettings").build());

    // local env service overrides
    connStringManifestOverrides.put(K8sValuesLocation.Service, connStringsServiceEnvOverride);
    connStringManifestOverrides.putAll(getConnStringsEnvServiceOverride());
    doReturn(connStringManifestOverrides)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    Map<String, ApplicationManifest> appServiceConfigurationManifests =
        azureAppServiceManifestUtils.getAppServiceConfigurationManifests(mockExecutionContext);

    assertThat(appServiceConfigurationManifests.size()).isEqualTo(2);

    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name())).isNotNull();
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name()).getAppId())
        .isEqualTo("APP_ID");
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name()).getUuid())
        .isEqualTo("APP_SETTINGS_ENV_SERVICE_OVERRIDE_UUID");

    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name())).isNotNull();
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name()).getAppId())
        .isEqualTo("APP_ID");
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name()).getUuid())
        .isEqualTo("CONN_STRINGS_ENV_SERVICE_OVERRIDE_UUID");
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testGetAppServiceConfigurationManifestsNoOverrides() {
    ExecutionContext mockExecutionContext = mock(ExecutionContext.class);
    mockGetManifestFilesByAppManifestId(mockExecutionContext);

    Map<K8sValuesLocation, ApplicationManifest> appSettingsManifestOverrides = new EnumMap<>(K8sValuesLocation.class);
    appSettingsManifestOverrides.putAll(getAppSettingsEnvServiceOverride());
    doReturn(appSettingsManifestOverrides)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE);

    // empty conn string manifest
    Map<K8sValuesLocation, ApplicationManifest> connStringManifestOverrides = new EnumMap<>(K8sValuesLocation.class);
    doReturn(connStringManifestOverrides)
        .when(applicationManifestUtils)
        .getApplicationManifests(mockExecutionContext, AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE);

    Map<String, ApplicationManifest> appServiceConfigurationManifests =
        azureAppServiceManifestUtils.getAppServiceConfigurationManifests(mockExecutionContext);

    assertThat(appServiceConfigurationManifests.size()).isEqualTo(2);

    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_CONN_STRINGS_OVERRIDE.name())).isNull();

    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name())).isNotNull();
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name()).getAppId())
        .isEqualTo("APP_ID");
    assertThat(appServiceConfigurationManifests.get(AppManifestKind.AZURE_APP_SETTINGS_OVERRIDE.name()).getUuid())
        .isEqualTo("APP_SETTINGS_ENV_SERVICE_OVERRIDE_UUID");

    assertThat(appServiceConfigurationManifests.get(K8sValuesLocation.Service.name())).isNotNull();
    assertThat(appServiceConfigurationManifests.get(K8sValuesLocation.Service.name()).getAppId()).isEqualTo("APP_ID");
    assertThat(appServiceConfigurationManifests.get(K8sValuesLocation.Service.name()).getUuid())
        .isEqualTo("SERVICE_UUID");
  }
}
