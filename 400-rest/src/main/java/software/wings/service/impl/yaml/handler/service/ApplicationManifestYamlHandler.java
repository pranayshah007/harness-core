/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.service;

import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.manifest.CustomSourceConfig;

import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.Yaml;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.helm.HelmChartConfig;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.HelmChartConfigHelperService;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.yaml.YamlResourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ApplicationManifestYamlHandler extends BaseYamlHandler<Yaml, ApplicationManifest> {
  @Inject YamlHelper yamlHelper;
  @Inject ApplicationManifestService applicationManifestService;
  @Inject GitFileConfigHelperService gitFileConfigHelperService;
  @Inject YamlResourceService yamlResourceService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject private HelmChartConfigHelperService helmChartConfigHelperService;
  @Inject FeatureFlagService featureFlagService;

  @Override
  public Yaml toYaml(ApplicationManifest applicationManifest, String appId) {
    return Yaml.builder()
        .type(yamlResourceService.getYamlTypeFromAppManifest(applicationManifest).name())
        .harnessApiVersion(getHarnessApiVersion())
        .storeType(applicationManifest.getStoreType().name())
        .gitFileConfig(getGitFileConfigForToYaml(applicationManifest))
        .helmChartConfig(getHelmChartConfigForToYaml(applicationManifest))
        .kustomizeConfig(applicationManifest.getKustomizeConfig())
        .customSourceConfig(applicationManifest.getCustomSourceConfig())
        .skipVersioningForAllK8sObjects(applicationManifest.getSkipVersioningForAllK8sObjects())
        .helmCommandFlag(applicationManifest.getHelmCommandFlag())
        .helmValuesYamlFilePaths(applicationManifest.getHelmValuesYamlFilePaths())
        .enableCollection(applicationManifest.getEnableCollection())
        .build();
  }

  @Override
  public ApplicationManifest upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("appId null for given yaml file:" + yamlFilePath, appId, USER);

    ApplicationManifest previous = get(accountId, yamlFilePath);
    ApplicationManifest applicationManifest = toBean(changeContext);
    applicationManifest.setSyncFromGit(changeContext.getChange().isSyncFromGit());

    if (previous != null) {
      applicationManifest.setUuid(previous.getUuid());
      return applicationManifestService.update(applicationManifest);
    } else {
      serviceResourceService.setK8v2ServiceFromAppManifest(
          applicationManifest, applicationManifestService.getAppManifestType(applicationManifest));
      serviceResourceService.setPcfV2ServiceFromAppManifestIfRequired(
          applicationManifest, applicationManifestService.getAppManifestType(applicationManifest));
      return applicationManifestService.create(applicationManifest);
    }
  }

  private ApplicationManifest toBean(ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());

    String filePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, filePath);
    notNullCheck("Could not lookup app for the yaml file: " + filePath, appId, USER);

    String envId = null;
    String serviceId = getServiceIdFromYamlPath(appId, filePath);
    if (serviceId == null) {
      envId = getEnvIdFromYamlPath(appId, filePath);
      Service service = yamlHelper.getServiceOverrideFromAppManifestPath(appId, filePath);
      serviceId = (service == null) ? null : service.getUuid();
    }

    if (yaml.getStoreType() == null) {
      throw new InvalidRequestException("StoreType field should not be null for helm chart");
    }
    StoreType storeType = Enum.valueOf(StoreType.class, yaml.getStoreType());
    AppManifestKind kind = yamlHelper.getAppManifestKindFromPath(filePath);
    GitFileConfig gitFileConfig = getGitFileConfigFromYaml(accountId, appId, yaml, storeType);
    HelmChartConfig helmChartConfig = getHelmChartConfigFromYaml(accountId, appId, yaml, storeType);
    KustomizeConfig kustomizeConfig = getKustomizeConfigFromYaml(yaml, storeType);
    CustomSourceConfig customSourceConfig = getCustomSourceConfigFromYaml(accountId, yaml, storeType);

    boolean isNotK8sServiceManifest =
        serviceId == null || envId != null || !serviceResourceService.isK8sV2Service(appId, serviceId);
    if (isNotK8sServiceManifest && yaml.getSkipVersioningForAllK8sObjects() != null) {
      throw new InvalidRequestException("SkipVersioning is only allowed for k8s services at the service level", USER);
    }

    ApplicationManifest manifest = ApplicationManifest.builder()
                                       .name(name)
                                       .serviceId(serviceId)
                                       .envId(envId)
                                       .storeType(storeType)
                                       .gitFileConfig(gitFileConfig)
                                       .helmChartConfig(helmChartConfig)
                                       .kind(kind)
                                       .kustomizeConfig(kustomizeConfig)
                                       .customSourceConfig(customSourceConfig)
                                       .skipVersioningForAllK8sObjects(yaml.getSkipVersioningForAllK8sObjects())
                                       .helmCommandFlag(yaml.getHelmCommandFlag())
                                       .helmValuesYamlFilePaths(yaml.getHelmValuesYamlFilePaths())
                                       .enableCollection(yaml.getEnableCollection())
                                       .build();

    manifest.setAppId(appId);
    return manifest;
  }

  private KustomizeConfig getKustomizeConfigFromYaml(Yaml yaml, StoreType storeType) {
    KustomizeConfig kustomizeConfig = yaml.getKustomizeConfig();
    if (kustomizeConfig != null && storeType != StoreType.KustomizeSourceRepo) {
      throw new InvalidRequestException(
          "KustomizeConfig should only be used with KustomizeSourceRepo store type", USER);
    }
    return kustomizeConfig;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  public ApplicationManifest get(String accountId, String yamlFilePath) {
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Could not find Application  for the yaml file: " + yamlFilePath, appId, USER);
    return yamlHelper.getApplicationManifest(appId, yamlFilePath);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    Change change = changeContext.getChange();

    ApplicationManifest applicationManifest = get(change.getAccountId(), change.getFilePath());
    if (applicationManifest == null) {
      return;
    }

    // Dont delete the appManifest if coming from git for service.
    if (isBlank(applicationManifest.getEnvId()) && applicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
        && !Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
      log.info("Deleting the application manifest for service from git is not allowed");
      return;
    }

    applicationManifest.setSyncFromGit(changeContext.getChange().isSyncFromGit());
    applicationManifestService.deleteAppManifest(applicationManifest);
  }

  private GitFileConfig getGitFileConfigForToYaml(ApplicationManifest applicationManifest) {
    if (StoreType.Local == applicationManifest.getStoreType()) {
      return null;
    }

    return gitFileConfigHelperService.getGitFileConfigForToYaml(applicationManifest.getGitFileConfig());
  }

  private GitFileConfig getGitFileConfigFromYaml(String accountId, String appId, Yaml yaml, StoreType storeType) {
    GitFileConfig gitFileConfig = yaml.getGitFileConfig();

    if (gitFileConfig == null) {
      return null;
    }
    if (StoreType.Local == storeType) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "Git file config should be null for store type local");
    }

    return gitFileConfigHelperService.getGitFileConfigFromYaml(accountId, appId, gitFileConfig);
  }

  private HelmChartConfig getHelmChartConfigForToYaml(ApplicationManifest applicationManifest) {
    if (StoreType.Local == applicationManifest.getStoreType()) {
      return null;
    }

    return helmChartConfigHelperService.getHelmChartConfigForToYaml(applicationManifest.getHelmChartConfig());
  }

  private HelmChartConfig getHelmChartConfigFromYaml(String accountId, String appId, Yaml yaml, StoreType storeType) {
    HelmChartConfig helmChartConfig = yaml.getHelmChartConfig();
    if (helmChartConfig == null) {
      return null;
    }

    if (StoreType.Local == storeType) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT)
          .addParam("args", "gitFileConfig cannot be used for Local storeType.");
    }

    return helmChartConfigHelperService.getHelmChartConfigFromYaml(accountId, appId, helmChartConfig);
  }

  private String getServiceIdFromYamlPath(String appId, String filePath) {
    try {
      return yamlHelper.getServiceId(appId, filePath);
    } catch (WingsException ex) {
      return null;
    }
  }

  private String getEnvIdFromYamlPath(String appId, String filePath) {
    try {
      return yamlHelper.getEnvironmentId(appId, filePath);
    } catch (WingsException ex) {
      return null;
    }
  }

  private CustomSourceConfig getCustomSourceConfigFromYaml(String accountId, Yaml yaml, StoreType storeType) {
    if (isCustomSource(storeType) && !featureFlagService.isEnabled(FeatureName.CUSTOM_MANIFEST, accountId)) {
      throw new InvalidRequestException("Custom Manifest feature is not enabled. Please contact Harness support");
    }

    CustomSourceConfig customSourceConfig = yaml.getCustomSourceConfig();
    if (customSourceConfig != null && !isCustomSource(storeType)) {
      throw new InvalidArgumentsException("CustomSourceConfig should only be used with Custom store type", USER);
    }

    return customSourceConfig;
  }

  private static boolean isCustomSource(StoreType storeType) {
    return StoreType.CUSTOM == storeType || StoreType.CUSTOM_OPENSHIFT_TEMPLATE == storeType;
  }
}
