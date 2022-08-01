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

import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.WebApp;
import com.microsoft.azure.management.appservice.implementation.SiteConfigResourceInner;
import com.microsoft.azure.management.appservice.implementation.SiteInstanceInner;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceFuture;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import rx.Completable;
import rx.Observable;

public interface AzureWebClient {
  /**
   * List web application by resource group name.
   *
   * @param context
   * @return
   */
  List<WebApp> listWebAppsByResourceGroupName(AzureClientContext context);

  /**
   * List deployment slots by web application name.
   *
   * @param context
   * @return
   */
  List<DeploymentSlot> listDeploymentSlotsByWebAppName(AzureWebClientContext context);

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
  Completable startDeploymentSlotAsync(AzureWebClientContext context, String slotName);

  void startDeploymentSlotAsync(AzureWebClientContext context, String slotName, ServiceCallback<Void> callback);

  void startWebAppAsync(AzureWebClientContext context, ServiceCallback<Void> callback);

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
  Completable startDeploymentSlotAsync(DeploymentSlot slot);

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
  Completable stopDeploymentSlotAsync(AzureWebClientContext context, String slotName);

  void stopDeploymentSlotAsync(AzureWebClientContext context, String slotName, ServiceCallback<Void> callback);

  void stopWebAppAsync(AzureWebClientContext context, ServiceCallback<Void> callback);

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
  Completable stopDeploymentSlotAsync(DeploymentSlot slot);

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
  Observable<Void> swapDeploymentSlotWithProductionAsync(AzureWebClientContext context, String sourceSlotName);

  /**
   * Swap deployment slots async.
   *
   * @param context
   * @param sourceSlotName
   * @param targetSlotName
   * @return
   */
  Observable<Void> swapDeploymentSlotsAsync(
      AzureWebClientContext context, String sourceSlotName, String targetSlotName);

  ServiceFuture<Void> swapDeploymentSlotsAsync(
      AzureWebClientContext context, String sourceSlotName, String targetSlotName, ServiceCallback<Void> callback);
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
  List<SiteInstanceInner> listInstanceIdentifiersSlot(AzureWebClientContext context, String slotName);

  List<SiteInstanceInner> listInstanceIdentifiers(AzureWebClientContext context);

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
  Completable deployZipToSlotAsync(AzureWebClientContext context, String slotName, File file);

  Completable deployZipToWebAppAsync(AzureWebClientContext context, File file);

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
  Completable deployWarToSlotAsync(AzureWebClientContext context, String slotName, File file);

  Completable deployWarToWebAppAsync(AzureWebClientContext context, File file);

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
  Observable<String> streamDeploymentLogsAsync(AzureWebClientContext context, String slotName);

  Observable<String> streamDeploymentLogsAsync(AzureWebClientContext context);

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
