/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureAppServiceConnectionStringType.fromValue;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.FILE_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.SLOT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.WEB_APP_NAME_BLANK_ERROR_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.WebAppHostingOS;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.runtime.azure.AzureAppServicesDeploymentSlotNotFoundException;
import io.harness.exception.runtime.azure.AzureAppServicesWebAppNotFoundException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.azure.core.http.rest.Response;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appservice.fluent.WebAppsClient;
import com.azure.resourcemanager.appservice.fluent.WebSiteManagementClient;
import com.azure.resourcemanager.appservice.fluent.models.SiteConfigResourceInner;
import com.azure.resourcemanager.appservice.fluent.models.StringDictionaryInner;
import com.azure.resourcemanager.appservice.fluent.models.WebSiteInstanceStatusInner;
import com.azure.resourcemanager.appservice.implementation.WebSiteManagementClientBuilder;
import com.azure.resourcemanager.appservice.models.AppSetting;
import com.azure.resourcemanager.appservice.models.ConnectionString;
import com.azure.resourcemanager.appservice.models.ConnectionStringType;
import com.azure.resourcemanager.appservice.models.CsmSlotEntity;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.Experiments;
import com.azure.resourcemanager.appservice.models.RampUpRule;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.azure.resourcemanager.appservice.models.WebAppBasic;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.google.inject.Singleton;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
public class AzureWebClientImpl extends AzureClient implements AzureWebClient {
  @Override
  public List<WebAppBasic> listWebAppsByResourceGroupName(AzureClientContext context) {
    String subscriptionId = context.getSubscriptionId();
    String resourceGroupName = context.getResourceGroupName();
    AzureResourceManager azure = getAzureClientByContext(context);

    log.debug("Start getting Web Applications by subscriptionId: {}, resourceGroupName: {}", subscriptionId,
        resourceGroupName);
    Instant startListingWebApps = Instant.now();
    List<WebAppBasic> webAppList =
        azure.webApps().listByResourceGroup(resourceGroupName).stream().collect(Collectors.toList());

    long elapsedTime = Duration.between(startListingWebApps, Instant.now()).toMillis();
    log.info("Obtained Web Applications items: {} for elapsed time: {}, resourceGroupName: {}, subscriptionId: {} ",
        webAppList.size(), elapsedTime, resourceGroupName, subscriptionId);

    return webAppList;
  }

  @Override
  public List<WebDeploymentSlotBasic> listDeploymentSlotsByWebAppName(AzureWebClientContext context) {
    String subscriptionId = context.getSubscriptionId();
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    AzureResourceManager azure = getAzureClientByContext(context);

    log.debug("Start listing deployment slots by subscriptionId: {}, resourceGroupName: {}, webAppName: {}",
        subscriptionId, resourceGroupName, webAppName);
    WebApp webApp = getWebApp(azure, resourceGroupName, webAppName);

    return webApp.deploymentSlots().list().stream().collect(Collectors.toList());
  }

  @NotNull
  private WebApp getWebApp(AzureResourceManager azure, String resourceGroupName, String webAppName) {
    WebApp webApp = azure.webApps().getByResourceGroup(resourceGroupName, webAppName);
    if (webApp == null) {
      throw new AzureAppServicesWebAppNotFoundException(webAppName, resourceGroupName);
    }
    return webApp;
  }

  @Override
  public Optional<WebApp> getWebAppByName(AzureWebClientContext context) {
    String webAppName = context.getAppName();
    AzureResourceManager azure = getAzureClientByContext(context);
    log.debug("Start getting web app by webAppName: {}, context: {}", webAppName, context);
    return Optional.ofNullable(azure.webApps().getByResourceGroup(context.getResourceGroupName(), webAppName));
  }

  @Override
  public WebApp getWebApp(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    AzureResourceManager azure = getAzureClientByContext(context);
    return getWebApp(azure, resourceGroupName, webAppName);
  }

  @Override
  public Optional<DeploymentSlot> getDeploymentSlotByName(AzureWebClientContext context, final String slotName) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start getting deployment slot by slotName: {} webAppName: {}, context: {}", slotName,
        context.getAppName(), context);
    try {
      return Optional.ofNullable(getWebApp(context).deploymentSlots().getByName(slotName));
    } catch (NoSuchElementException e) {
      log.warn(format("Unable to find deployment slot with name: %s, for app name: %s", slotName, context.getAppName()),
          ExceptionMessageSanitizer.sanitizeException(e));
      return Optional.empty();
    }
  }

  @Override
  public void startDeploymentSlot(AzureWebClientContext context, final String slotName) {
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    log.debug("Starting slot with name: {}, context: {}", slotName, context);
    deploymentSlot.start();
  }

  @Override
  public Mono<Response<Void>> startDeploymentSlotAsync(AzureWebClientContext context, String slotName) {
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    return startDeploymentSlotAsync(context, slotName, webAppsClient);
  }

  @Override
  public Mono<Response<Void>> startDeploymentSlotAsync(
      AzureWebClientContext context, String slotName, WebAppsClient webAppsClient) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return startWebAppAsync(context, webAppsClient);
    }

    log.debug("Stopping async slot with name: {}, context: {}", slotName, context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", slotName, context);
    return webAppsClient.startSlotWithResponseAsync(resourceGroupName, webAppName, slotName);
  }
  public Mono<Response<Void>> startWebAppAsync(AzureWebClientContext context, WebAppsClient webAppsClient) {
    log.debug("Starting async, context: {}", context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    return webAppsClient.startWithResponseAsync(resourceGroupName, webAppName);
  }

  @Override
  public void startDeploymentSlot(DeploymentSlot slot) {
    log.debug("Starting slot with name: {}", slot.name());
    slot.start();
  }

  @Override
  public Mono<Void> startDeploymentSlotAsync(DeploymentSlot slot) {
    log.debug("Starting async slot with name: {}", slot.name());
    return slot.startAsync();
  }

  @Override
  public void stopDeploymentSlot(AzureWebClientContext context, final String slotName) {
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    log.debug("Stopping slot with name: {}, context: {}", slotName, context);
    deploymentSlot.stop();
  }

  @Override
  public Mono<Response<Void>> stopDeploymentSlotAsync(AzureWebClientContext context, final String slotName) {
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    return stopDeploymentSlotAsync(context, slotName, webAppsClient);
  }

  @Override
  public Mono<Response<Void>> stopDeploymentSlotAsync(
      AzureWebClientContext context, final String slotName, WebAppsClient webAppsClient) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return stopWebAppAsync(context, webAppsClient);
    }

    log.debug("Stopping async slot with name: {}, context: {}", slotName, context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", slotName, context);
    return webAppsClient.stopSlotWithResponseAsync(resourceGroupName, webAppName, slotName);
  }

  @Override
  public Mono<Response<Void>> stopWebAppAsync(AzureWebClientContext context, WebAppsClient webAppsClient) {
    log.debug("Stopping async, context: {}", context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    return webAppsClient.stopWithResponseAsync(resourceGroupName, webAppName);
  }

  @Override
  public void stopDeploymentSlot(DeploymentSlot slot) {
    log.debug("Stopping slot with name: {}", slot.name());
    slot.stop();
  }

  @Override
  public Mono<Void> stopDeploymentSlotAsync(DeploymentSlot slot) {
    log.debug("Stopping async slot with name: {}", slot.name());
    return slot.stopAsync();
  }

  @Override
  public String getSlotState(AzureWebClientContext context, final String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equalsIgnoreCase(slotName)) {
      return getProductionState(context);
    }
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    log.debug("Start getting slot with slotName: {}, context: {}", slotName, context);
    return deploymentSlot.state();
  }

  private String getProductionState(AzureWebClientContext context) {
    AzureResourceManager azure = getAzureClientByContext(context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebApp webApp = getWebApp(azure, resourceGroupName, webAppName);
    return webApp.state();
  }

  @Override
  public void updateDeploymentSlotAppSettings(AzureWebClientContext context, final String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettings) {
    if (appSettings.isEmpty()) {
      log.info("Slot app settings list is empty, slotName: {}, context: {}", slotName, context);
      return;
    }
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      updateWebAppAppSettings(context, appSettings);
      return;
    }
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);

    log.debug("Start updating slot app settings by slotName: {}, context: {}", slotName, context);

    DeploymentSlot.Update update = deploymentSlot.update();
    appSettings.values().forEach(appSetting
        -> update.withAppSetting(appSetting.getName(), appSetting.getValue())
               .withAppSettingStickiness(appSetting.getName(), appSetting.isSticky()));
    update.apply();
  }

  @Override
  public void updateWebAppAppSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> appSettings) {
    WebApp azureApp = getWebApp(context);
    log.debug("Start updating app settings, context: {}", context);
    WebApp.Update update = azureApp.update();
    appSettings.values().forEach(appSetting
        -> update.withAppSetting(appSetting.getName(), appSetting.getValue())
               .withAppSettingStickiness(appSetting.getName(), appSetting.isSticky()));
    update.apply();
  }

  @Override
  public Map<String, AzureAppServiceApplicationSetting> listDeploymentSlotAppSettings(
      AzureWebClientContext context, final String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return listWebAppAppSettings(context);
    }
    log.debug("Start listing slot app settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, AppSetting> appSettings = deploymentSlot.getAppSettings();

    return appSettings.values().stream().collect(
        Collectors.toMap(AppSetting::key, this::buildAzureAppServiceApplicationSetting));
  }

  @Override
  public Map<String, AzureAppServiceApplicationSetting> listWebAppAppSettings(AzureWebClientContext context) {
    log.debug("Start listing slot app settings, context: {}", context);
    WebApp azureApp = getWebApp(context);
    Map<String, AppSetting> appSettings = azureApp.getAppSettings();

    return appSettings.values().stream().collect(
        Collectors.toMap(AppSetting::key, this::buildAzureAppServiceApplicationSetting));
  }

  public AzureAppServiceApplicationSetting buildAzureAppServiceApplicationSetting(AppSetting appSetting) {
    return AzureAppServiceApplicationSetting.builder()
        .name(appSetting.key())
        .value(appSetting.value())
        .sticky(appSetting.sticky())
        .build();
  }

  public void deleteDeploymentSlotAppSettings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      deleteWebAppAppSettings(context, appSettingsToRemove);
      return;
    }
    log.debug("Start deleting slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    DeploymentSlot.Update update = deploymentSlot.update();
    appSettingsToRemove.keySet().forEach(update::withoutAppSetting);
    update.apply();
  }

  @Override
  public void deleteWebAppAppSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove) {
    log.debug("Start deleting connection settings, context: {}", context);
    WebApp azureApp = getWebApp(context);
    WebApp.Update update = azureApp.update();
    appSettingsToRemove.keySet().forEach(update::withoutAppSetting);
    update.apply();
  }

  @Override
  public void updateDeploymentSlotConnectionStrings(AzureWebClientContext context, final String slotName,
      Map<String, AzureAppServiceConnectionString> connectionStrings) {
    if (connectionStrings.isEmpty()) {
      log.info("Slot connection settings list is empty, slotName: {}, context: {}", slotName, context);
      return;
    }

    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      updateWebAppConnectionStrings(context, connectionStrings);
      return;
    }

    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    DeploymentSlot.Update update = deploymentSlot.update();
    connectionStrings.values().forEach(connString -> {
      String name = connString.getName();
      String value = connString.getValue();
      boolean sticky = connString.isSticky();
      ConnectionStringType type = ConnectionStringType.fromString(connString.getType().getValue());

      update.withConnectionString(name, value, type).withConnectionStringStickiness(name, sticky);
    });
    update.apply();
  }

  @Override
  public void updateWebAppConnectionStrings(
      AzureWebClientContext context, Map<String, AzureAppServiceConnectionString> connectionStrings) {
    WebApp azureApp = getWebApp(context);
    WebApp.Update update = azureApp.update();
    connectionStrings.values().forEach(connString -> {
      String name = connString.getName();
      String value = connString.getValue();
      boolean sticky = connString.isSticky();
      ConnectionStringType type = ConnectionStringType.fromString(connString.getType().getValue());

      update.withConnectionString(name, value, type).withConnectionStringStickiness(name, sticky);
    });
    update.apply();
  }

  @Override
  public Map<String, AzureAppServiceConnectionString> listDeploymentSlotConnectionStrings(
      AzureWebClientContext context, final String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return listWebAppConnectionStrings(context);
    }
    log.debug("Start listing slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, ConnectionString> connSettings = deploymentSlot.getConnectionStrings();

    return connSettings.values().stream().collect(
        Collectors.toMap(ConnectionString::name, this::buildAzureAppServiceConnectionStrings));
  }

  @Override
  public Map<String, AzureAppServiceConnectionString> listWebAppConnectionStrings(AzureWebClientContext context) {
    log.debug("Start listing slot connection settings, context: {}", context);
    WebApp azureApp = getWebApp(context);
    Map<String, ConnectionString> connSettings = azureApp.getConnectionStrings();

    return connSettings.values().stream().collect(
        Collectors.toMap(ConnectionString::name, this::buildAzureAppServiceConnectionStrings));
  }

  public AzureAppServiceConnectionString buildAzureAppServiceConnectionStrings(ConnectionString connectionString) {
    return AzureAppServiceConnectionString.builder()
        .name(connectionString.name())
        .value(connectionString.value())
        .sticky(connectionString.sticky())
        .type(fromValue(connectionString.type().toString()))
        .build();
  }

  public void deleteDeploymentSlotConnectionStrings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      deleteWebAppConnectionStrings(context, connSettingsToRemove);
      return;
    }
    log.debug("Start deleting slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    DeploymentSlot.Update update = deploymentSlot.update();
    connSettingsToRemove.keySet().forEach(update::withoutConnectionString);
    update.apply();
  }

  @Override
  public void deleteWebAppConnectionStrings(
      AzureWebClientContext context, Map<String, AzureAppServiceConnectionString> connSettingsToRemove) {
    log.debug("Start deleting slot connection settings, context: {}", context);
    WebApp azureApp = getWebApp(context);
    WebApp.Update update = azureApp.update();
    connSettingsToRemove.keySet().forEach(update::withoutConnectionString);
    update.apply();
  }

  @Override
  public void updateDeploymentSlotDockerSettings(AzureWebClientContext context, final String slotName,
      Map<String, AzureAppServiceApplicationSetting> dockerSettings) {
    if (dockerSettings.isEmpty()) {
      log.info("Docker settings list is empty, slotName: {}, context: {}", slotName, context);
      return;
    }
    validateDockerSettings(dockerSettings);
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      updateWebAppDockerSettings(context, dockerSettings);
      return;
    }
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);

    log.debug("Start updating slot docker settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot.Update update = deploymentSlot.update();
    dockerSettings.values().forEach(
        dockerSetting -> update.withAppSetting(dockerSetting.getName(), dockerSetting.getValue()));
    update.apply();
  }

  @Override
  public void updateWebAppDockerSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> dockerSettings) {
    WebApp azureApp = getWebApp(context);
    log.debug("Start updating slot docker settings, context: {}", context);
    WebApp.Update update = azureApp.update();
    dockerSettings.values().forEach(
        dockerSetting -> update.withAppSetting(dockerSetting.getName(), dockerSetting.getValue()));
    update.apply();
  }

  private void validateDockerSettings(Map<String, AzureAppServiceApplicationSetting> dockerSettings) {
    dockerSettings.values().forEach(dockerSetting -> {
      String dockerSettingName = dockerSetting.getName();
      if (!AzureResourceUtility.DOCKER_REGISTRY_PROPERTY_NAMES.contains(dockerSettingName)) {
        throw new IllegalArgumentException(format("Not valid docker settings: %s", dockerSettingName));
      }
    });
  }

  @Override
  public Map<String, AzureAppServiceApplicationSetting> listDeploymentSlotDockerSettings(
      AzureWebClientContext context, String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return listWebAppDockerSettings(context);
    }
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, AppSetting> appSettings = deploymentSlot.getAppSettings();

    return appSettings.values()
        .stream()
        .filter(appSetting -> AzureResourceUtility.DOCKER_REGISTRY_PROPERTY_NAMES.contains(appSetting.key()))
        .collect(Collectors.toMap(AppSetting::key, this::buildAzureAppServiceApplicationSetting));
  }

  @Override
  public Map<String, AzureAppServiceApplicationSetting> listWebAppDockerSettings(
      AzureWebClientContext azureWebClientContext) {
    WebApp azureApp = getWebApp(azureWebClientContext);
    Map<String, AppSetting> appSettings = azureApp.getAppSettings();

    return appSettings.values()
        .stream()
        .filter(appSetting -> AzureResourceUtility.DOCKER_REGISTRY_PROPERTY_NAMES.contains(appSetting.key()))
        .collect(Collectors.toMap(AppSetting::key, this::buildAzureAppServiceApplicationSetting));
  }

  @Override
  public void deleteDeploymentSlotDockerSettings(AzureWebClientContext context, final String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      deleteWebAppDockerSettings(context);
      return;
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    StringDictionaryInner siteConfigResourceInner =
        webAppsClient.listApplicationSettingsSlot(resourceGroupName, webAppName, slotName);
    Map<String, String> existingSlotProperties = siteConfigResourceInner.properties();
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME);
    siteConfigResourceInner.withProperties(existingSlotProperties);

    log.debug("Start deleting slot docker settings by slotName: {}, context: {}", slotName, context);
    webAppsClient.updateApplicationSettingsSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void deleteWebAppDockerSettings(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    StringDictionaryInner siteConfigResourceInner =
        webAppsClient.listApplicationSettings(resourceGroupName, webAppName);
    Map<String, String> existingSlotProperties = siteConfigResourceInner.properties();
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME);
    siteConfigResourceInner.withProperties(existingSlotProperties);

    log.debug("Start deleting slot docker settings, context: {}", context);
    webAppsClient.updateApplicationSettings(resourceGroupName, webAppName, siteConfigResourceInner);
  }

  @Override
  public void updateDeploymentSlotDockerImageNameAndTagSettings(
      AzureWebClientContext context, final String slotName, final String imageNameAndTag, WebAppHostingOS hostingOS) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    String dockerImageAndTagPath = AzureResourceUtility.getDockerImageAndTagFullPath(imageNameAndTag);

    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      updateWebAppDockerImageNameAndTagSettings(context, dockerImageAndTagPath, hostingOS);
      return;
    }
    final SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfigurationSlot(resourceGroupName, webAppName, slotName);

    if (WebAppHostingOS.LINUX == hostingOS) {
      siteConfigResourceInner.withLinuxFxVersion(dockerImageAndTagPath);
    } else if (WebAppHostingOS.WINDOWS == hostingOS) {
      siteConfigResourceInner.withWindowsFxVersion(dockerImageAndTagPath);
    } else {
      throw new IllegalArgumentException(format("Unsupported app hosting OS type: %s", hostingOS));
    }

    log.debug(
        "Start updating slot docker image name and tag settings, slotName: {}, context: {}, dockerImageAndTagPath: {}",
        slotName, context, dockerImageAndTagPath);
    webAppsClient.updateConfigurationSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void updateWebAppDockerImageNameAndTagSettings(
      AzureWebClientContext context, final String dockerImageAndTagPath, WebAppHostingOS hostingOS) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    final SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfiguration(resourceGroupName, webAppName);

    if (WebAppHostingOS.LINUX == hostingOS) {
      siteConfigResourceInner.withLinuxFxVersion(dockerImageAndTagPath);
    } else if (WebAppHostingOS.WINDOWS == hostingOS) {
      siteConfigResourceInner.withWindowsFxVersion(dockerImageAndTagPath);
    } else {
      throw new IllegalArgumentException(format("Unsupported app hosting OS type: %s", hostingOS));
    }

    log.debug("Start updating docker image name and tag settings, context: {}, dockerImageAndTagPath: {}", context,
        dockerImageAndTagPath);
    webAppsClient.updateConfiguration(resourceGroupName, webAppName, siteConfigResourceInner);
  }

  @Override
  public void deleteDeploymentSlotDockerImageNameAndTagSettings(AzureWebClientContext context, final String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return;
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfigurationSlot(resourceGroupName, webAppName, slotName);
    siteConfigResourceInner.withLinuxFxVersion(EMPTY);
    siteConfigResourceInner.withWindowsFxVersion(null);

    log.debug("Start deleting slot docker image name and tag by slotName: {}, context: {}", slotName, context);
    webAppsClient.updateConfigurationSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void swapDeploymentSlotWithProduction(AzureWebClientContext context, final String sourceSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    CsmSlotEntity slotSwapEntity = getTargetCsmSlotEntity(sourceSlotName);

    log.debug("Start swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    webAppsClient.swapSlotWithProduction(resourceGroupName, webAppName, slotSwapEntity);
  }

  @Override
  public void swapDeploymentSlots(
      AzureWebClientContext context, final String sourceSlotName, final String targetSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    CsmSlotEntity targetSlotSwapEntity = getTargetCsmSlotEntity(targetSlotName);

    log.debug("Start swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    webAppsClient.swapSlot(resourceGroupName, webAppName, sourceSlotName, targetSlotSwapEntity);
  }

  @Override
  public Mono<Void> swapDeploymentSlotWithProductionAsync(AzureWebClientContext context, final String sourceSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    CsmSlotEntity slotSwapEntity = getTargetCsmSlotEntity(sourceSlotName);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    return webAppsClient.swapSlotWithProductionAsync(resourceGroupName, webAppName, slotSwapEntity);
  }

  @Override
  public Mono<Response<Flux<ByteBuffer>>> swapDeploymentSlotsAsync(
      AzureWebClientContext context, final String sourceSlotName, String targetSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    CsmSlotEntity targetSlotSwapEntity = getTargetCsmSlotEntity(targetSlotName);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    return webAppsClient.swapSlotWithResponseAsync(resourceGroupName, webAppName, sourceSlotName, targetSlotSwapEntity);
  }

  @NotNull
  public CsmSlotEntity getTargetCsmSlotEntity(String targetSlotName) {
    CsmSlotEntity targetSlotSwapEntity = new CsmSlotEntity();
    targetSlotSwapEntity.withPreserveVnet(true);
    targetSlotSwapEntity.withTargetSlot(targetSlotName);
    return targetSlotSwapEntity;
  }

  @NotNull
  private DeploymentSlot getDeploymentSlot(AzureWebClientContext context, final String slotName) {
    Optional<DeploymentSlot> deploymentSlotOp = getDeploymentSlotByName(context, slotName);
    if (!deploymentSlotOp.isPresent()) {
      throw new AzureAppServicesDeploymentSlotNotFoundException(
          slotName, context.getAppName(), context.getResourceGroupName(), context.getSubscriptionId());
    }
    return deploymentSlotOp.get();
  }

  @Override
  public WebAppHostingOS getWebAppHostingOS(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    SiteConfigResourceInner config = webAppsClient.getConfiguration(resourceGroupName, webAppName);
    if (isBlank(config.windowsFxVersion()) && isBlank(config.linuxFxVersion())) {
      throw new IllegalArgumentException(
          format("There is no hosting operating system for subscriptionId: %s, resourceGroupName: %s,"
                  + " webAppName: %s",
              context.getSubscriptionId(), resourceGroupName, webAppName));
    }

    return isNotBlank(config.windowsFxVersion()) ? WebAppHostingOS.WINDOWS : WebAppHostingOS.LINUX;
  }

  @Override
  public Optional<String> getSlotDockerImageNameAndTag(AzureWebClientContext context, String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return getWebAppDockerImageNameAndTag(context);
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    SiteConfigResourceInner slotConfig = webAppsClient.getConfigurationSlot(resourceGroupName, webAppName, slotName);

    String windowsFxVersion = slotConfig.windowsFxVersion();
    if (isNotBlank(windowsFxVersion)) {
      return Optional.of(AzureResourceUtility.removeDockerFxImagePrefix(windowsFxVersion));
    }

    String linuxFxVersion = slotConfig.linuxFxVersion();
    return isNotBlank(linuxFxVersion) ? Optional.of(AzureResourceUtility.removeDockerFxImagePrefix(linuxFxVersion))
                                      : Optional.empty();
  }

  @Override
  public Optional<String> getWebAppDockerImageNameAndTag(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    SiteConfigResourceInner slotConfig = webAppsClient.getConfiguration(resourceGroupName, webAppName);

    String windowsFxVersion = slotConfig.windowsFxVersion();
    if (isNotBlank(windowsFxVersion)) {
      return Optional.of(AzureResourceUtility.removeDockerFxImagePrefix(windowsFxVersion));
    }

    String linuxFxVersion = slotConfig.linuxFxVersion();
    return isNotBlank(linuxFxVersion) ? Optional.of(AzureResourceUtility.removeDockerFxImagePrefix(linuxFxVersion))
                                      : Optional.empty();
  }

  public void rerouteProductionSlotTraffic(
      AzureWebClientContext context, String targetRerouteSlotName, double trafficReroutePercentage) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    DeploymentSlot targetRouteSlot = getDeploymentSlot(context, targetRerouteSlotName);

    List<RampUpRule> rampUpRules = createRampUpRules(targetRouteSlot, trafficReroutePercentage);
    Experiments experiments = createExperiments(rampUpRules);

    log.debug("Start getting app configuration settings, targetRerouteSlotName: {}, context: {}", targetRerouteSlotName,
        context);
    SiteConfigResourceInner siteConfig = webAppsClient.getConfiguration(resourceGroupName, webAppName);
    siteConfig.withExperiments(experiments);

    log.debug("Start rerouting slot traffic, targetRerouteSlotName: {}, context: {}", targetRerouteSlotName, context);
    webAppsClient.updateConfiguration(resourceGroupName, webAppName, siteConfig);
  }

  @NotNull
  private List<RampUpRule> createRampUpRules(DeploymentSlot targetRerouteSlot, double trafficReroutePercentage) {
    String defaultHostName = targetRerouteSlot.defaultHostname();
    String targetRerouteSlotName = targetRerouteSlot.name();

    RampUpRule rampUpRule = new RampUpRule();
    rampUpRule.withActionHostname(defaultHostName);
    rampUpRule.withName(targetRerouteSlotName);
    rampUpRule.withReroutePercentage(trafficReroutePercentage);

    List<RampUpRule> rampUpRules = new ArrayList<>();
    rampUpRules.add(rampUpRule);

    return rampUpRules;
  }

  @NotNull
  private Experiments createExperiments(List<RampUpRule> rampUpRules) {
    Experiments experiments = new Experiments();
    experiments.withRampUpRules(rampUpRules);
    return experiments;
  }

  public double getDeploymentSlotTrafficWeight(AzureWebClientContext context, String slotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    double defaultTrafficWeight = 0.0;

    String slotDefaultHostName = getDeploymentSlotDefaultHostName(context, slotName);

    SiteConfigResourceInner configurationSlot = webAppsClient.getConfiguration(resourceGroupName, webAppName);
    Experiments experiments = configurationSlot.experiments();
    if (experiments == null) {
      return defaultTrafficWeight;
    }

    return experiments.rampUpRules()
        .stream()
        .filter(rule -> slotDefaultHostName.equals(rule.actionHostname()))
        .map(RampUpRule::reroutePercentage)
        .findFirst()
        .orElse(defaultTrafficWeight);
  }

  private String getDeploymentSlotDefaultHostName(AzureWebClientContext context, String slotName) {
    return getDeploymentSlot(context, slotName).defaultHostname();
  }

  public List<WebSiteInstanceStatusInner> listInstanceIdentifiersSlot(AzureWebClientContext context, String slotName) {
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return listInstanceIdentifiers(context, webAppsClient);
    }

    String appName = context.getAppName();
    String resourceGroupName = context.getResourceGroupName();

    log.debug("Start listing instance identifiers for slot, resourceGroupName: {}, slotName: {}, context: {}",
        resourceGroupName, slotName, context);
    return webAppsClient.listInstanceIdentifiersSlot(resourceGroupName, appName, slotName)
        .stream()
        .collect(Collectors.toList());
  }

  @Override
  public List<WebSiteInstanceStatusInner> listInstanceIdentifiers(
      AzureWebClientContext context, WebAppsClient webAppsClient) {
    String appName = context.getAppName();
    String resourceGroupName = context.getResourceGroupName();

    log.debug("Start listing instance identifiers for WebApp, resourceGroupName: {}, context: {}", resourceGroupName,
        context);
    return webAppsClient.listInstanceIdentifiers(resourceGroupName, appName).stream().collect(Collectors.toList());
  }

  public void deployZipToSlot(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug("Start deploying zip file on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    deploymentSlot.zipDeploy(file);
  }

  public Mono<Void> deployZipToSlotAsync(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return deployZipToWebAppAsync(context, file);
    }

    log.debug("Start deploying zip file async on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.zipDeployAsync(file);
  }

  @Override
  public Mono<Void> deployZipToWebAppAsync(AzureWebClientContext context, File file) {
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug("Start deploying zip file async, fileAbsoluteFile: {}, context: {}", file.getAbsolutePath(), context);
    WebApp azureApp = getWebApp(context);
    return azureApp.zipDeployAsync(file);
  }

  public void deployWarToSlot(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug("Start deploying war file on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    deploymentSlot.warDeploy(file);
  }

  public Mono<Void> deployWarToSlotAsync(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return deployWarToWebAppAsync(context, file);
    }

    log.debug("Start deploying war file async on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.warDeployAsync(file);
  }

  @Override
  public Mono<Void> deployWarToWebAppAsync(AzureWebClientContext context, File file) {
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug(
        "Start deploying war file async on slot, fileAbsoluteFile: {}, context: {}", file.getAbsolutePath(), context);
    WebApp azureApp = getWebApp(context);
    return azureApp.warDeployAsync(file);
  }

  public InputStream streamDeploymentLogs(AzureWebClientContext context, final String slotName) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start streaming deployment log on slot, slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.streamDeploymentLogs();
  }

  public Flux<String> streamDeploymentLogsAsync(AzureWebClientContext context, final String slotName) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return streamDeploymentLogsAsync(context);
    }
    log.debug("Start streaming deployment log on slot, slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.streamDeploymentLogsAsync();
  }

  @Override
  public Flux<String> streamDeploymentLogsAsync(AzureWebClientContext context) {
    log.debug("Start streaming deployment log, context: {}", context);
    WebApp azureApp = getWebApp(context);
    return azureApp.streamDeploymentLogsAsync();
  }

  public SiteConfigResourceInner updateSlotConfigurationWithAppCommandLineScript(
      AzureWebClientContext context, final String slotName, final String startupCommand) {
    if (startupCommand == null) {
      return null;
    }
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return updateWebAppConfigurationWithAppCommandLineScript(context, startupCommand);
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(webAppName)) {
      throw new IllegalArgumentException(WEB_APP_NAME_BLANK_ERROR_MSG);
    }
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    final SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfigurationSlot(resourceGroupName, webAppName, slotName);
    siteConfigResourceInner.withAppCommandLine(startupCommand);

    log.debug("Start updating slot with app command line, slotName: {}, context: {}", slotName, context);
    return webAppsClient.updateConfigurationSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public SiteConfigResourceInner updateWebAppConfigurationWithAppCommandLineScript(
      AzureWebClientContext context, String startupCommand) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();
    if (isBlank(webAppName)) {
      throw new IllegalArgumentException(WEB_APP_NAME_BLANK_ERROR_MSG);
    }
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    final SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfiguration(resourceGroupName, webAppName);
    siteConfigResourceInner.withAppCommandLine(startupCommand);

    log.debug("Start updating web app with app command line, context: {}", context);
    return webAppsClient.updateConfiguration(resourceGroupName, webAppName, siteConfigResourceInner);
  }

  public String getDeploymentSlotStartupCommand(AzureWebClientContext context, String slotName) {
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      return getWebAppStartupCommand(context);
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(webAppName)) {
      throw new IllegalArgumentException(WEB_APP_NAME_BLANK_ERROR_MSG);
    }
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    log.debug("Start getting slot configs, slotName: {}, context: {}", slotName, context);
    SiteConfigResourceInner siteConfigResourceInner =
        webAppsClient.getConfigurationSlot(resourceGroupName, webAppName, slotName);

    return siteConfigResourceInner.appCommandLine();
  }

  @Override
  public String getWebAppStartupCommand(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    if (isBlank(webAppName)) {
      throw new IllegalArgumentException(WEB_APP_NAME_BLANK_ERROR_MSG);
    }
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    WebAppsClient webAppsClient = getWebSiteManagementClient(context).getWebApps();

    log.debug("Start getting slot configs, context: {}", context);
    SiteConfigResourceInner siteConfigResourceInner = webAppsClient.getConfiguration(resourceGroupName, webAppName);

    return siteConfigResourceInner.appCommandLine();
  }

  protected WebSiteManagementClient getWebSiteManagementClient(AzureClientContext context) {
    return getWebSiteManagementClient(
        context.getAzureConfig(), context.getSubscriptionId(), context.isExtendedReadTimeout());
  }

  protected WebSiteManagementClient getWebSiteManagementClient(AzureConfig azureConfig) {
    return getWebSiteManagementClient(azureConfig, null);
  }

  protected WebSiteManagementClient getWebSiteManagementClient(AzureConfig azureConfig, String subscriptionId) {
    return new WebSiteManagementClientBuilder()
        .subscriptionId(subscriptionId)
        .pipeline(getAzureHttpPipeline(azureConfig, subscriptionId))
        .endpoint(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getResourceManagerEndpoint())
        .environment(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()))
        .buildClient();
  }

  protected WebSiteManagementClient getWebSiteManagementClient(
      AzureConfig azureConfig, String subscriptionId, boolean extendedReadTimeout) {
    return new WebSiteManagementClientBuilder()
        .subscriptionId(subscriptionId)
        .pipeline(getAzureHttpPipeline(azureConfig, subscriptionId, extendedReadTimeout))
        .endpoint(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getResourceManagerEndpoint())
        .environment(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()))
        .buildClient();
  }
}
