/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector.types;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.graphql.datafetcher.connector.ConnectorsController;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ConnectorFactory {
  private ConnectorFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static Connector getConnector(QLConnectorType qlConnectorType, ConnectorsController connectorsController,
      SecretManager secretManager, SettingsService settingsService, UsageScopeController usageScopeController,
      FeatureFlagService featureFlagService) {
    switch (qlConnectorType) {
      case GIT:
        return new GitConnector(secretManager, settingsService, connectorsController, usageScopeController);
      case DOCKER:
        return new DockerConnector(secretManager, connectorsController);
      case NEXUS:
        return new NexusConnector(secretManager, connectorsController);
      case AMAZON_S3_HELM_REPO:
      case OCI_HELM_REPO:
      case GCS_HELM_REPO:
      case HTTP_HELM_REPO:
        return new HelmConnector(featureFlagService, secretManager, connectorsController, settingsService);
      default:
        throw new InvalidRequestException("Invalid connector Type");
    }
  }
}
