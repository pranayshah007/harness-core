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
import static software.wings.security.PermissionAttribute.PermissionType;

import io.harness.beans.FeatureName;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.yaml.YamlType;
import software.wings.security.PermissionAttribute;
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
    try {
      authorizeUpdate(filePath, accountId);
    } catch (EntityNotFoundException e) {
      authorizeCreate(filePath, accountId);
    }
  }

  private void authorizeCreate(String filePath, String accountId) {
    authorize(filePath, Action.CREATE, accountId);
  }

  private void authorizeUpdate(String filePath, String accountId) {
    authorize(filePath, Action.UPDATE, accountId);
  }

  public void authorizeDelete(String filePath, String accountId) {
    authorize(filePath, Action.DELETE, accountId);
  }

  private void authorize(String filePath, Action action, String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.YAML_APIS_GRANULAR_PERMISSION, accountId)) {
      authHandler.authorizeAccountPermission(
          Collections.singletonList(new PermissionAttribute(PermissionType.ACCOUNT_MANAGEMENT)));
      return;
    }
    YamlType yamlType = yamlService.findYamlType(filePath);
    String appId = null;
    try {
      appId = yamlHelper.getAppId(accountId, filePath);
    } catch (GeneralException e) {
      appId = GLOBAL_APP_ID;
    } catch (Exception e) {
      log.error("Error in appId fetching hence returning", e);
    }
    try {
      switch (yamlType) {
        case APPLICATION_TEMPLATE_LIBRARY:
        case GLOBAL_TEMPLATE_LIBRARY:

          templateAuthHandler.authorizeCreate(appId);
          break;
        case SERVICE:
        case APPLICATION_MANIFEST_APP_SERVICE:
        case COMMAND:
        case CONFIG_FILE:
          String serviceId = null;
          if (!Action.CREATE.equals(action)) {
            Service service = yamlHelper.getService(appId, filePath);
            if (service == null) {
              throw new EntityNotFoundException("Service not found");
            }
            serviceId = service.getUuid();
          }
          authService.authorize(accountId, appId, serviceId, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.SERVICE, action)));
          break;
        case APPLICATION:
          authHandler.authorizeAccountPermission(
              Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_APPLICATIONS)));
          break;
        case PIPELINE:
          String pipelineId = null;
          if (!Action.CREATE.equals(action)) {
            Pipeline pipeline = yamlHelper.getPipeline(accountId, filePath);
            if (pipeline == null) {
              throw new EntityNotFoundException("Pipeline not found");
            }
            pipelineId = pipeline.getUuid();
          }
          authService.authorize(accountId, appId, pipelineId, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.PIPELINE, action)));
          break;
        case WORKFLOW:
          String workflowId = null;
          if (!Action.CREATE.equals(action)) {
            Workflow workflow = yamlHelper.getWorkflow(accountId, filePath);
            if (workflow == null) {
              throw new EntityNotFoundException("Workflow not found");
            }
            workflowId = workflow.getUuid();
          }
          authService.authorize(accountId, appId, workflowId, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.WORKFLOW, action)));
          break;
        case CLOUD_PROVIDER:
          authHandler.authorizeAccountPermission(
              Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_CLOUD_PROVIDERS)));
          break;
        case ENVIRONMENT:
        case INFRA_MAPPING:
          String environmentId = null;
          if (!Action.CREATE.equals(action)) {
            Environment environment = yamlHelper.getEnvironment(appId, filePath);
            if (environment == null) {
              throw new EntityNotFoundException("Environment not found");
            }
          }
          authService.authorize(accountId, appId, environmentId, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.ENV, action)));
          break;
        case GOVERNANCE_CONFIG:
        case GOVERNANCE_FREEZE_CONFIG:
          authHandler.authorizeAccountPermission(
              Collections.singletonList(new PermissionAttribute(PermissionType.MANAGE_DEPLOYMENT_FREEZES)));
          break;
        case PROVISIONER:
          String infrastructureProvisionerId = null;
          if (!Action.CREATE.equals(action)) {
            InfrastructureProvisioner infrastructureProvisioner =
                yamlHelper.getInfrastructureProvisioner(accountId, filePath);
            if (infrastructureProvisioner == null) {
              throw new EntityNotFoundException("InfrastructureProvisioner not found");
            }
            infrastructureProvisionerId = infrastructureProvisioner.getUuid();
          }
          authService.authorize(accountId, appId, infrastructureProvisionerId, UserThreadLocal.get(),
              Collections.singletonList(new PermissionAttribute(PermissionType.PROVISIONER, action)));
          break;
        case ACCOUNT_DEFAULTS:
          defaultsAuthHandler.authorizeUsage(appId, accountId);
          break;
        default:
          if (filePath.contains(PATH_DELIMITER + SERVICES_FOLDER + PATH_DELIMITER)) {
            Service serviceFromFolder = yamlHelper.getService(appId, filePath);
            if (serviceFromFolder == null) {
              throw new EntityNotFoundException("Service not found");
            }
            authService.authorize(accountId, appId, serviceFromFolder.getUuid(), UserThreadLocal.get(),
                Collections.singletonList(new PermissionAttribute(PermissionType.SERVICE, action)));
          }
          if (filePath.contains(PATH_DELIMITER + ENVIRONMENTS_FOLDER + PATH_DELIMITER)) {
            Environment envFromFolder = yamlHelper.getEnvironment(appId, filePath);
            if (envFromFolder == null) {
              throw new EntityNotFoundException("Environment not found");
            }
            authService.authorize(accountId, appId, envFromFolder.getUuid(), UserThreadLocal.get(),
                Collections.singletonList(new PermissionAttribute(PermissionType.ENV, action)));
          }
          break;
      }
    } catch (EntityNotFoundException e) {
      throw e;
    } catch (WingsException e) {
      log.error(
          "Encountered error in creating filepath [{}], action[{}], accountId[{}]", filePath, action, accountId, e);
      throw e;
    } catch (Exception e) {
      log.error("Encountered unknown error in creating filepath [{}], action[{}], accountId[{}]", filePath, action,
          accountId, e);
    }
  }
}
