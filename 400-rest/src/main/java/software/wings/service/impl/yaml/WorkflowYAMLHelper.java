/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.isEmptyCustomExpression;
import static io.harness.expression.ExpressionEvaluator.matchesVariablePattern;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.NameAccess;
import io.harness.persistence.UuidAccess;

import software.wings.beans.EntityType;
import software.wings.beans.Variable;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._955_CG_YAML)
public class WorkflowYAMLHelper {
  private static final String ASSOCIATED_TO_GOOGLE_CLOUD_BUILD_STATE_DOES_NOT_EXIST =
      "] associated to Google Cloud Build State does not exist";

  @Inject EnvironmentService environmentService;
  @Inject ArtifactStreamService artifactStreamService;
  @Inject InfrastructureMappingService infraMappingService;
  @Inject InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject SettingsService settingsService;
  @Inject UserGroupService userGroupService;

  public String getWorkflowVariableValueBean(String accountId, String envId, String appId, String entityType,
      String variableValue, boolean skipEmpty, Variable variable) {
    if (entityType == null || (skipEmpty && isEmpty(variableValue))
        || (matchesVariablePattern(variableValue) && (!isEmptyCustomExpression(variableValue))) || variable == null) {
      return variableValue;
    }

    if (isBlank(variableValue)) {
      throw new InvalidRequestException(
          format("Empty value from YAML for variable: %s & EntityType %s", variable.getName(), entityType));
    }

    if (isEmptyCustomExpression(variableValue)) {
      throw new InvalidRequestException(
          format("Custom Expression is empty for variable: %s & EntityType %s", variable.getName(), entityType));
    }

    EntityType entityTypeEnum = EntityType.valueOf(entityType);
    if (entityTypeEnum != variable.obtainEntityType()) {
      throw new InvalidRequestException(
          format("Cannot change Entity type from YAML for variable: %s original EntityType is %s", variable.getName(),
              variable.obtainEntityType()));
    }

    if (variableValue.contains(",")) {
      return handleMultipleValuesBean(accountId, envId, appId, variableValue, variable);
    }

    UuidAccess uuidAccess = getUuidAccess(accountId, envId, appId, variableValue, variable.obtainEntityType());
    if (uuidAccess != null) {
      return uuidAccess.getUuid();
    } else {
      return variableValue;
    }
  }

  private String handleMultipleValuesBean(
      String accountId, String envId, String appId, String variableValue, Variable variable) {
    if (!variable.isAllowMultipleValues()) {
      throw new InvalidRequestException(format("Variable %s doesnt allow multiple values", variable.getName()));
    }

    List<String> returnValues = new ArrayList<>();
    String[] values = variableValue.trim().split("\\s*,\\s*");
    for (String value : values) {
      UuidAccess uuidAccess = getUuidAccess(accountId, envId, appId, value, variable.obtainEntityType());
      if (uuidAccess != null) {
        returnValues.add(uuidAccess.getUuid());
      } else {
        returnValues.add(value);
      }
    }
    return String.join(",", returnValues);
  }

  public String getWorkflowVariableValueBean(
      String accountId, String envId, String appId, String entityType, String variableValue, Variable variable) {
    return getWorkflowVariableValueBean(accountId, envId, appId, entityType, variableValue, false, variable);
  }

  public String getWorkflowVariableValueYaml(
      String appId, String entryValue, EntityType entityType, boolean skipEmpty) {
    if (entityType == null || (skipEmpty && isEmpty(entryValue)) || matchesVariablePattern(entryValue)) {
      return entryValue;
    }

    if (entryValue.contains(",")) {
      return handleMultipleValues(appId, entryValue, entityType);
    }
    NameAccess x = getNameAccess(appId, entryValue, entityType);
    if (x != null) {
      return x.getName();
    } else {
      return entryValue;
    }
  }

  private String handleMultipleValues(String appId, String entryValue, EntityType entityType) {
    String[] values = entryValue.trim().split("\\s*,\\s*");
    List<String> nameValues = new ArrayList<>();
    for (String value : values) {
      NameAccess x = getNameAccess(appId, value, entityType);
      if (x != null) {
        nameValues.add(x.getName());
      } else {
        nameValues.add(value);
      }
    }
    return String.join(",", nameValues);
  }

  public String getWorkflowVariableValueYaml(String appId, String entryValue, EntityType entityType) {
    return getWorkflowVariableValueYaml(appId, entryValue, entityType, false);
  }

  @Nullable
  private NameAccess getNameAccess(String appId, String entryValue, EntityType entityType) {
    switch (entityType) {
      case ENVIRONMENT:
        return environmentService.get(appId, entryValue, false);
      case SERVICE:
        return serviceResourceService.get(appId, entryValue, false);
      case INFRASTRUCTURE_MAPPING:
        return infraMappingService.get(appId, entryValue);
      case INFRASTRUCTURE_DEFINITION:
        return infrastructureDefinitionService.get(appId, entryValue);
      case CF_AWS_CONFIG_ID:
      case HELM_GIT_CONFIG_ID:
      case SS_SSH_CONNECTION_ATTRIBUTE:
      case SS_WINRM_CONNECTION_ATTRIBUTE:
      case GCP_CONFIG:
      case GIT_CONFIG:
      case JENKINS_SERVER:
        return settingsService.get(entryValue);
      case USER_GROUP:
        return userGroupService.get(entryValue);
      case ARTIFACT_STREAM:
        return getArtifactStream(appId, entryValue);
      default:
        return null;
    }
  }

  @Nullable
  private NameAccess getArtifactStream(String appId, String entryValue) {
    ArtifactStream artifactStream = artifactStreamService.get(entryValue);
    if (artifactStream == null) {
      log.error("Artifact stream with id {} doesn't exist", entryValue);
      return null;
    }
    String serviceName = serviceResourceService.getName(appId, artifactStream.getServiceId());
    if (isEmpty(serviceName)) {
      log.error("Service {} associated with artifact stream {} doesn't exist", artifactStream.getServiceId(),
          artifactStream.getName());
      return null;
    }
    artifactStream.setName(StringUtils.join(artifactStream.getName(), " (", serviceName, ")"));
    return artifactStream;
  }

  @Nullable
  private UuidAccess getUuidAccess(
      String accountId, String envId, String appId, String variableValue, EntityType entityType) {
    UuidAccess uuidAccess;
    switch (entityType) {
      case ENVIRONMENT:
        uuidAccess = environmentService.getEnvironmentByName(appId, variableValue, false);
        notNullCheck("Environment [" + variableValue + "] does not exist", uuidAccess, USER);
        break;
      case SERVICE:
        uuidAccess = serviceResourceService.getServiceByName(appId, variableValue, false);
        notNullCheck("Service [" + variableValue + "] does not exist", uuidAccess, USER);
        break;
      case INFRASTRUCTURE_MAPPING:
        uuidAccess = infraMappingService.getInfraMappingByName(appId, envId, variableValue);
        notNullCheck(
            "Service Infrastructure [" + variableValue + "] does not exist for the environment", uuidAccess, USER);
        break;
      case INFRASTRUCTURE_DEFINITION:
        uuidAccess = infrastructureDefinitionService.getInfraDefByName(appId, envId, variableValue);
        notNullCheck(
            "Infrastructure Definition [" + variableValue + "] does not exist for the environment", uuidAccess, USER);
        break;
      case CF_AWS_CONFIG_ID:
        uuidAccess = settingsService.fetchSettingAttributeByName(accountId, variableValue, SettingVariableTypes.AWS);
        notNullCheck(
            "Aws Cloud Provider [" + variableValue + "] associated to the State does not exist", uuidAccess, USER);
        break;
      case HELM_GIT_CONFIG_ID:
        uuidAccess = settingsService.fetchSettingAttributeByName(accountId, variableValue, SettingVariableTypes.GIT);
        notNullCheck(
            "Git Connector [" + variableValue + "] associated to the Helm State does not exist", uuidAccess, USER);
        break;
      case SS_SSH_CONNECTION_ATTRIBUTE:
        uuidAccess = settingsService.fetchSettingAttributeByName(
            accountId, variableValue, SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES);
        notNullCheck(
            "Ssh connection attribute [" + variableValue + "] associated to the Shell Script State does not exist",
            uuidAccess, USER);
        break;
      case USER_GROUP:
        uuidAccess = userGroupService.fetchUserGroupByName(accountId, variableValue);
        notNullCheck(
            "userGroup [" + variableValue + "] associated to the Approval State does not exist", uuidAccess, USER);
        break;
      case SS_WINRM_CONNECTION_ATTRIBUTE:
        uuidAccess = settingsService.fetchSettingAttributeByName(
            accountId, variableValue, SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES);
        notNullCheck(
            "Winrm connection attribute [" + variableValue + "] associated to the Shell Script State does not exist",
            uuidAccess, USER);
        break;
      case GCP_CONFIG:
        uuidAccess = settingsService.fetchSettingAttributeByName(accountId, variableValue, SettingVariableTypes.GCP);
        notNullCheck("Google Cloud Provider [" + variableValue + ASSOCIATED_TO_GOOGLE_CLOUD_BUILD_STATE_DOES_NOT_EXIST,
            uuidAccess, USER);
        break;
      case GIT_CONFIG:
        uuidAccess = settingsService.fetchSettingAttributeByName(accountId, variableValue, SettingVariableTypes.GIT);
        notNullCheck("Git ConnectorDisconnectHandler [" + variableValue + ASSOCIATED_TO_GOOGLE_CLOUD_BUILD_STATE_DOES_NOT_EXIST,
            uuidAccess, USER);
        break;
      case JENKINS_SERVER:
        uuidAccess =
            settingsService.fetchSettingAttributeByName(accountId, variableValue, SettingVariableTypes.JENKINS);
        notNullCheck(
            "Jenkins server [" + variableValue + "] associated to the Jenkins State does not exist", uuidAccess, USER);
        break;
      case ARTIFACT_STREAM:
        ArtifactStream artifactStream = artifactStreamService.fetchByArtifactSourceVariableValue(appId, variableValue);
        notNullCheck(
            "Artifact Stream [" + variableValue + "] associated with the Artifact Collection State does not exist",
            artifactStream, USER);
        if (artifactStream.isArtifactStreamParameterized()) {
          throw new InvalidRequestException(
              "Parameterized Artifact Source cannot be used as a value for Artifact template variable");
        }
        uuidAccess = artifactStream;
        break;
      default:
        return null;
    }

    return uuidAccess;
  }
}
