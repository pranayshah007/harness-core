/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.context.AzureClientContext;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.WebAppHostingOS;

import com.azure.core.http.rest.Response;
import com.azure.resourcemanager.appservice.fluent.WebAppsClient;
import com.azure.resourcemanager.appservice.fluent.models.SiteConfigResourceInner;
import com.azure.resourcemanager.appservice.fluent.models.WebSiteInstanceStatusInner;
import com.azure.resourcemanager.appservice.models.DeployOptions;
import com.azure.resourcemanager.appservice.models.DeployType;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.azure.resourcemanager.appservice.models.WebAppBasic;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AzureWebClient {
  /**
   * List web application by resource group name.
   *
   * @param context
   * @return
   */
  List<WebAppBasic> listWebAppsByResourceGroupName(AzureClientContext context);

  /**
   * List deployment slots by web application name.
   *
   * @param context
   * @return
   */
  List<WebDeploymentSlotBasic> listDeploymentSlotsByWebAppName(AzureWebClientContext context);

  /**
   * Get Web Application by name.
   *
   * @param context
   * @return
   */
  Optional<WebApp> getWebAppByName(AzureWebClientContext context);

  WebApp getWebApp(AzureWebClientContext context);

  /**
   * Get deployment slot by name.
   *
   * @param context
   * @param slotName
   * @return
   */
  Optional<DeploymentSlot> getDeploymentSlotByName(AzureWebClientContext context, String slotName);

  /**
   * Start deployment slot.
   *
   * @param context
   * @param slotName
   */
  void startDeploymentSlot(AzureWebClientContext context, String slotName);

  /**
   * Start deployment slot async.
   *
   * @param context
   * @param slotName
   * @return
   */
  Mono<Response<Void>> startDeploymentSlotAsync(AzureWebClientContext context, String slotName);

  /**
   * Start deployment slot async.
   *
   * @param context
   * @param slotName
   * @param webAppsClient
   * @return
   */
  Mono<Response<Void>> startDeploymentSlotAsync(
      AzureWebClientContext context, String slotName, WebAppsClient webAppsClient);

  Mono<Response<Void>> startWebAppAsync(AzureWebClientContext context, WebAppsClient webAppsClient);

  /**
   * Start deployment slot.
   *
   * @param slot
   */
  void startDeploymentSlot(DeploymentSlot slot);

  /**
   * Start deployment slot async.
   *
   * @param slot
   * @return
   */
  Mono<Void> startDeploymentSlotAsync(DeploymentSlot slot);

  /**
   * Stop deployment slot.
   *
   * @param context
   * @param slotName
   */
  void stopDeploymentSlot(AzureWebClientContext context, String slotName);

  /**
   * Stop deployment slot async.
   *
   * @param context
   * @param slotName
   * @return
   */
  Mono<Response<Void>> stopDeploymentSlotAsync(AzureWebClientContext context, String slotName);

  /**
   * Stop deployment slot async.
   *
   * @param context
   * @param slotName
   * @param webAppsClient
   * @return
   */
  Mono<Response<Void>> stopDeploymentSlotAsync(
      AzureWebClientContext context, String slotName, WebAppsClient webAppsClient);

  Mono<Response<Void>> stopWebAppAsync(AzureWebClientContext context, WebAppsClient webAppsClient);

  /**
   * Stop deployment slot.
   *
   * @param slot
   */
  void stopDeploymentSlot(DeploymentSlot slot);

  /**
   * Stop deployment slot async.
   *
   * @param slot
   * @return
   */
  Mono<Void> stopDeploymentSlotAsync(DeploymentSlot slot);

  /**
   * Get deployment slot state.
   *
   * @param context
   * @param slotName
   * @return
   */
  String getSlotState(AzureWebClientContext context, String slotName);

  /**
   * Update deployment slot application settings.
   *
   * @param context
   * @param slotName
   * @param appSettings
   */
  void updateDeploymentSlotAppSettings(
      AzureWebClientContext context, String slotName, Map<String, AzureAppServiceApplicationSetting> appSettings);

  void updateWebAppAppSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> appSettings);

  /**
   * List deployment slot application settings.
   *
   * @param context
   * @param slotName
   * @return
   */
  Map<String, AzureAppServiceApplicationSetting> listDeploymentSlotAppSettings(
      AzureWebClientContext context, String slotName);

  void deleteWebAppAppSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove);

  /**
   * Update deployment slot connection settings.
   *
   * @param context
   * @param slotName
   * @param connectionSettings
   */
  void updateDeploymentSlotConnectionStrings(
      AzureWebClientContext context, String slotName, Map<String, AzureAppServiceConnectionString> connectionSettings);

  void updateWebAppConnectionStrings(
      AzureWebClientContext context, Map<String, AzureAppServiceConnectionString> connectionStrings);

  /**
   * List deployment slot connection settings.
   *
   * @param context
   * @param slotName
   * @return
   */
  Map<String, AzureAppServiceConnectionString> listDeploymentSlotConnectionStrings(
      AzureWebClientContext context, String slotName);

  void deleteWebAppConnectionStrings(
      AzureWebClientContext context, Map<String, AzureAppServiceConnectionString> connSettingsToRemove);

  /**
   * Update deployment slot docker settings.
   *
   * @param context
   * @param slotName
   * @param dockerSettings
   */
  void updateDeploymentSlotDockerSettings(
      AzureWebClientContext context, String slotName, Map<String, AzureAppServiceApplicationSetting> dockerSettings);

  void updateWebAppDockerSettings(
      AzureWebClientContext context, Map<String, AzureAppServiceApplicationSetting> dockerSettings);

  /**
   * List deployment slot docker settings.
   *
   * @param azureWebClientContext
   * @param slotName
   * @return
   */
  Map<String, AzureAppServiceApplicationSetting> listDeploymentSlotDockerSettings(
      AzureWebClientContext azureWebClientContext, String slotName);

  Map<String, AzureAppServiceApplicationSetting> listWebAppDockerSettings(AzureWebClientContext azureWebClientContext);

  /**
   * List deployment slot docker settings.
   *
   * @param context
   * @param slotName
   */
  void deleteDeploymentSlotDockerSettings(AzureWebClientContext context, String slotName);

  void deleteWebAppDockerSettings(AzureWebClientContext context);

  /**
   * Update deployment slot docker image name and tag settings.
   *
   * @param context
   * @param slotName
   * @param newImageAndTag
   * @param hostingOS
   */
  void updateDeploymentSlotDockerImageNameAndTagSettings(
      AzureWebClientContext context, String slotName, String newImageAndTag, WebAppHostingOS hostingOS);

  void updateWebAppDockerImageNameAndTagSettings(
      AzureWebClientContext context, String dockerImageAndTagPath, WebAppHostingOS hostingOS);

  /**
   * Delete deployment slot docker image name and tag settings.
   *
   * @param context
   * @param slotName
   */
  void deleteDeploymentSlotDockerImageNameAndTagSettings(AzureWebClientContext context, String slotName);

  /**
   * Swap deployment slot with production slot.
   *
   * @param context
   * @param sourceSlotName
   */
  void swapDeploymentSlotWithProduction(AzureWebClientContext context, String sourceSlotName);

  /**
   * Swap deployment slots.
   *
   * @param context
   * @param sourceSlotName
   * @param targetSlotName
   */
  void swapDeploymentSlots(AzureWebClientContext context, String sourceSlotName, String targetSlotName);

  /**
   * Swap deployment slot with production async.
   *
   * @param context
   * @param sourceSlotName
   * @return
   */
  Mono<Void> swapDeploymentSlotWithProductionAsync(AzureWebClientContext context, String sourceSlotName);

  /**
   * Swap deployment slots async.
   *
   * @param context
   * @param sourceSlotName
   * @param targetSlotName
   * @return
   */

  Mono<Response<Flux<ByteBuffer>>> swapDeploymentSlotsAsync(
      AzureWebClientContext context, String sourceSlotName, String targetSlotName);
  /**
   * Get Web App hosting operating system.
   *
   * @param azureWebClientContext
   * @return
   */
  WebAppHostingOS getWebAppHostingOS(AzureWebClientContext azureWebClientContext);

  /**
   * Get docker image name and tag.
   *
   * @param context
   * @param slotName
   * @return
   */
  Optional<String> getSlotDockerImageNameAndTag(AzureWebClientContext context, String slotName);

  Optional<String> getWebAppDockerImageNameAndTag(AzureWebClientContext context);

  /**
   * Reroute production slot traffic to target slot in percentage.
   *
   * @param context
   * @param targetRerouteSlotName
   * @param trafficReroutePercentage
   * @return
   */
  void rerouteProductionSlotTraffic(
      AzureWebClientContext context, String targetRerouteSlotName, double trafficReroutePercentage);

  Map<String, AzureAppServiceApplicationSetting> listWebAppAppSettings(AzureWebClientContext context);

  /**
   * Delete deployment slot application settings.
   *
   * @param context
   * @param slotName
   * @param appSettingsToRemove
   */
  void deleteDeploymentSlotAppSettings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove);

  Map<String, AzureAppServiceConnectionString> listWebAppConnectionStrings(AzureWebClientContext context);

  /**
   * Delete deployment slot connections settings.
   *
   * @param context
   * @param slotName
   * @param connSettingsToRemove
   */
  void deleteDeploymentSlotConnectionStrings(AzureWebClientContext context, String slotName,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove);

  /**
   * Get deployment slot current traffic weight.
   *
   * @param context
   * @param slotName
   * @return
   */
  double getDeploymentSlotTrafficWeight(AzureWebClientContext context, String slotName);

  /**
   * List slot instance identifiers.
   *
   * @param context
   * @param slotName
   * @return
   */
  List<WebSiteInstanceStatusInner> listInstanceIdentifiersSlot(AzureWebClientContext context, String slotName);

  List<WebSiteInstanceStatusInner> listInstanceIdentifiers(AzureWebClientContext context, WebAppsClient webAppsClient);

  /**
   * Deploying ZIP to deployment slot.
   *
   * @param context
   * @param slotName
   * @param file
   */
  void deployZipToSlot(AzureWebClientContext context, String slotName, File file);

  /**
   * Deploying ZIP async to deployment slot.
   *
   * @param context
   * @param slotName
   * @param file
   * @return
   */
  Mono<Void> deployZipToSlotAsync(AzureWebClientContext context, String slotName, File file);

  /**
   * Deploying async to deployment slot.
   *
   * @param context
   * @param type
   * @param slotName
   * @param file
   * @param options
   * @return
   */
  Mono<Void> deployAsync(
      AzureWebClientContext context, DeployType type, String slotName, File file, DeployOptions options);

  /**
   * Deploying async to deployment slot.
   *
   * @param context
   * @param type
   * @param file
   * @param options
   * @return
   */
  Mono<Void> deployToWebAppAsync(AzureWebClientContext context, DeployType type, File file, DeployOptions options);

  Mono<Void> deployZipToWebAppAsync(AzureWebClientContext context, File file);

  /**
   * Deploying WAR to deployment slot.
   *
   * @param context
   * @param slotName
   * @param file
   */
  void deployWarToSlot(AzureWebClientContext context, String slotName, File file);

  /**
   * Deploying WAR async to deployment slot.
   *
   * @param context
   * @param slotName
   * @param file
   * @return
   */
  Mono<Void> deployWarToSlotAsync(AzureWebClientContext context, String slotName, File file);

  Mono<Void> deployWarToWebAppAsync(AzureWebClientContext context, File file);

  /**
   * Stream deployment logs on slot.
   *
   * @param context
   * @param slotName
   * @return
   */
  InputStream streamDeploymentLogs(AzureWebClientContext context, String slotName);

  /**
   * Stream deployment logs on slot asynchronously.
   *
   * @param context
   * @param slotName
   * @return
   */
  Flux<String> streamDeploymentLogsAsync(AzureWebClientContext context, String slotName);

  Flux<String> streamDeploymentLogsAsync(AzureWebClientContext context);

  /**
   * Update slot configuration with app command line script.
   *
   * @param context
   * @param slotName
   * @param startupCommand
   * @return
   */
  SiteConfigResourceInner updateSlotConfigurationWithAppCommandLineScript(
      AzureWebClientContext context, String slotName, String startupCommand);

  SiteConfigResourceInner updateWebAppConfigurationWithAppCommandLineScript(
      AzureWebClientContext context, String startupCommand);

  /**
   * Get deployment slot startup command.
   *
   * @param azureWebClientContext Azure Web client context
   * @param slotName slot name
   * @return startup command
   */
  String getDeploymentSlotStartupCommand(AzureWebClientContext azureWebClientContext, String slotName);

  String getWebAppStartupCommand(AzureWebClientContext context);
}
