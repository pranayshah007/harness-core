/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceInstance.ServiceInstanceKeys;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.infrastructure.Host;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceInstanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by anubhaw on 5/26/16.
 */
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@ValidateOnExecution
@Singleton
public class ServiceInstanceServiceImpl implements ServiceInstanceService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AppService appService;

  @Override
  public PageResponse<ServiceInstance> list(PageRequest<ServiceInstance> pageRequest) {
    return wingsPersistence.query(ServiceInstance.class, pageRequest);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceInstanceService#save(software.wings.beans.ServiceInstance)
   */
  @Override
  public ServiceInstance save(ServiceInstance serviceInstance) {
    if (serviceInstance.getAccountId() == null) {
      String accountId = appService.getAccountIdByAppId(serviceInstance.getAppId());
      serviceInstance.setAccountId(accountId);
    }
    return wingsPersistence.saveAndGet(ServiceInstance.class, serviceInstance);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceInstanceService#get(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public ServiceInstance get(String appId, String envId, String instanceId) {
    return wingsPersistence.createQuery(ServiceInstance.class)
        .filter("appId", appId)
        .filter(ServiceInstanceKeys.envId, envId)
        .filter(ID_KEY, instanceId)
        .get();
  }

  /* (non-Javadoc)
   * @see
   * software.wings.service.intfc.ServiceInstanceService#updateInstanceMappings(software.wings.beans.ServiceTemplate,
   * java.util.List, java.util.List)
   */
  @Override
  public List<ServiceInstance> updateInstanceMappings(
      ServiceTemplate template, InfrastructureMapping infraMapping, List<Host> hosts) {
    return hosts.stream()
        .map(host -> {
          ServiceInstance serviceInstance = wingsPersistence.createQuery(ServiceInstance.class)
                                                .filter("infraMappingId", infraMapping.getUuid())
                                                .filter("hostId", host.getUuid())
                                                .filter(ServiceInstanceKeys.hostName, host.getHostName())
                                                .filter(ServiceInstanceKeys.publicDns, host.getPublicDns())
                                                .get();
          return serviceInstance != null ? serviceInstance
                                         : save(aServiceInstance()
                                                    .withAppId(template.getAppId())
                                                    .withEnvId(template.getEnvId())
                                                    .withServiceTemplate(template)
                                                    .withHost(host)
                                                    .withInfraMappingId(infraMapping.getUuid())
                                                    .withInfraMappingType(infraMapping.getComputeProviderType())
                                                    .build());
        })
        .collect(toList());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceInstanceService#delete(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public void delete(String appId, String envId, String instanceId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceInstance.class)
                                .filter("appId", appId)
                                .filter(ServiceInstanceKeys.envId, envId)
                                .filter(ID_KEY, instanceId));
  }

  @Override
  public void deleteByEnv(String appId, String envId) {
    wingsPersistence.createQuery(ServiceInstance.class)
        .filter(ServiceInstanceKeys.appId, appId)
        .filter(ServiceInstanceKeys.envId, envId)
        .asList()
        .forEach(serviceInstance -> delete(appId, envId, serviceInstance.getUuid()));
  }

  @Override
  public void deleteByServiceTemplate(String appId, String envId, String templateId) {
    wingsPersistence.createQuery(ServiceInstance.class)
        .filter("appId", appId)
        .filter("envId", envId)
        .filter("serviceTemplate", templateId)
        .asList()
        .forEach(serviceInstance -> delete(appId, envId, serviceInstance.getUuid()));
  }

  @Override
  public void updateActivity(Activity activity) {
    Query<ServiceInstance> query = wingsPersistence.createQuery(ServiceInstance.class)
                                       .filter("appId", activity.getAppId())
                                       .filter(ID_KEY, activity.getServiceInstanceId());
    UpdateOperations<ServiceInstance> operations = wingsPersistence.createUpdateOperations(ServiceInstance.class);

    if (isNotBlank(activity.getArtifactId())) {
      operations.set("artifactId", activity.getArtifactId())
          .set("artifactName", activity.getArtifactName())
          .set("artifactStreamId", activity.getArtifactStreamId())
          .set("artifactStreamName", activity.getArtifactStreamName())
          .set("artifactDeployedOn", activity.getCreatedAt())
          .set("artifactDeploymentStatus", activity.getStatus())
          .set("artifactDeploymentActivityId", activity.getUuid());
    }
    operations.set("lastActivityId", activity.getUuid())
        .set("lastActivityStatus", activity.getStatus())
        .set("commandName", activity.getCommandName())
        .set("commandType", activity.getCommandType())
        .set("lastActivityCreatedAt", activity.getCreatedAt());

    if (activity.getType() == Type.Command && isNotBlank(activity.getArtifactId())) {
      operations.set("lastDeployedOn", activity.getLastUpdatedAt());
    }
    wingsPersistence.update(query, operations);
  }

  @Override
  public void pruneByInfrastructureMapping(String appId, String infraMappingId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceInstance.class)
                                .filter(ServiceInstanceKeys.appId, appId)
                                .filter(ServiceInstanceKeys.infraMappingId, infraMappingId));
  }

  @Override
  public void pruneByHost(String appId, String hostId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceInstance.class)
                                .filter(ServiceInstanceKeys.appId, appId)
                                .filter(ServiceInstanceKeys.hostId, hostId));
  }

  @Override
  public List<ServiceInstance> fetchServiceInstances(String appId, Set<String> uuids) {
    return wingsPersistence.createQuery(ServiceInstance.class)
        .filter(ServiceInstanceKeys.appId, appId)
        .field(ID_KEY)
        .in(uuids)
        .asList();
  }
}
