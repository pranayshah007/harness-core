/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.environment;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.AllowedValueYaml;
import software.wings.beans.Application;
import software.wings.beans.Base.BaseKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.Builder;
import software.wings.beans.Environment.VariableOverrideYaml;
import software.wings.beans.Environment.Yaml;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceTemplate.ServiceTemplateKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.OverrideType;
import software.wings.beans.ServiceVariable.ServiceVariableBuilder;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.ArtifactVariableYamlHelper;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.ServiceVariableYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;

/**
 * @author rktummala on 11/07/17
 */
@Singleton
@Slf4j
public class EnvironmentYamlHandler extends BaseYamlHandler<Environment.Yaml, Environment> {
  @Inject YamlHelper yamlHelper;
  @Inject EnvironmentService environmentService;
  @Inject SecretManager secretManager;
  @Inject ServiceVariableService serviceVariableService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject ServiceTemplateService serviceTemplateService;
  @Inject FeatureFlagService featureFlagService;
  @Inject ArtifactVariableYamlHelper artifactVariableYamlHelper;
  @Inject private AppService appService;
  @Inject private ServiceVariableYamlHelper serviceVariableYamlHelper;

  @Override
  public Environment.Yaml toYaml(Environment environment, String appId) {
    List<ServiceVariable> serviceVariableList = getAllVariableOverridesForEnv(environment);
    List<VariableOverrideYaml> variableOverrideYamlList =
        convertToVariableOverrideYaml(serviceVariableList, environment.getName(), environment.getAccountId());

    Yaml yaml = Yaml.builder()
                    .description(environment.getDescription())
                    .configMapYaml(environment.getConfigMapYaml())
                    .configMapYamlByServiceTemplateName(
                        serviceTemplateIdToName(appId, environment.getConfigMapYamlByServiceTemplateId()))
                    .environmentType(environment.getEnvironmentType().name())
                    .variableOverrides(variableOverrideYamlList)
                    .harnessApiVersion(getHarnessApiVersion())
                    .build();

    updateYamlWithAdditionalInfo(environment, appId, yaml);
    return yaml;
  }

  private Map<String, String> serviceTemplateIdToName(
      String appId, Map<String, String> configMapYamlByServiceTemplateId) {
    if (isEmpty(configMapYamlByServiceTemplateId)) {
      return Collections.emptyMap();
    }
    Set<String> uuids = configMapYamlByServiceTemplateId.keySet();
    final int pageSize = PageRequest.DEFAULT_UNLIMITED;
    int offset = 0;
    PageRequest<ServiceTemplate> pageRequest = PageRequestBuilder.<ServiceTemplate>aPageRequest()
                                                   .withLimit(String.valueOf(pageSize))
                                                   .withOffset(String.valueOf(offset))
                                                   .addFilter(ServiceTemplateKeys.appId, Operator.EQ, appId)
                                                   .addFilter(BaseKeys.uuid, Operator.IN, uuids.toArray())
                                                   .build();
    List<ServiceTemplate> serviceTemplates = getRequiredServiceTemplates(pageSize, pageRequest);

    if (isEmpty(serviceTemplates) || serviceTemplates.size() != uuids.size()) {
      Set<String> retrievedIds = serviceTemplates.stream().map(ServiceTemplate::getUuid).collect(Collectors.toSet());
      Set<String> invalidIds = Sets.symmetricDifference(retrievedIds, uuids);
      throw new UnexpectedException(format("Could not find Service Templates with id %s", invalidIds));
    }

    Map<String, String> serviceTemplateIdNameMap =
        serviceTemplates.stream().collect(Collectors.toMap(ServiceTemplate::getUuid, ServiceTemplate::getName));
    return configMapYamlByServiceTemplateId.keySet().stream().collect(
        Collectors.toMap(serviceTemplateIdNameMap::get, configMapYamlByServiceTemplateId::get));
  }

  private Map<String, String> serviceTemplateNameToId(
      String appId, Map<String, String> configMapYamlByServiceTemplateName) {
    if (isEmpty(configMapYamlByServiceTemplateName)) {
      return Collections.emptyMap();
    }
    Set<String> names = configMapYamlByServiceTemplateName.keySet();
    final int pageSize = PageRequest.DEFAULT_UNLIMITED;
    int offset = 0;
    PageRequest<ServiceTemplate> pageRequest = PageRequestBuilder.<ServiceTemplate>aPageRequest()
                                                   .withLimit(String.valueOf(pageSize))
                                                   .withOffset(String.valueOf(offset))
                                                   .addFilter(ServiceTemplateKeys.appId, Operator.EQ, appId)
                                                   .addFilter(ServiceTemplateKeys.name, Operator.IN, names.toArray())
                                                   .build();
    List<ServiceTemplate> serviceTemplates = getRequiredServiceTemplates(pageSize, pageRequest);

    if (isEmpty(serviceTemplates) || serviceTemplates.size() != names.size()) {
      Set<String> retrievedNames = serviceTemplates.stream().map(ServiceTemplate::getName).collect(Collectors.toSet());
      Set<String> invalidNames = Sets.symmetricDifference(retrievedNames, names);
      throw new UnexpectedException(format("Could not find Service Templates with names %s", invalidNames));
    }

    Map<String, String> serviceTemplateNameIdMap =
        serviceTemplates.stream().collect(Collectors.toMap(ServiceTemplate::getName, ServiceTemplate::getUuid));
    return configMapYamlByServiceTemplateName.keySet().stream().collect(
        Collectors.toMap(serviceTemplateNameIdMap::get, configMapYamlByServiceTemplateName::get));
  }

  @VisibleForTesting
  @NotNull
  List<ServiceTemplate> getRequiredServiceTemplates(int pageSize, PageRequest<ServiceTemplate> pageRequest) {
    int offset = Integer.parseInt(pageRequest.getOffset());
    List<ServiceTemplate> reqdServiceTemplates = new ArrayList<>();
    List<ServiceTemplate> serviceTemplates =
        serviceTemplateService.listWithoutServiceAndInfraMappingSummary(pageRequest, false, MASKED).getResponse();
    reqdServiceTemplates.addAll(serviceTemplates);
    while (serviceTemplates.size() == pageSize) {
      offset += pageSize;
      pageRequest.setOffset(String.valueOf(offset));
      serviceTemplates = serviceTemplateService.listWithoutServiceAndInfraMappingSummary(pageRequest, false, MASKED);
      reqdServiceTemplates.addAll(serviceTemplates);
    }
    return reqdServiceTemplates;
  }

  private List<ServiceVariable> getAllVariableOverridesForEnv(Environment environment) {
    List<ServiceVariable> serviceVariableList = Lists.newArrayList();
    List<ServiceVariable> serviceVariablesForAllServices = serviceVariableService.getServiceVariablesForEntity(
        environment.getAppId(), environment.getUuid(), OBTAIN_VALUE);
    serviceVariableList.addAll(serviceVariablesForAllServices);

    if (isEmpty(environment.getServiceTemplates())) {
      return serviceVariableList;
    }
    environment.getServiceTemplates().forEach(serviceTemplate -> {
      List<ServiceVariable> serviceVariablesByTemplate = serviceVariableService.getServiceVariablesByTemplate(
          environment.getAppId(), environment.getUuid(), serviceTemplate, OBTAIN_VALUE);
      serviceVariableList.addAll(serviceVariablesByTemplate);
    });

    return serviceVariableList;
  }

  private List<VariableOverrideYaml> convertToVariableOverrideYaml(
      List<ServiceVariable> serviceVariables, String envName, String accountId) {
    if (serviceVariables == null) {
      return Lists.newArrayList();
    }

    return serviceVariables.stream()
        .map(serviceVariable -> {
          List<AllowedValueYaml> allowedValueYamlList = new ArrayList<>();
          ServiceVariableType variableType = serviceVariable.getType();
          String value = null;
          if (ServiceVariableType.ENCRYPTED_TEXT == variableType) {
            value =
                secretManager.getEncryptedYamlRef(serviceVariable.getAccountId(), serviceVariable.getEncryptedValue());
          } else if (ServiceVariableType.TEXT == variableType) {
            if (serviceVariable.getValue() != null) {
              value = String.valueOf(serviceVariable.getValue());
            }
          } else if (ServiceVariableType.ARTIFACT == variableType) {
            serviceVariableYamlHelper.convertArtifactVariableToYaml(accountId, serviceVariable, allowedValueYamlList);
          } else {
            String msg = "Invalid value type: " + variableType + ". for variable: " + serviceVariable.getName()
                + " in env: " + envName;
            throw new InvalidRequestException(msg);
          }

          String parentServiceName = getParentServiceName(serviceVariable);

          return VariableOverrideYaml.builder()
              .valueType(variableType.name())
              .value(value)
              .name(serviceVariable.getName())
              .serviceName(parentServiceName)
              .allowedValueYamls(allowedValueYamlList)
              .build();
        })
        .collect(toList());
  }

  private String getParentServiceName(ServiceVariable serviceVariable) {
    if (serviceVariable.getEntityType() == SERVICE_TEMPLATE) {
      ServiceTemplate serviceTemplate =
          serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getEntityId());
      notNullCheck("Service template not found for id: " + serviceVariable.getEntityId(), serviceTemplate, USER);
      String serviceId = serviceTemplate.getServiceId();
      Service service = serviceResourceService.getWithDetails(serviceVariable.getAppId(), serviceId);
      notNullCheck("Service not found for id: " + serviceId, service, USER);
      return service.getName();
    }

    return null;
  }

  @Override
  public Environment upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String appId =
        yamlHelper.getAppId(changeContext.getChange().getAccountId(), changeContext.getChange().getFilePath());
    notNullCheck("appId null for given yaml file:" + changeContext.getChange().getFilePath(), appId, USER);
    Yaml yaml = changeContext.getYaml();
    String environmentName = yamlHelper.getEnvironmentName(changeContext.getChange().getFilePath());
    Environment current = Builder.anEnvironment()
                              .appId(appId)
                              .name(environmentName)
                              .description(yaml.getDescription())
                              .configMapYaml(yaml.getConfigMapYaml())
                              .configMapYamlByServiceTemplateId(
                                  serviceTemplateNameToId(appId, yaml.getConfigMapYamlByServiceTemplateName()))
                              .environmentType(EnvironmentType.valueOf(yaml.getEnvironmentType()))
                              .build();

    boolean syncFromGit = changeContext.getChange().isSyncFromGit();
    current.setSyncFromGit(syncFromGit);

    Environment previous = yamlHelper.getEnvironment(appId, changeContext.getChange().getFilePath());

    if (previous != null) {
      current.setUuid(previous.getUuid());
      // Service Variables are updated first. Consider a scenario where we are adding 1000 new variables.
      // If the service is updated first, then the change set created because of this may not contain all service
      // variables. Lets assume that only 200 variables were added. Now after the change set is pushed to git, it will
      // come back to harness. There is a chance that this change set may delete the newly added service
      // variables if the remaining 800 variables are not added yet. This does not apply when new environment is created
      // as this is not allowed in harness UI. If the environment is created in git, then we wont be pushing it to git
      // again.
      List<ServiceVariable> currentVariableList = getAllVariableOverridesForEnv(previous);
      saveOrUpdateVariableOverrides(
          yaml.getVariableOverrides(), currentVariableList, previous.getAppId(), previous.getUuid());

      current = environmentService.update(current, true);
    } else {
      current = environmentService.save(current);
      saveOrUpdateVariableOverrides(yaml.getVariableOverrides(), emptyList(), current.getAppId(), current.getUuid());
    }

    changeContext.setEntity(current);
    return current;
  }

  @Override
  public Class getYamlClass() {
    return Environment.Yaml.class;
  }

  @Override
  public Environment get(String accountId, String yamlFilePath) {
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("appId null for given yaml file:" + yamlFilePath, appId, USER);
    return yamlHelper.getEnvironment(appId, yamlFilePath);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
    if (!optionalApplication.isPresent()) {
      return;
    }

    Optional<Environment> optionalEnvironment =
        yamlHelper.getEnvIfPresent(optionalApplication.get().getUuid(), yamlFilePath);
    if (!optionalEnvironment.isPresent()) {
      return;
    }

    environmentService.delete(optionalApplication.get().getUuid(), optionalEnvironment.get().getUuid(),
        changeContext.getChange().isSyncFromGit());
  }

  private List<VariableOverrideYaml> getConfigVariablesToAdd(List<VariableOverrideYaml> yamlVariableOverrideList,
      Map<ServiceVariableKey, ServiceVariable> existingVariableMap) {
    List<VariableOverrideYaml> configVarsToAdd = new ArrayList<>();

    if (isEmpty(yamlVariableOverrideList)) {
      return configVarsToAdd;
    }

    for (VariableOverrideYaml configVar : yamlVariableOverrideList) {
      ServiceVariableKey serviceVariableKey =
          ServiceVariableKey.builder().name(configVar.getName()).serviceName(configVar.getServiceName()).build();

      if (!existingVariableMap.containsKey(serviceVariableKey)) {
        configVarsToAdd.add(configVar);
      }
    }

    return configVarsToAdd;
  }

  private List<VariableOverrideYaml> getConfigVariablesToUpdate(List<VariableOverrideYaml> yamlVariableOverrideList,
      Map<ServiceVariableKey, ServiceVariable> existingVariableMap) {
    List<VariableOverrideYaml> configVarsToUpdate = new ArrayList<>();

    if (isEmpty(yamlVariableOverrideList)) {
      return configVarsToUpdate;
    }

    for (VariableOverrideYaml variableOverrideYaml : yamlVariableOverrideList) {
      ServiceVariableKey serviceVariableKey = ServiceVariableKey.builder()
                                                  .name(variableOverrideYaml.getName())
                                                  .serviceName(variableOverrideYaml.getServiceName())
                                                  .build();

      if (existingVariableMap.containsKey(serviceVariableKey)) {
        ServiceVariable serviceVariable = existingVariableMap.get(serviceVariableKey);

        switch (serviceVariable.getType()) {
          case TEXT:
            if (variableOverrideYaml.getValue() == null
                || !Arrays.equals(variableOverrideYaml.getValue().toCharArray(), serviceVariable.getValue())) {
              configVarsToUpdate.add(variableOverrideYaml);
            }
            break;

          case ARTIFACT:
          case ENCRYPTED_TEXT:
            configVarsToUpdate.add(variableOverrideYaml);
            break;

          default:
            log.warn(format("Unhandled type %s while finding config variables to update", serviceVariable.getType()));
        }
      }
    }

    return configVarsToUpdate;
  }

  private List<ServiceVariable> getConfigVariablesToDelete(List<VariableOverrideYaml> yamlVariableOverrideList,
      Map<ServiceVariableKey, ServiceVariable> existingVariableMap) {
    List<ServiceVariable> configVarsToDelete = new ArrayList<>();
    if (isEmpty(existingVariableMap)) {
      return configVarsToDelete;
    }

    Map<ServiceVariableKey, VariableOverrideYaml> yamlVariableOverrideMap = new HashMap<>();
    if (isNotEmpty(yamlVariableOverrideList)) {
      for (VariableOverrideYaml variableOverrideYaml : yamlVariableOverrideList) {
        ServiceVariableKey serviceVariableKey = ServiceVariableKey.builder()
                                                    .name(variableOverrideYaml.getName())
                                                    .serviceName(variableOverrideYaml.getServiceName())
                                                    .build();
        yamlVariableOverrideMap.put(serviceVariableKey, variableOverrideYaml);
      }
    }

    for (Map.Entry<ServiceVariableKey, ServiceVariable> entry : existingVariableMap.entrySet()) {
      if (!yamlVariableOverrideMap.containsKey(entry.getKey())) {
        configVarsToDelete.add(entry.getValue());
      }
    }

    return configVarsToDelete;
  }

  private void saveOrUpdateVariableOverrides(List<VariableOverrideYaml> latestVariableOverrideList,
      List<ServiceVariable> currentVariables, String appId, String envId) {
    Map<ServiceVariableKey, ServiceVariable> variableMap = new HashMap<>();
    for (ServiceVariable serviceVariable : currentVariables) {
      String serviceName = getParentServiceName(serviceVariable);
      variableMap.put(ServiceVariableKey.builder().name(serviceVariable.getName()).serviceName(serviceName).build(),
          serviceVariable);
    }

    List<VariableOverrideYaml> configVarsToAdd = getConfigVariablesToAdd(latestVariableOverrideList, variableMap);
    List<ServiceVariable> configVarsToDelete = getConfigVariablesToDelete(latestVariableOverrideList, variableMap);
    List<VariableOverrideYaml> configVarsToUpdate = getConfigVariablesToUpdate(latestVariableOverrideList, variableMap);

    // We are passing true for syncFromGit because we don't want to generate multiple yaml change sets if user is adding
    // many service variables through yaml. After performing all add/update/delete, we will call pushToGit which will
    // push everything to git
    String accountId = appService.get(appId).getAccountId();

    // Delete service variables
    for (ServiceVariable serviceVariable : configVarsToDelete) {
      serviceVariableService.delete(appId, serviceVariable.getUuid(), true);
    }

    // Add service variables
    for (VariableOverrideYaml configVar : configVarsToAdd) {
      serviceVariableService.save(createNewVariableOverride(appId, envId, configVar), true);
    }

    // Update service variables
    updateServiceVariables(accountId, configVarsToUpdate, variableMap);
  }

  private void updateServiceVariables(String accountId, List<VariableOverrideYaml> configVarsToUpdate,
      Map<ServiceVariableKey, ServiceVariable> variableMap) {
    for (VariableOverrideYaml configVar : configVarsToUpdate) {
      ServiceVariableKey serviceVariableKey =
          ServiceVariableKey.builder().name(configVar.getName()).serviceName(configVar.getServiceName()).build();
      ServiceVariable serviceVariable = Objects.requireNonNull(variableMap.get(serviceVariableKey));
      String value = configVar.getValue();

      switch (serviceVariable.getType()) {
        case ENCRYPTED_TEXT:
          String encryptedRecordId = yamlHelper.extractEncryptedRecordId(value, accountId);
          serviceVariable.setEncryptedValue(encryptedRecordId);
          serviceVariable.setValue(isBlank(encryptedRecordId) ? null : encryptedRecordId.toCharArray());
          break;

        case TEXT:
          serviceVariable.setValue(value != null ? value.toCharArray() : null);
          break;

        case ARTIFACT:
          if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
            List<String> allowedList = artifactVariableYamlHelper.computeAllowedList(
                accountId, configVar.getAllowedList(), configVar.getName());
            serviceVariable.setAllowedList(allowedList);
          } else {
            log.warn("Yaml doesn't support {} type service variables", configVar.getValueType());
            continue;
          }
          break;

        default:
          log.warn("Yaml doesn't support {} type service variables", serviceVariable.getType());
          continue;
      }

      serviceVariableService.update(serviceVariable, true);
    }
  }

  private ServiceVariable createNewVariableOverride(String appId, String envId, VariableOverrideYaml overrideYaml) {
    notNullCheck("Value type is not set for variable: " + overrideYaml.getName(), overrideYaml.getValueType(), USER);

    String accountId = appService.get(appId, false).getAccountId();
    ServiceVariableBuilder variableBuilder =
        ServiceVariable.builder().name(overrideYaml.getName()).accountId(accountId);
    if (overrideYaml.getServiceName() == null) {
      variableBuilder.entityType(EntityType.ENVIRONMENT)
          .entityId(envId)
          .templateId(ServiceVariable.DEFAULT_TEMPLATE_ID)
          .envId(GLOBAL_ENV_ID);
    } else {
      String parentServiceName = overrideYaml.getServiceName();
      Service service = serviceResourceService.getServiceByName(appId, parentServiceName);
      notNullCheck("No service found for given name: " + parentServiceName, service, USER);
      List<Key<ServiceTemplate>> templateRefKeysByService =
          serviceTemplateService.getTemplateRefKeysByService(appId, service.getUuid(), envId);
      if (isEmpty(templateRefKeysByService)) {
        throw new InvalidRequestException(
            "Unable to locate a service template for the given service: " + parentServiceName);
      }

      String serviceTemplateId = (String) templateRefKeysByService.get(0).getId();
      if (isEmpty(serviceTemplateId)) {
        throw new InvalidRequestException(
            "Unable to locate a service template with the given service: " + parentServiceName + " and env: " + envId);
      }

      List<ServiceVariable> serviceVariablesList =
          serviceVariableService.getServiceVariablesForEntity(appId, service.getUuid(), OBTAIN_VALUE);
      Optional<ServiceVariable> variableOptional =
          serviceVariablesList.stream()
              .filter(serviceVariable -> serviceVariable.getName().equals(overrideYaml.getName()))
              .findFirst();
      if (variableOptional.isPresent()) {
        ServiceVariable parentServiceVariable = variableOptional.get();
        variableBuilder.parentServiceVariableId(parentServiceVariable.getUuid());
      }

      variableBuilder.entityType(EntityType.SERVICE_TEMPLATE)
          .entityId(serviceTemplateId)
          .templateId(serviceTemplateId)
          .overrideType(OverrideType.ALL)
          .envId(envId);
    }

    if ("TEXT".equals(overrideYaml.getValueType())) {
      variableBuilder.type(ServiceVariableType.TEXT);
      variableBuilder.value(overrideYaml.getValue() != null ? overrideYaml.getValue().toCharArray() : null);
    } else if ("ENCRYPTED_TEXT".equals(overrideYaml.getValueType())) {
      variableBuilder.type(ServiceVariableType.ENCRYPTED_TEXT);
      String encryptedRecordId = yamlHelper.extractEncryptedRecordId(overrideYaml.getValue(), accountId);
      variableBuilder.encryptedValue(encryptedRecordId);
      variableBuilder.value(isBlank(encryptedRecordId) ? null : encryptedRecordId.toCharArray());
    } else if ("ARTIFACT".equals(overrideYaml.getValueType())) {
      if (featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
        variableBuilder.type(ServiceVariableType.ARTIFACT);
        List<String> allowedList = artifactVariableYamlHelper.computeAllowedList(
            accountId, overrideYaml.getAllowedList(), overrideYaml.getName());
        variableBuilder.allowedList(allowedList);
      } else {
        log.warn("Yaml doesn't support {} type service variables", overrideYaml.getValueType());
      }
    } else {
      log.warn("Yaml doesn't support {} type service variables", overrideYaml.getValueType());
      variableBuilder.value(overrideYaml.getValue() != null ? overrideYaml.getValue().toCharArray() : null);
    }

    ServiceVariable serviceVariable = variableBuilder.build();
    serviceVariable.setAppId(appId);

    return serviceVariable;
  }

  @AllArgsConstructor
  @lombok.Builder
  @EqualsAndHashCode
  private static class ServiceVariableKey {
    private String name;
    private String serviceName;
  }
}
