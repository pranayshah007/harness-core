/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class NgManifestFactory {
  @Inject K8sManifestRemoteStoreService k8sManifestRemoteStoreService;
  @Inject TanzuManifestRemoteStoreService tanzuManifestRemoteStoreService;
  @Inject K8sManifestHelmSourceRepoStoreService k8sManifestHelmSourceRepoStoreService;
  @Inject K8sManifestHelmChartRepoStoreService k8sManifestHelmChartRepoStoreService;
  @Inject ValuesManifestRemoteStoreService valuesManifestRemoteStoreService;
  @Inject ValuesManifestLocalStoreService valuesManifestLocalStoreService;
  @Inject OpenshiftParamRemoteStoreService openshiftParamRemoteStoreService;
  @Inject OpenshiftParamLocalStoreService openshiftParamLocalStoreService;
  @Inject K8sManifestLocalStoreService k8sManifestLocalStoreService;
  @Inject TanzuManifestLocalStoreService tanzuManifestLocalStoreService;
  @Inject TanzuManifestCustomStoreService tanzuManifestCustomStoreService;
  @Inject KustomizeSourceRepoStoreService kustomizeSourceRepoStoreService;
  @Inject OpenshiftSourceRepoStoreService openshiftSourceRepoStoreService;
  @Inject HelmChartOverrideRepoStoreService helmChartOverrideRepoStoreService;
  @Inject AzureAppServiceLocalStoreService azureAppServiceLocalStoreService;
  @Inject AzureAppServiceRemoteStoreService azureAppServiceRemoteStoreService;

  private static String ERROR_STRING = "%s storetype is currently not supported for %s appManifestKind";

  public NgManifestService getNgManifestService(ApplicationManifest applicationManifest, Service service) {
    if (applicationManifest.getKind() == null) {
      throw new InvalidRequestException("Empty appManifest kind is not supported for migration");
    }
    AppManifestKind appManifestKind = applicationManifest.getKind();
    StoreType storeType = applicationManifest.getStoreType();

    switch (appManifestKind) {
      case K8S_MANIFEST:
        switch (storeType) {
          case Local:
            if (null != service && service.getDeploymentType().equals(DeploymentType.PCF)) {
              return tanzuManifestLocalStoreService;
            } else {
              return k8sManifestLocalStoreService;
            }
          case Remote:
            if (null != service && service.getDeploymentType().equals(DeploymentType.PCF)) {
              return tanzuManifestRemoteStoreService;
            } else {
              return k8sManifestRemoteStoreService;
            }
          case HelmSourceRepo:
            return k8sManifestHelmSourceRepoStoreService;
          case HelmChartRepo:
            return k8sManifestHelmChartRepoStoreService;
          case KustomizeSourceRepo:
            return kustomizeSourceRepoStoreService;
          case OC_TEMPLATES:
            return openshiftSourceRepoStoreService;
          case CUSTOM:
            return tanzuManifestCustomStoreService;
          default:
            throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
        }
      case VALUES:
        switch (storeType) {
          case Remote:
            return valuesManifestRemoteStoreService;
          case Local:
            return valuesManifestLocalStoreService;
          default:
            throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
        }
      case OC_PARAMS:
        switch (storeType) {
          case Remote:
            return openshiftParamRemoteStoreService;
          case Local:
            return openshiftParamLocalStoreService;
          default:
            throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
        }
      case HELM_CHART_OVERRIDE:
        if (storeType == StoreType.HelmChartRepo) {
          return helmChartOverrideRepoStoreService;
        }
        throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
      case AZURE_APP_SERVICE_MANIFEST:
        switch (storeType) {
          case Remote:
            return azureAppServiceRemoteStoreService;
          case Local:
            return azureAppServiceLocalStoreService;
          default:
            throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
        }
      case PCF_OVERRIDE:
        switch (storeType) {
          case Local:
            return tanzuManifestLocalStoreService;
          case Remote:
            return tanzuManifestRemoteStoreService;
          case CUSTOM:
            // We have added the CUSTOM override but it is not yet supported in NG. Just for future ref.
            return tanzuManifestCustomStoreService;
          default:
            throw new InvalidRequestException(String.format(ERROR_STRING, storeType, appManifestKind));
        }
      default:
        throw new InvalidRequestException(
            String.format("%s appManifestKind is currently not supported", appManifestKind));
    }
  }

  public static NgEntityDetail getGitConnector(
      Map<CgEntityId, NGYamlFile> migratedEntities, ApplicationManifest applicationManifest) {
    GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
    CgEntityId connectorId =
        CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build();
    if (!migratedEntities.containsKey(connectorId)) {
      log.error(
          String.format("We could not migrate the following manifest %s as we could not find the git connector %s",
              applicationManifest.getUuid(), gitFileConfig.getConnectorId()));
      return null;
    }
    return migratedEntities.get(connectorId).getNgEntityDetail();
  }
}
