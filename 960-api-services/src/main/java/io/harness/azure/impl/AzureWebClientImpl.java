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
import io.harness.azure.model.WebAppHostingOS;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Singleton;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.AppSetting;
import com.microsoft.azure.management.appservice.ConnectionString;
import com.microsoft.azure.management.appservice.ConnectionStringType;
import com.microsoft.azure.management.appservice.CsmSlotEntity;
import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.Experiments;
import com.microsoft.azure.management.appservice.RampUpRule;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.management.appservice.implementation.SiteConfigResourceInner;
import com.microsoft.azure.management.appservice.implementation.SiteInstanceInner;
import com.microsoft.azure.management.appservice.implementation.StringDictionaryInner;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceFuture;
import java.io.File;
import java.io.InputStream;
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
import rx.Completable;
import rx.Observable;

@Singleton
@Slf4j
public class AzureWebClientImpl extends AzureClient implements AzureWebClient {
  @Override
  public List<WebApp> listWebAppsByResourceGroupName(AzureClientContext context) {
    String subscriptionId = context.getSubscriptionId();
    String resourceGroupName = context.getResourceGroupName();
    Azure azure = getAzureClientByContext(context);

    log.debug("Start getting Web Applications by subscriptionId: {}, resourceGroupName: {}", subscriptionId,
        resourceGroupName);
    Instant startListingWebApps = Instant.now();
    PagedList<WebApp> webApps = azure.webApps().listByResourceGroup(resourceGroupName);

    List<WebApp> webAppList = new ArrayList<>();
    for (WebApp app : webApps) {
      webAppList.add(app);
    }

    long elapsedTime = Duration.between(startListingWebApps, Instant.now()).toMillis();
    log.info("Obtained Web Applications items: {} for elapsed time: {}, resourceGroupName: {}, subscriptionId: {} ",
        webAppList.size(), elapsedTime, resourceGroupName, subscriptionId);

    return webAppList;
  }

  @Override
  public List<DeploymentSlot> listDeploymentSlotsByWebAppName(AzureWebClientContext context) {
    String subscriptionId = context.getSubscriptionId();
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    log.debug("Start listing deployment slots by subscriptionId: {}, resourceGroupName: {}, webAppName: {}",
        subscriptionId, resourceGroupName, webAppName);
    WebApp webApp = getWebApp(azure, resourceGroupName, webAppName);
    PagedList<DeploymentSlot> deploymentSlots = webApp.deploymentSlots().list();

    List<DeploymentSlot> deploymentSlotList = new ArrayList<>();
    for (DeploymentSlot slot : deploymentSlots) {
      deploymentSlotList.add(slot);
    }

    return deploymentSlotList;
  }

  @NotNull
  private WebApp getWebApp(Azure azure, String resourceGroupName, String webAppName) {
    WebApp webApp = azure.webApps().getByResourceGroup(resourceGroupName, webAppName);
    if (webApp == null) {
      throw new IllegalArgumentException(
          format("Not found web app with name: %s, resource group name: %s", webAppName, resourceGroupName));
    }
    return webApp;
  }

  @Override
  public Optional<WebApp> getWebAppByName(AzureWebClientContext context) {
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    log.debug("Start getting web app by webAppName: {}, context: {}", webAppName, context);
    return Optional.ofNullable(azure.webApps().getByResourceGroup(context.getResourceGroupName(), webAppName));
  }

  @Override
  public Optional<DeploymentSlot> getDeploymentSlotByName(AzureWebClientContext context, final String slotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug(
        "Start getting deployment slot by slotName: {} webAppName: {}, context: {}", slotName, webAppName, context);
    try {
      return Optional.ofNullable(getWebApp(azure, resourceGroupName, webAppName).deploymentSlots().getByName(slotName));
    } catch (NoSuchElementException e) {
      log.warn(format("Unable to find deployment slot with name: %s, for app name: %s", slotName, webAppName),
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
  public Completable startDeploymentSlotAsync(AzureWebClientContext context, final String slotName) {
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    log.debug("Starting async slot with name: {}, context: {}", slotName, context);
    return deploymentSlot.startAsync();
  }

  @Override
  public void startDeploymentSlotAsync(AzureWebClientContext context, String slotName, ServiceCallback<Void> callback) {
    log.debug("Stopping async slot with name: {}, context: {}", slotName, context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", slotName, context);
    azure.webApps().inner().startSlotAsync(resourceGroupName, webAppName, slotName, callback);
  }

  @Override
  public void startDeploymentSlot(DeploymentSlot slot) {
    log.debug("Starting slot with name: {}", slot.name());
    slot.start();
  }

  @Override
  public Completable startDeploymentSlotAsync(DeploymentSlot slot) {
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
  public Completable stopDeploymentSlotAsync(AzureWebClientContext context, final String slotName) {
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    log.debug("Stopping async slot with name: {}, context: {}", slotName, context);
    return deploymentSlot.stopAsync();
  }

  @Override
  public void stopDeploymentSlotAsync(
      AzureWebClientContext context, final String slotName, ServiceCallback<Void> callback) {
    log.debug("Stopping async slot with name: {}, context: {}", slotName, context);
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", slotName, context);
    azure.webApps().inner().stopSlotAsync(resourceGroupName, webAppName, slotName, callback);
  }

  @Override
  public void stopDeploymentSlot(DeploymentSlot slot) {
    log.debug("Stopping slot with name: {}", slot.name());
    slot.stop();
  }

  @Override
  public Completable stopDeploymentSlotAsync(DeploymentSlot slot) {
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
    Azure azure = getAzureClientByContext(context);
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
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);

    log.debug("Start updating slot app settings by slotName: {}, context: {}", slotName, context);

    DeploymentSlot.Update update = deploymentSlot.update();
    appSettings.values().forEach(appSetting
        -> update.withAppSetting(appSetting.getName(), appSetting.getValue())
               .withAppSettingStickiness(appSetting.getName(), appSetting.isSticky()));
    update.apply();
  }

  @Override
  public Map<String, AzureAppServiceApplicationSetting> listDeploymentSlotAppSettings(
      AzureWebClientContext context, final String slotName) {
    log.debug("Start listing slot app settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, AppSetting> appSettings = deploymentSlot.getAppSettings();

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

  public DeploymentSlot deleteDeploymentSlotAppSettings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove) {
    log.debug("Start deleting slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    DeploymentSlot.Update update = deploymentSlot.update();
    appSettingsToRemove.keySet().forEach(update::withoutAppSetting);
    return update.apply();
  }

  @Override
  public void updateDeploymentSlotConnectionStrings(AzureWebClientContext context, final String slotName,
      Map<String, AzureAppServiceConnectionString> connectionStrings) {
    if (connectionStrings.isEmpty()) {
      log.info("Slot connection settings list is empty, slotName: {}, context: {}", slotName, context);
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
  public Map<String, AzureAppServiceConnectionString> listDeploymentSlotConnectionStrings(
      AzureWebClientContext context, final String slotName) {
    log.debug("Start listing slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, ConnectionString> connSettings = deploymentSlot.getConnectionStrings();

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

  public DeploymentSlot deleteDeploymentSlotConnectionStrings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove) {
    log.debug("Start deleting slot connection settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    DeploymentSlot.Update update = deploymentSlot.update();
    connSettingsToRemove.keySet().forEach(update::withoutConnectionString);
    return update.apply();
  }

  @Override
  public void updateDeploymentSlotDockerSettings(AzureWebClientContext context, final String slotName,
      Map<String, AzureAppServiceApplicationSetting> dockerSettings) {
    if (dockerSettings.isEmpty()) {
      log.info("Docker settings list is empty, slotName: {}, context: {}", slotName, context);
      return;
    }
    validateDockerSettings(dockerSettings);

    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);

    log.debug("Start updating slot docker settings by slotName: {}, context: {}", slotName, context);
    DeploymentSlot.Update update = deploymentSlot.update();
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
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    Map<String, AppSetting> appSettings = deploymentSlot.getAppSettings();

    return appSettings.values()
        .stream()
        .filter(appSetting -> AzureResourceUtility.DOCKER_REGISTRY_PROPERTY_NAMES.contains(appSetting.key()))
        .collect(Collectors.toMap(AppSetting::key, this::buildAzureAppServiceApplicationSetting));
  }

  @Override
  public void deleteDeploymentSlotDockerSettings(AzureWebClientContext context, final String slotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    StringDictionaryInner siteConfigResourceInner =
        azure.webApps().inner().listApplicationSettingsSlot(resourceGroupName, webAppName, slotName);
    Map<String, String> existingSlotProperties = siteConfigResourceInner.properties();
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    existingSlotProperties.remove(DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME);
    siteConfigResourceInner.withProperties(existingSlotProperties);

    log.debug("Start deleting slot docker settings by slotName: {}, context: {}", slotName, context);
    azure.webApps().inner().updateApplicationSettingsSlot(
        resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void updateDeploymentSlotDockerImageNameAndTagSettings(
      AzureWebClientContext context, final String slotName, final String imageNameAndTag, WebAppHostingOS hostingOS) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    final SiteConfigResourceInner siteConfigResourceInner =
        azure.webApps().inner().getConfigurationSlot(resourceGroupName, webAppName, slotName);

    String dockerImageAndTagPath = AzureResourceUtility.getDockerImageAndTagFullPath(imageNameAndTag);
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
    azure.webApps().inner().updateConfigurationSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void deleteDeploymentSlotDockerImageNameAndTagSettings(AzureWebClientContext context, final String slotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);

    SiteConfigResourceInner siteConfigResourceInner =
        azure.webApps().inner().getConfigurationSlot(resourceGroupName, webAppName, slotName);
    siteConfigResourceInner.withLinuxFxVersion(EMPTY);
    siteConfigResourceInner.withWindowsFxVersion(null);

    log.debug("Start deleting slot docker image name and tag by slotName: {}, context: {}", slotName, context);
    azure.webApps().inner().updateConfigurationSlot(resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  @Override
  public void swapDeploymentSlotWithProduction(AzureWebClientContext context, final String sourceSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    CsmSlotEntity slotSwapEntity = getTargetCsmSlotEntity(sourceSlotName);

    log.debug("Start swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    azure.webApps().inner().swapSlotWithProduction(resourceGroupName, webAppName, slotSwapEntity);
  }

  @Override
  public void swapDeploymentSlots(
      AzureWebClientContext context, final String sourceSlotName, final String targetSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    CsmSlotEntity targetSlotSwapEntity = getTargetCsmSlotEntity(targetSlotName);

    log.debug("Start swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    azure.webApps().inner().swapSlotSlot(resourceGroupName, webAppName, sourceSlotName, targetSlotSwapEntity);
  }

  @Override
  public Observable<Void> swapDeploymentSlotWithProductionAsync(
      AzureWebClientContext context, final String sourceSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    CsmSlotEntity slotSwapEntity = getTargetCsmSlotEntity(sourceSlotName);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    return azure.webApps().inner().swapSlotWithProductionAsync(resourceGroupName, webAppName, slotSwapEntity);
  }

  @Override
  public Observable<Void> swapDeploymentSlotsAsync(
      AzureWebClientContext context, final String sourceSlotName, final String targetSlotName) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    CsmSlotEntity targetSlotSwapEntity = getTargetCsmSlotEntity(targetSlotName);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    return azure.webApps().inner().swapSlotSlotAsync(
        resourceGroupName, webAppName, sourceSlotName, targetSlotSwapEntity);
  }

  @Override
  public ServiceFuture<Void> swapDeploymentSlotsAsync(AzureWebClientContext context, final String sourceSlotName,
      String targetSlotName, ServiceCallback<Void> callback) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    CsmSlotEntity targetSlotSwapEntity = getTargetCsmSlotEntity(targetSlotName);

    log.debug("Start async swapping slot with production, slotName: {}, context: {}", sourceSlotName, context);
    return azure.webApps().inner().swapSlotSlotAsync(
        resourceGroupName, webAppName, sourceSlotName, targetSlotSwapEntity, callback);
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
      throw new IllegalArgumentException(format(
          "Unable to get deployment slot by slot name: %s, app name: %s, subscription id: %s, resource group name: %s",
          slotName, context.getAppName(), context.getSubscriptionId(), context.getResourceGroupName()));
    }
    return deploymentSlotOp.get();
  }

  @Override
  public WebAppHostingOS getWebAppHostingOS(AzureWebClientContext context) {
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    SiteConfigResourceInner config = azure.webApps().inner().getConfiguration(resourceGroupName, webAppName);
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
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
    SiteConfigResourceInner slotConfig =
        azure.webApps().inner().getConfigurationSlot(resourceGroupName, webAppName, slotName);

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
    Azure azure = getAzureClientByContext(context);
    DeploymentSlot targetRouteSlot = getDeploymentSlot(context, targetRerouteSlotName);

    List<RampUpRule> rampUpRules = createRampUpRules(targetRouteSlot, trafficReroutePercentage);
    Experiments experiments = createExperiments(rampUpRules);

    log.debug("Start getting app configuration settings, targetRerouteSlotName: {}, context: {}", targetRerouteSlotName,
        context);
    SiteConfigResourceInner siteConfig = azure.webApps().inner().getConfiguration(resourceGroupName, webAppName);
    siteConfig.withExperiments(experiments);

    log.debug("Start rerouting slot traffic, targetRerouteSlotName: {}, context: {}", targetRerouteSlotName, context);
    azure.webApps().inner().updateConfiguration(resourceGroupName, webAppName, siteConfig);
  }

  @NotNull
  private List<RampUpRule> createRampUpRules(DeploymentSlot targetRerouteSlot, double trafficReroutePercentage) {
    String defaultHostName = targetRerouteSlot.defaultHostName();
    String targetRerouteSlotName = targetRerouteSlot.name();

    RampUpRule rampUpRule = new RampUpRule();
    rampUpRule.withActionHostName(defaultHostName);
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
    Azure azure = getAzureClientByContext(context);
    double defaultTrafficWeight = 0.0;

    String slotDefaultHostName = getDeploymentSlotDefaultHostName(context, slotName);

    SiteConfigResourceInner configurationSlot = azure.webApps().inner().getConfiguration(resourceGroupName, webAppName);
    Experiments experiments = configurationSlot.experiments();
    if (experiments == null) {
      return defaultTrafficWeight;
    }

    return experiments.rampUpRules()
        .stream()
        .filter(rule -> slotDefaultHostName.equals(rule.actionHostName()))
        .map(RampUpRule::reroutePercentage)
        .findFirst()
        .orElse(defaultTrafficWeight);
  }

  private String getDeploymentSlotDefaultHostName(AzureWebClientContext context, String slotName) {
    return getDeploymentSlot(context, slotName).defaultHostName();
  }

  public List<SiteInstanceInner> listInstanceIdentifiersSlot(AzureWebClientContext context, String slotName) {
    Azure azure = getAzureClientByContext(context);
    String appName = context.getAppName();
    String resourceGroupName = context.getResourceGroupName();

    log.debug("Start listing instance identifiers for slot, resourceGroupName: {}, slotName: {}, context: {}",
        resourceGroupName, slotName, context);
    PagedList<SiteInstanceInner> siteInstanceInners =
        azure.webApps().inner().listInstanceIdentifiersSlot(resourceGroupName, appName, slotName);
    return new ArrayList<>(siteInstanceInners);
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

  public Completable deployZipToSlotAsync(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug("Start deploying zip file async on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.zipDeployAsync(file);
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

  public Completable deployWarToSlotAsync(AzureWebClientContext context, final String slotName, final File file) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }
    if (file == null) {
      throw new IllegalArgumentException(FILE_BLANK_ERROR_MSG);
    }

    log.debug("Start deploying war file async on slot, fileAbsoluteFile: {}, slotName: {}, context: {}",
        file.getAbsolutePath(), slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.warDeployAsync(file);
  }

  public InputStream streamDeploymentLogs(AzureWebClientContext context, final String slotName) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start streaming deployment log on slot, slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.streamDeploymentLogs();
  }

  public Observable<String> streamDeploymentLogsAsync(AzureWebClientContext context, final String slotName) {
    if (isBlank(slotName)) {
      throw new IllegalArgumentException(SLOT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start streaming deployment log on slot, slotName: {}, context: {}", slotName, context);
    DeploymentSlot deploymentSlot = getDeploymentSlot(context, slotName);
    return deploymentSlot.streamDeploymentLogsAsync();
  }

  public SiteConfigResourceInner updateSlotConfigurationWithAppCommandLineScript(
      AzureWebClientContext context, final String slotName, final String startupCommand) {
    if (isBlank(startupCommand)) {
      return null;
    }
    String resourceGroupName = context.getResourceGroupName();
    String webAppName = context.getAppName();
    Azure azure = getAzureClientByContext(context);
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
        azure.webApps().inner().getConfigurationSlot(resourceGroupName, webAppName, slotName);
    siteConfigResourceInner.withAppCommandLine(startupCommand);

    log.debug("Start updating slot with app command line, slotName: {}, context: {}", slotName, context);
    return azure.webApps().inner().updateConfigurationSlot(
        resourceGroupName, webAppName, slotName, siteConfigResourceInner);
  }

  public String getDeploymentSlotStartupCommand(AzureWebClientContext context, String slotName) {
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
    Azure azure = getAzureClientByContext(context);

    log.debug("Start getting slot configs, slotName: {}, context: {}", slotName, context);
    SiteConfigResourceInner siteConfigResourceInner =
        azure.webApps().inner().getConfigurationSlot(resourceGroupName, webAppName, slotName);

    return siteConfigResourceInner.appCommandLine();
  }
}
