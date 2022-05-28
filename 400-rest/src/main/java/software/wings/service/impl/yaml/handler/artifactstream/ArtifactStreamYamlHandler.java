/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.service.impl.ArtifactStreamServiceImpl.ARTIFACT_STREAM_DEBUG_LOG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.Yaml;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 10/09/17
 */
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public abstract class ArtifactStreamYamlHandler<Y extends Yaml, B extends ArtifactStream>
    extends BaseYamlHandler<Y, B> {
  @Inject SettingsService settingsService;
  @Inject ArtifactStreamService artifactStreamService;
  @Inject YamlHelper yamlHelper;
  @Inject private TemplateService templateService;
  @Inject private FeatureFlagService featureFlagService;

  protected String getSettingId(String accountId, String appId, String settingName) {
    SettingAttribute settingAttribute = settingsService.getByName(accountId, appId, settingName);
    notNullCheck("Invalid SettingAttribute:" + settingName, settingAttribute, USER);
    settingsService.checkRbacOnSettingAttribute(appId, settingAttribute);
    return settingAttribute.getUuid();
  }

  protected String getSettingName(String settingId) {
    SettingAttribute settingAttribute = settingsService.get(settingId);
    notNullCheck("SettingAttribute can't be found for Id:" + settingId, settingAttribute, USER);
    return settingAttribute.getName();
  }

  protected B getArtifactStream(String accountId, String yamlFilePath) {
    notNullCheck("Yaml file path is null", yamlFilePath, USER);
    return (B) yamlHelper.getArtifactStream(accountId, yamlFilePath);
  }

  @Override
  public B get(String accountId, String yamlFilePath) {
    return getArtifactStream(accountId, yamlFilePath);
  }

  @Override
  public void delete(ChangeContext<Y> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
      if (!optionalApplication.isPresent()) {
        return;
      }

      Application application = optionalApplication.get();
      Optional<Service> serviceOptional = yamlHelper.getServiceIfPresent(application.getUuid(), yamlFilePath);
      if (!serviceOptional.isPresent()) {
        return;
      }

      ArtifactStream artifactStream =
          yamlHelper.getArtifactStream(application.getUuid(), serviceOptional.get().getUuid(), yamlFilePath);
      if (artifactStream != null) {
        log.info(ARTIFACT_STREAM_DEBUG_LOG + "Deleting Artifact Stream {} from yaml", artifactStream.getName());
        artifactStreamService.deleteWithBinding(
            application.getUuid(), artifactStream.getUuid(), false, changeContext.getChange().isSyncFromGit());
      }
    } else {
      if (changeContext.getYamlType() == YamlType.ARTIFACT_STREAM) {
        Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
        if (!optionalApplication.isPresent()) {
          return;
        }

        Application application = optionalApplication.get();
        Optional<Service> serviceOptional = yamlHelper.getServiceIfPresent(application.getUuid(), yamlFilePath);
        if (!serviceOptional.isPresent()) {
          return;
        }

        ArtifactStream artifactStream =
            yamlHelper.getArtifactStream(application.getUuid(), serviceOptional.get().getUuid(), yamlFilePath);
        if (artifactStream != null) {
          artifactStreamService.deleteWithBinding(
              application.getUuid(), artifactStream.getUuid(), false, changeContext.getChange().isSyncFromGit());
        }
      } else {
        ArtifactStream artifactStream = yamlHelper.getArtifactStream(accountId, yamlFilePath);
        if (artifactStream != null) {
          artifactStreamService.deleteWithBinding(
              GLOBAL_APP_ID, artifactStream.getUuid(), false, changeContext.getChange().isSyncFromGit());
        }
      }
    }
  }

  protected void toYaml(Y yaml, B bean) {
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, bean.getAccountId())) {
      yaml.setServerName(getSettingName(bean.getSettingId()));
    } else {
      if (!CUSTOM.name().equals(yaml.getType())) {
        yaml.setServerName(getSettingName(bean.getSettingId()));
      }
    }
    yaml.setHarnessApiVersion(getHarnessApiVersion());
    String templateUuid = bean.getTemplateUuid();
    String templateUri = templateService.makeNamespacedTemplareUri(templateUuid, bean.getTemplateVersion());

    yaml.setTemplateUri(templateUri);
    yaml.setTemplateVariables(TemplateHelper.convertToTemplateVariables(bean.getTemplateVariables()));
    yaml.setCollectionEnabled(bean.getCollectionEnabled());
  }

  @Override
  public B upsertFromYaml(ChangeContext<Y> changeContext, List<ChangeContext> changeSetContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    B previous = get(changeContext.getChange().getAccountId(), yamlFilePath);
    if (previous != null) {
      toBean(previous, changeContext, previous.fetchAppId());
      previous.setSyncFromGit(changeContext.getChange().isSyncFromGit());
      return (B) artifactStreamService.update(previous, !previous.isSyncFromGit());
    } else {
      if (!featureFlagService.isEnabled(
              FeatureName.ARTIFACT_STREAM_REFACTOR, changeContext.getChange().getAccountId())) {
        String appId = yamlHelper.getAppId(changeContext.getChange().getAccountId(), yamlFilePath);
        String serviceId = yamlHelper.getServiceId(appId, yamlFilePath);

        B artifactStream = getNewArtifactStreamObject();
        artifactStream.setServiceId(serviceId);
        artifactStream.setAppId(appId);
        toBean(artifactStream, changeContext, appId);
        artifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());
        log.info(ARTIFACT_STREAM_DEBUG_LOG + "Creating Artifact Stream {} from yaml", artifactStream.getName());
        return (B) artifactStreamService.createWithBinding(appId, artifactStream, !artifactStream.isSyncFromGit());
      } else {
        if (changeContext.getYamlType() == YamlType.ARTIFACT_STREAM) {
          String appId = yamlHelper.getAppId(changeContext.getChange().getAccountId(), yamlFilePath);
          String serviceId = yamlHelper.getServiceId(appId, yamlFilePath);

          B artifactStream = getNewArtifactStreamObject();
          artifactStream.setServiceId(serviceId);
          artifactStream.setAppId(appId);
          toBean(artifactStream, changeContext, appId);
          artifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());
          return (B) artifactStreamService.createWithBinding(appId, artifactStream, !artifactStream.isSyncFromGit());
        } else {
          B artifactStream = getNewArtifactStreamObject();
          artifactStream.setAppId(GLOBAL_APP_ID);
          toBean(artifactStream, changeContext, GLOBAL_APP_ID);
          artifactStream.setSyncFromGit(changeContext.getChange().isSyncFromGit());
          return (B) artifactStreamService.createWithBinding(
              GLOBAL_APP_ID, artifactStream, !artifactStream.isSyncFromGit());
        }
      }
    }
  }

  protected void toBean(B bean, ChangeContext<Y> changeContext, String appId) {
    Y yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
    bean.setName(name);
    bean.setAutoPopulate(false);
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      bean.setSettingId(getSettingId(changeContext.getChange().getAccountId(), appId, yaml.getServerName()));
    } else {
      if (!CUSTOM.name().equals(yaml.getType())) {
        bean.setSettingId(getSettingId(changeContext.getChange().getAccountId(), appId, yaml.getServerName()));
      }
    }

    String templateUri = yaml.getTemplateUri();
    String templateUuid;
    if (isNotEmpty(templateUri)) {
      if (isNotEmpty(appId)) {
        templateUuid = templateService.fetchTemplateIdFromUri(accountId, appId, templateUri);
      } else {
        templateUuid = templateService.fetchTemplateIdFromUri(accountId, templateUri);
      }
      bean.setTemplateUuid(templateUuid);
      bean.setTemplateVersion(TemplateHelper.obtainTemplateVersion(templateUri));
    }
    bean.setTemplateVariables(TemplateHelper.convertToEntityVariables(yaml.getTemplateVariables()));
    bean.setCollectionEnabled(yaml.getCollectionEnabled());
  }

  protected abstract B getNewArtifactStreamObject();
}
