/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.yaml;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.YamlConstants.ENVIRONMENTS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SERVICES_FOLDER;
import static software.wings.security.PermissionAttribute.Action;

import io.harness.beans.FeatureName;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.beans.yaml.YamlType;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.impl.security.auth.DefaultsAuthHandler;
import software.wings.service.impl.security.auth.TemplateAuthHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.yaml.sync.YamlService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class YamlAuthHandler {
  @Inject TemplateAuthHandler templateAuthHandler;
  @Inject DefaultsAuthHandler defaultsAuthHandler;
  @Inject AuthHandler authHandler;
  @Inject AuthService authService;
  @Inject YamlService yamlService;
  @Inject YamlHelper yamlHelper;
  @Inject FeatureFlagService featureFlagService;

  public void authorizeUpsert(String filePath, String accountId) {
    authorize(filePath, Action.CREATE, accountId);
  }
  public void authorizeDelete(String filePath, String accountId) {
    authorize(filePath, Action.DELETE, accountId);
  }

  private void authorize(String filePath, Action action, String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.YAML_APIS_GRANULAR_PERMISSION, accountId)) {
      authHandler.authorizeAccountPermission(
          Collections.singletonList(new PermissionAttribute(PermissionType.ACCOUNT_MANAGEMENT)));
    }

    YamlType yamlType = yamlService.findYamlType(filePath);
    String appId;
    try {
      appId = yamlHelper.getAppId(accountId, filePath);
    } catch (GeneralException e) {
      appId = GLOBAL_APP_ID;
    } catch (Exception e) {
      log.error("Error in appId fetching hence returning", e);
    }
    switch (yamlType) {
      case APPLICATION_TEMPLATE_LIBRARY:
      case GLOBAL_TEMPLATE_LIBRARY:
        templateAuthHandler.authorizeCreate(appId);
        break;
      case SERVICE:
      case APPLICATION_MANIFEST_APP_SERVICE:
      case COMMAND:
      case CONFIG_FILE:
        String serviceId = yamlHelper.getServiceId(appId, filePath);
        authService.authorize(accountId, appId, serviceId, UserThreadLocal.get(),
            Collections.singletonList(new PermissionAttribute(PermissionType.SERVICE, action)));
        break;
      case APPLICATION:
        authHandler.authorizeAccountPermission(
            Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_APPLICATIONS)));
        break;
      case PIPELINE:
        Pipeline pipeline = yamlHelper.getPipeline(accountId, filePath);
        authService.authorize(accountId, appId, pipeline.getUuid(), UserThreadLocal.get(),
            Collections.singletonList(new PermissionAttribute(PermissionType.PIPELINE, action)));
        break;
      case WORKFLOW:
        Workflow workflow = yamlHelper.getWorkflow(accountId, filePath);
        authService.authorize(accountId, appId, workflow.getUuid(), UserThreadLocal.get(),
            Collections.singletonList(new PermissionAttribute(PermissionType.WORKFLOW, action)));
        break;
      case CLOUD_PROVIDER:
        authHandler.authorizeAccountPermission(
            Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_CLOUD_PROVIDERS)));
        break;
      case ENVIRONMENT:
      case INFRA_MAPPING:
        String environmentId = yamlHelper.getEnvironmentId(appId, filePath);
        authService.authorize(accountId, appId, environmentId, UserThreadLocal.get(),
            Collections.singletonList(new PermissionAttribute(PermissionType.ENV, action)));
        break;
      case GOVERNANCE_CONFIG:
      case GOVERNANCE_FREEZE_CONFIG:
        authHandler.authorizeAccountPermission(
            Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_DEPLOYMENT_FREEZES)));
        break;
      case PROVISIONER:
        InfrastructureProvisioner infrastructureProvisioner =
            yamlHelper.getInfrastructureProvisioner(accountId, filePath);
        authService.authorize(accountId, appId, infrastructureProvisioner.getUuid(), UserThreadLocal.get(),
            Collections.singletonList(new PermissionAttribute(PermissionType.PROVISIONER, action)));
        break;
      case ACCOUNT_DEFAULTS:
        defaultsAuthHandler.authorizeUsage(appId, accountId);
        break;
      default:
        if (filePath.contains(PATH_DELIMITER + SERVICES_FOLDER + PATH_DELIMITER)) {
          String serviceIdFromFolder = yamlHelper.getServiceId(appId, filePath);
          authService.authorize(accountId, appId, serviceIdFromFolder, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.SERVICE, action)));
        }
        if (filePath.contains(PATH_DELIMITER + ENVIRONMENTS_FOLDER + PATH_DELIMITER)) {
          String envIdFromFolder = yamlHelper.getEnvironmentId(appId, filePath);
          authService.authorize(accountId, appId, envIdFromFolder, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.ENV, action)));
        }
        break;
    }
  }
}
