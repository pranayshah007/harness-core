/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationEnvironments;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.domains.Domain;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.routes.Route;

@OwnedBy(HarnessTeam.CDP)
public interface CfSdkClient {
  /**
   * Get organizations.
   *
   * @param pcfRequestConfig request config
   * @return List of OrganizationSummary
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<OrganizationSummary> getOrganizations(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get space for organization.
   *
   * @param pcfRequestConfig request config
   * @return list of organization spaces
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<String> getSpacesForOrganization(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get applications.
   *
   * @param pcfRequestConfig request config
   * @return List of ApplicationSummary
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<ApplicationSummary> getApplications(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get application by name.
   *
   * @param pcfRequestConfig request config
   * @return application details
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  ApplicationDetail getApplicationByName(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Start applications.
   *
   * @param pcfRequestConfig request config
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void startApplication(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Scale application.
   *
   * @param pcfRequestConfig request config
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void scaleApplications(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Stop application.
   *
   * @param pcfRequestConfig request config
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void stopApplication(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Rename application.
   *
   * @param cfRenameRequest rename request
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void renameApplication(CfRenameRequest cfRenameRequest) throws PivotalClientApiException, InterruptedException;

  /**
   * Delete application.
   *
   * @param pcfRequestConfig request config
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void deleteApplication(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Push application.
   *
   * @param pcfRequestConfig request config
   * @param path manifest path
   * @param logCallback log callback
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void pushAppBySdk(CfRequestConfig pcfRequestConfig, Path path, LogCallback logCallback)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Create route.
   *
   * @param pcfRequestConfig request config
   * @param host host
   * @param domain domain
   * @param path path
   * @param tcpRoute tcp route
   * @param useRandomPort whether to use random port
   * @param port port
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void createRouteMap(CfRequestConfig pcfRequestConfig, String host, String domain, String path, boolean tcpRoute,
      boolean useRandomPort, Integer port) throws PivotalClientApiException, InterruptedException;

  /**
   * Unmap route.
   *
   * @param pcfRequestConfig request config
   * @param route application route
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void unmapRouteMapForApp(CfRequestConfig pcfRequestConfig, Route route)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Map routes.
   *
   * @param pcfRequestConfig request config
   * @param routes application routes
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void mapRoutesForApplication(CfRequestConfig pcfRequestConfig, List<String> routes)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Map route.
   *
   * @param pcfRequestConfig request config
   * @param route application route
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void mapRouteMapForApp(CfRequestConfig pcfRequestConfig, Route route)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get route.
   *
   * @param pcfRequestConfig request config
   * @param route route
   * @return Optional of Route
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  Optional<Route> getRouteMap(CfRequestConfig pcfRequestConfig, String route)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Unmap route.
   *
   * @param pcfRequestConfig request config
   * @param routes application routes
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void unmapRoutesForApplication(CfRequestConfig pcfRequestConfig, List<String> routes)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get route.
   *
   * @param paths paths
   * @param pcfRequestConfig request config
   * @return list of routs
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<Route> getRouteMapsByNames(List<String> paths, CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get routes for space.
   *
   * @param pcfRequestConfig request config
   * @return list of space routes
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<String> getRoutesForSpace(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   *
   *
   * @param pcfRequestConfig request config
   * @param logsAfterTsNs after time in ns
   * @return logging messages
   * @throws PivotalClientApiException
   */
  List<LogMessage> getRecentLogs(CfRequestConfig pcfRequestConfig, long logsAfterTsNs) throws PivotalClientApiException;

  /**
   * Get application environments by name.
   *
   * @param pcfRequestConfig request config
   * @return application environments
   * @throws PivotalClientApiException
   */
  ApplicationEnvironments getApplicationEnvironmentsByName(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException;

  /**
   * Get tasks.
   *
   * @param pcfRequestConfig request config
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void getTasks(CfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Get all space domains.
   *
   * @param pcfRequestConfig request config
   * @return List of Domains
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<Domain> getAllDomainsForSpace(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;
}
