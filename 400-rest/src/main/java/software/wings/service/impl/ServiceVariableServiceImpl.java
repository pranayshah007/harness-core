/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.CollectionUtils.isEqualCollection;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.beans.ServiceVariableType.TEXT;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import io.harness.validation.Create;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.Environment;
import software.wings.beans.Event;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by peeyushaggarwal on 9/14/16.
 */
@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ServiceVariableServiceImpl implements ServiceVariableService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;
  @Inject private ExecutorService executorService;
  @Inject private YamlPushService yamlPushService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private AuthHandler authHandler;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private EntityVersionService entityVersionService;

  @Override
  public PageResponse<ServiceVariable> list(PageRequest<ServiceVariable> request) {
    return list(request, OBTAIN_VALUE);
  }

  @Override
  public PageResponse<ServiceVariable> list(
      PageRequest<ServiceVariable> request, EncryptedFieldMode encryptedFieldMode) {
    PageResponse<ServiceVariable> response = wingsPersistence.query(ServiceVariable.class, request);
    if (encryptedFieldMode == MASKED) {
      response.getResponse().forEach(
          serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    }
    return response;
  }

  @Override
  @ValidationGroups(Create.class)
  public ServiceVariable save(@Valid ServiceVariable serviceVariable) {
    checkValidEncryptedReference(serviceVariable);
    return save(serviceVariable, false);
  }

  @Override
  public ServiceVariable saveWithChecks(@NotEmpty String appId, ServiceVariable serviceVariable) {
    serviceVariable.setAppId(appId);
    serviceVariable.setAccountId(appService.get(appId).getAccountId());

    checkUserPermissions(serviceVariable);

    // TODO:: revisit. for environment envId can be specific
    String envId = serviceVariable.getEntityType() == SERVICE || serviceVariable.getEntityType() == ENVIRONMENT
        ? GLOBAL_ENV_ID
        : serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getTemplateId()).getEnvId();
    serviceVariable.setEnvId(envId);
    ServiceVariable savedServiceVariable = save(serviceVariable);
    if (savedServiceVariable.getType() == ENCRYPTED_TEXT) {
      serviceVariable.setValue(SECRET_MASK.toCharArray());
    }
    if (savedServiceVariable.getOverriddenServiceVariable() != null
        && savedServiceVariable.getOverriddenServiceVariable().getType() == ENCRYPTED_TEXT) {
      savedServiceVariable.getOverriddenServiceVariable().setValue(SECRET_MASK.toCharArray());
    }
    return savedServiceVariable;
  }

  private void validateServiceVariable(ServiceVariable serviceVariable) {
    if (serviceVariable.getType() == TEXT || serviceVariable.getType() == ENCRYPTED_TEXT) {
      String variableName = serviceVariable.getName();

      if (variableName.contains("-")) {
        throw new InvalidRequestException(
            format("Adding variable name %s with hyphens (dashes) is not allowed", variableName));
      }

      if (isEmpty(serviceVariable.getValue())) {
        throw new InvalidRequestException(format("Service Variable [%s] value cannot be empty", variableName));
      } else {
        checkDuplicateNameServiceVariable(serviceVariable);
      }
    }
  }

  private void checkDuplicateNameServiceVariable(ServiceVariable serviceVariable) {
    ServiceVariable savedServiceVariable = wingsPersistence.createQuery(ServiceVariable.class)
                                               .filter(ServiceVariableKeys.appId, serviceVariable.getAppId())
                                               .filter(ServiceVariableKeys.entityId, serviceVariable.getEntityId())
                                               .filter(ServiceVariableKeys.name, serviceVariable.getName())
                                               .get();

    if (savedServiceVariable != null) {
      throw new GeneralException("Duplicate name " + serviceVariable.getName(), USER);
    }
  }

  @Override
  @ValidationGroups(Create.class)
  public ServiceVariable save(@Valid ServiceVariable serviceVariable, boolean syncFromGit) {
    checkValidEncryptedReference(serviceVariable);
    if (!asList(SERVICE, EntityType.SERVICE_TEMPLATE, EntityType.ENVIRONMENT, EntityType.HOST)
             .contains(serviceVariable.getEntityType())) {
      throw new WingsException(INVALID_ARGUMENT)
          .addParam("args", "Service setting not supported for entityType " + serviceVariable.getEntityType());
    }

    validateServiceVariable(serviceVariable);

    if (serviceVariable.getAccountId() == null) {
      String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
      serviceVariable.setAccountId(accountId);
    }

    String serviceVariableId =
        duplicateCheck(() -> wingsPersistence.save(serviceVariable), "name", serviceVariable.getName());

    entityVersionService.newEntityVersion(serviceVariable.getAppId(), EntityType.CONFIG, serviceVariable.getUuid(),
        serviceVariable.getEntityId(), serviceVariable.getName(), ChangeType.CREATED, null);

    if (isEmpty(serviceVariableId)) {
      return null;
    }
    ServiceVariable newServiceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
    if (newServiceVariable == null) {
      return null;
    }

    executorService.submit(() -> addAndSaveSearchTags(serviceVariable));

    newServiceVariable.setSyncFromGit(syncFromGit);

    // Type.UPDATE is intentionally passed. Don't change this.
    yamlPushService.pushYamlChangeSet(newServiceVariable.getAccountId(), newServiceVariable, newServiceVariable,
        Event.Type.UPDATE, syncFromGit, false);

    return newServiceVariable;
  }

  @Override
  public ServiceVariable get(@NotEmpty String appId, @NotEmpty String settingId) {
    return get(appId, settingId, OBTAIN_VALUE);
  }

  @Override
  public ServiceVariable get(String appId, String settingId, EncryptedFieldMode encryptedFieldMode) {
    ServiceVariable serviceVariable = wingsPersistence.getWithAppId(ServiceVariable.class, appId, settingId);
    notNullCheck("ServiceVariable is null for id: " + settingId, serviceVariable);
    if (encryptedFieldMode == MASKED) {
      processEncryptedServiceVariable(encryptedFieldMode, serviceVariable);
    }
    return serviceVariable;
  }

  @Override
  public ServiceVariable update(@Valid ServiceVariable serviceVariable) {
    return update(serviceVariable, false);
  }

  @Override
  public ServiceVariable updateWithChecks(
      @NotEmpty String appId, @NotEmpty String serviceVariableId, ServiceVariable serviceVariable) {
    serviceVariable.setUuid(serviceVariableId);
    serviceVariable.setAppId(appId);

    checkUserPermissions(serviceVariable);

    ServiceVariable savedServiceVariable = update(serviceVariable);
    if (savedServiceVariable.getType() == ENCRYPTED_TEXT) {
      serviceVariable.setValue(SECRET_MASK.toCharArray());
    }
    if (savedServiceVariable.getOverriddenServiceVariable() != null
        && savedServiceVariable.getOverriddenServiceVariable().getType() == ENCRYPTED_TEXT) {
      savedServiceVariable.getOverriddenServiceVariable().setValue(SECRET_MASK.toCharArray());
    }
    return savedServiceVariable;
  }

  @Override
  public ServiceVariable update(@Valid ServiceVariable serviceVariable, boolean syncFromGit) {
    checkValidEncryptedReference(serviceVariable);
    ServiceVariable savedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());
    // variables with type ARTIFACT have null value
    if (isNotEmpty(serviceVariable.getValue())) {
      executorService.submit(
          () -> removeSearchTagsIfNecessary(savedServiceVariable, String.valueOf(serviceVariable.getValue())));
    }
    notNullCheck("Service variable", savedServiceVariable);
    if (serviceVariable.getName() != null) {
      if (savedServiceVariable.getName() != null && !savedServiceVariable.getName().equals(serviceVariable.getName())) {
        if (savedServiceVariable.getType() == ServiceVariableType.ARTIFACT) {
          throw new InvalidRequestException("Artifact variable name can not be changed.");
        } else {
          throw new InvalidRequestException("Service variable name can not be changed.");
        }
      }
    }

    Map<String, Object> updateMap = new HashMap<>();
    if (isNotEmpty(serviceVariable.getValue())) {
      updateMap.put(ServiceVariableKeys.value, serviceVariable.getValue());
    }
    if (serviceVariable.getType() != null) {
      updateMap.put(ServiceVariableKeys.type, serviceVariable.getType());
    }

    // TODO: ASR: optimize this to only update in case of change
    // TODO: ASR: what to do in case of YAML?
    List<String> allowedList = serviceVariable.getAllowedList();
    if (allowedList == null) {
      allowedList = new ArrayList<>();
    }
    updateMap.put(ServiceVariableKeys.allowedList, allowedList);

    if (savedServiceVariable.getAccountId() == null) {
      String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
      updateMap.put(ServiceVariableKeys.accountId, accountId);
    }

    if (isNotEmpty(updateMap)) {
      updateFields(serviceVariable, savedServiceVariable, updateMap, syncFromGit);
    }
    return serviceVariable;
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String settingId) {
    delete(appId, settingId, false);
  }

  @Override
  public void deleteWithChecks(@NotEmpty String appId, @NotEmpty String serviceVariableId) {
    ServiceVariable serviceVariable = get(appId, serviceVariableId, MASKED);
    checkUserPermissions(serviceVariable);
    delete(appId, serviceVariableId);
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String settingId, boolean syncFromGit) {
    ServiceVariable serviceVariable = get(appId, settingId);
    if (serviceVariable == null) {
      return;
    }

    executorService.submit(() -> removeSearchTagsIfNecessary(serviceVariable, null));
    Query<ServiceVariable> query = wingsPersistence.createQuery(ServiceVariable.class)
                                       .filter(ServiceVariableKeys.parentServiceVariableId, settingId)
                                       .filter(ServiceVariableKeys.appId, appId);
    List<ServiceVariable> modified = query.asList();
    UpdateOperations<ServiceVariable> updateOperations = wingsPersistence.createUpdateOperations(ServiceVariable.class)
                                                             .unset(ServiceVariableKeys.parentServiceVariableId);
    wingsPersistence.update(query, updateOperations);

    wingsPersistence.delete(wingsPersistence.createQuery(ServiceVariable.class)
                                .filter(ServiceVariableKeys.appId, appId)
                                .filter(ID_KEY, settingId));
    serviceVariable.setSyncFromGit(syncFromGit);
    // Type.UPDATE is intentionally passed. Don't change this.
    String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
    yamlPushService.pushYamlChangeSet(
        accountId, serviceVariable, serviceVariable, Event.Type.UPDATE, syncFromGit, false);
    if (isNotEmpty(modified)) {
      for (ServiceVariable serviceVariable1 : modified) {
        accountId = appService.getAccountIdByAppId(serviceVariable1.getAppId());
        yamlPushService.pushYamlChangeSet(
            accountId, serviceVariable1, serviceVariable1, Event.Type.UPDATE, syncFromGit, false);
      }
    }
  }

  @Override
  public List<ServiceVariable> getServiceVariablesForEntity(
      String appId, String entityId, EncryptedFieldMode encryptedFieldMode) {
    PageRequest<ServiceVariable> request = aPageRequest()
                                               .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
                                               .addFilter(ServiceVariableKeys.entityId, Operator.EQ, entityId)
                                               .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    return variables;
  }

  @Override
  public List<ServiceVariable> getServiceVariablesByTemplate(
      String appId, String envId, ServiceTemplate serviceTemplate, EncryptedFieldMode encryptedFieldMode) {
    PageRequest<ServiceVariable> request =
        aPageRequest()
            .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
            .addFilter(ServiceVariableKeys.envId, Operator.EQ, envId)
            .addFilter(ServiceVariableKeys.templateId, Operator.EQ, serviceTemplate.getUuid())
            .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    return variables;
  }

  @Override
  public void deleteByTemplateId(String appId, String serviceTemplateId) {
    List<ServiceVariable> serviceVariables = wingsPersistence.createQuery(ServiceVariable.class)
                                                 .filter(ServiceVariableKeys.appId, appId)
                                                 .filter(ServiceVariableKeys.templateId, serviceTemplateId)
                                                 .asList();
    deleteServiceVariables(appId, serviceVariables);
  }

  @Override
  public void pruneByService(String appId, String entityId) {
    List<ServiceVariable> serviceVariables = wingsPersistence.createQuery(ServiceVariable.class)
                                                 .filter(ServiceVariableKeys.appId, appId)
                                                 .filter(ServiceVariableKeys.entityId, entityId)
                                                 .asList();
    deleteServiceVariables(appId, serviceVariables);
  }

  private void deleteServiceVariables(String appId, List<ServiceVariable> serviceVariables) {
    for (ServiceVariable serviceVariable : serviceVariables) {
      if (wingsPersistence.delete(serviceVariable)) {
        auditServiceHelper.reportDeleteForAuditing(appId, serviceVariable);
      }
    }
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    pruneByService(appId, envId);
  }

  private void processEncryptedServiceVariable(EncryptedFieldMode encryptedFieldMode, ServiceVariable serviceVariable) {
    if (serviceVariable.getType() == ENCRYPTED_TEXT) {
      if (encryptedFieldMode == MASKED) {
        serviceVariable.setValue(SECRET_MASK.toCharArray());
      }
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, serviceVariable.getEncryptedValue());
      notNullCheck("no encrypted ref found for " + serviceVariable.getUuid(), encryptedData, USER);
      serviceVariable.setSecretTextName(encryptedData.getName());
    }
  }

  @Override
  public int updateSearchTagsForSecrets(String accountId) {
    int updateRecords = 0;
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.accountId, accountId)
                                     .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT);
    try (HIterator<EncryptedData> records = new HIterator<>(query.fetch())) {
      for (EncryptedData savedData : records) {
        List<String> appIds = savedData.getAppIds() == null ? null : new ArrayList<>(savedData.getAppIds());
        List<String> serviceIds = savedData.getServiceIds() == null ? null : new ArrayList<>(savedData.getServiceIds());
        List<String> envIds = savedData.getEnvIds() == null ? null : new ArrayList<>(savedData.getEnvIds());
        Set<String> serviceVariableIds =
            savedData.getServiceVariableIds() == null ? null : new HashSet<>(savedData.getServiceVariableIds());

        savedData.clearSearchTags();

        savedData.getParents().forEach(encryptedDataParent -> {
          if (encryptedDataParent.getType() == SettingVariableTypes.SERVICE_VARIABLE) {
            String serviceVariableId = encryptedDataParent.getId();
            ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
            if (serviceVariable == null) {
              return;
            }
            addSearchTags(serviceVariable, savedData);
          }
        });
        if (!isEqualCollection(appIds, savedData.getAppIds())
            || !isEqualCollection(serviceIds, savedData.getServiceIds())
            || !isEqualCollection(envIds, savedData.getEnvIds())
            || !isEqualCollection(serviceVariableIds, savedData.getServiceVariableIds())) {
          log.info("updating {}", savedData.getUuid());
          wingsPersistence.save(savedData);
          updateRecords++;
        }
      }
    }
    return updateRecords;
  }

  private void addAndSaveSearchTags(ServiceVariable serviceVariable) {
    if (serviceVariable.getType() != ServiceVariableType.ENCRYPTED_TEXT) {
      return;
    }

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, serviceVariable.getEncryptedValue());
    addSearchTags(serviceVariable, encryptedData);

    wingsPersistence.save(encryptedData);
  }

  private void addSearchTags(ServiceVariable serviceVariable, EncryptedData encryptedData) {
    Preconditions.checkNotNull(encryptedData, "could not find encrypted reference for " + serviceVariable);

    String appId = serviceVariable.getAppId();
    try {
      Application app = appService.get(appId);
      encryptedData.addApplication(appId, app.getName());
    } catch (Exception e) {
      log.info("application {} does not exists", appId);
    }

    String envId = serviceVariable.getEnvId();

    String serviceId;
    switch (serviceVariable.getEntityType()) {
      case SERVICE:
        serviceId = serviceVariable.getEntityId();
        encryptedData.addServiceVariable(serviceVariable.getUuid(), serviceVariable.getName());
        break;

      case SERVICE_TEMPLATE:
        ServiceTemplate serviceTemplate = wingsPersistence.get(ServiceTemplate.class, serviceVariable.getEntityId());
        serviceId = serviceTemplate.getServiceId();
        encryptedData.addServiceVariable(serviceTemplate.getUuid(), serviceTemplate.getName());
        break;

      case ENVIRONMENT:
        envId = serviceVariable.getEntityId();
        serviceId = null;
        break;

      default:
        return;
    }

    if (!isEmpty(envId) && !envId.equals(GLOBAL_ENV_ID)) {
      Environment environment = environmentService.get(appId, envId);
      if (environment != null) {
        encryptedData.addEnvironment(envId, environment.getName());
      }
    }

    if (!isEmpty(serviceId)) {
      Service service = serviceResourceService.getWithDetails(appId, serviceId);
      if (service != null) {
        encryptedData.addService(serviceId, service.getName());
      }
    }
  }

  private void removeSearchTagsIfNecessary(ServiceVariable savedServiceVariable, String newValue) {
    ServiceVariableType savedServiceVariableType = savedServiceVariable.getType();
    if (savedServiceVariableType != ENCRYPTED_TEXT) {
      return;
    }

    if (savedServiceVariable.getEncryptedValue().equals(newValue)) {
      return;
    }

    Query<EncryptedData> query =
        wingsPersistence.createQuery(EncryptedData.class).field(ID_KEY).equal(savedServiceVariable.getEncryptedValue());
    EncryptedData encryptedData = query.get();
    Preconditions.checkNotNull(encryptedData, "could not find encrypted reference for " + savedServiceVariable);

    String appId = savedServiceVariable.getAppId();
    encryptedData.removeApplication(appId, appService.get(appId).getName());
    String envId = savedServiceVariable.getEnvId();

    String serviceId;
    switch (savedServiceVariable.getEntityType()) {
      case SERVICE:
        serviceId = savedServiceVariable.getEntityId();
        encryptedData.removeServiceVariable(savedServiceVariable.getUuid(), savedServiceVariable.getName());
        break;

      case SERVICE_TEMPLATE:
        ServiceTemplate serviceTemplate =
            wingsPersistence.get(ServiceTemplate.class, savedServiceVariable.getEntityId());
        serviceId = serviceTemplate.getServiceId();
        encryptedData.removeServiceVariable(serviceTemplate.getUuid(), serviceTemplate.getName());
        break;

      case ENVIRONMENT:
        envId = savedServiceVariable.getEntityId();
        serviceId = null;
        break;

      default:
        throw new IllegalArgumentException("Invalid entity type " + savedServiceVariable.getEntityType());
    }

    if (!isEmpty(serviceId)) {
      encryptedData.removeService(serviceId, serviceResourceService.getWithDetails(appId, serviceId).getName());
    }

    if (!isEmpty(envId) && !envId.equals(GLOBAL_ENV_ID)) {
      Environment environment = environmentService.get(appId, envId);
      encryptedData.removeEnvironment(envId, environment.getName());
    }

    UpdateOperations<EncryptedData> updateOperations =
        wingsPersistence.createUpdateOperations(EncryptedData.class)
            .set(EncryptedDataKeys.appIds, encryptedData.getAppIds())
            .set(EncryptedDataKeys.serviceIds, encryptedData.getServiceIds())
            .set(EncryptedDataKeys.envIds, encryptedData.getEnvIds())
            .set(EncryptedDataKeys.serviceVariableIds, encryptedData.getEnvIds())
            .set(EncryptedDataKeys.searchTags, encryptedData.getSearchTags())
            .set(EncryptedDataKeys.keywords, encryptedData.getKeywords());

    wingsPersistence.update(query, updateOperations);
  }

  private void checkValidEncryptedReference(@Valid ServiceVariable serviceVariable) {
    if (serviceVariable.getType() == ENCRYPTED_TEXT) {
      Preconditions.checkNotNull(serviceVariable.getValue(), "value passed is null for " + serviceVariable);
      EncryptedData encryptedData =
          wingsPersistence.get(EncryptedData.class, String.valueOf(serviceVariable.getValue()));
      if (encryptedData == null) {
        throw new WingsException(
            INVALID_ARGUMENT, "No secret text with id " + new String(serviceVariable.getValue()) + " exists", USER)
            .addParam("args", "No secret text with given name exists. Please select one from the drop down.");
      }
    }
  }

  private void checkUserPermissions(ServiceVariable serviceVariable) {
    notNullCheck("Service variable null", serviceVariable, WingsException.USER);

    notNullCheck("Unknown entity type for service variable " + serviceVariable.getName(),
        serviceVariable.getEntityType(), WingsException.USER);

    List<PermissionAttribute> permissionAttributeList;
    String entityId;
    PermissionType permissionType;
    switch (serviceVariable.getEntityType()) {
      case SERVICE:
        entityId = serviceVariable.getEntityId();
        permissionType = PermissionType.SERVICE;
        break;

      case SERVICE_TEMPLATE:
        ServiceTemplate serviceTemplate =
            serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getEntityId());
        entityId = serviceTemplate.getEnvId();
        permissionType = PermissionType.ENV;
        break;

      case ENVIRONMENT:
        entityId = serviceVariable.getEntityId();
        permissionType = PermissionType.ENV;
        break;

      default:
        throw new WingsException("Unknown entity type for service variable " + serviceVariable.getEntityType());
    }

    PermissionAttribute permissionAttribute = new PermissionAttribute(permissionType, Action.UPDATE);
    permissionAttributeList = asList(permissionAttribute);
    authHandler.authorize(permissionAttributeList, asList(serviceVariable.getAppId()), entityId);
  }

  @Override
  public void pushServiceVariablesToGit(ServiceVariable serviceVariable) {
    String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
    yamlPushService.pushYamlChangeSet(accountId, serviceVariable, serviceVariable, Event.Type.UPDATE, false, false);
  }

  private ServiceVariable updateFields(ServiceVariable serviceVariable, ServiceVariable savedServiceVariable,
      Map<String, Object> updateMap, boolean syncFromGit) {
    Set<String> fieldsToRemove = new HashSet<>();
    if (serviceVariable.getType() == TEXT && savedServiceVariable.getEncryptedValue() != null) {
      fieldsToRemove.add("encryptedValue");
    }
    wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), updateMap, fieldsToRemove);
    entityVersionService.newEntityVersion(serviceVariable.getAppId(), EntityType.CONFIG, serviceVariable.getUuid(),
        serviceVariable.getEntityId(), serviceVariable.getName(), ChangeType.UPDATED, null);
    ServiceVariable updatedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());
    if (updatedServiceVariable == null) {
      return null;
    }

    yamlPushService.pushYamlChangeSet(updatedServiceVariable.getAccountId(), serviceVariable, updatedServiceVariable,
        Event.Type.UPDATE, syncFromGit, false);
    // variables with type ARTIFACT have null value
    if (isNotEmpty(serviceVariable.getValue())) {
      serviceVariable.setEncryptedValue(String.valueOf(serviceVariable.getValue()));
    }
    executorService.submit(() -> addAndSaveSearchTags(serviceVariable));
    return updatedServiceVariable;
  }
}
