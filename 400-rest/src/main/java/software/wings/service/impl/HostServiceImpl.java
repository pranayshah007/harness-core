/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.queue.QueuePublisher;
import io.harness.stream.BoundedInputStream;

import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.Host.HostKeys;
import software.wings.dl.WingsPersistence;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ownership.OwnedByHost;
import software.wings.utils.HostCsvFileHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 5/9/16.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HostServiceImpl implements HostService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private HostCsvFileHelper csvFileHelper;
  @Inject private NotificationService notificationService;
  @Inject private EnvironmentService environmentService;
  @Inject private ConfigService configService;
  @Inject private ExecutorService executorService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ServiceResourceService serviceResourceService;

  @Inject private QueuePublisher<PruneEvent> pruneQueue;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<Host> list(PageRequest<Host> req) {
    return wingsPersistence.query(Host.class, req);
  }

  @Override
  public Host get(String appId, String envId, String hostId) {
    Host host = wingsPersistence.createQuery(Host.class)
                    .filter(ID_KEY, hostId)
                    .filter(HostKeys.envId, envId)
                    .filter("appId", appId)
                    .get();
    notNullCheck("Host is null for hostId: " + hostId + " appId: " + appId + " envId: " + envId, host);
    return host;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#update(software.wings.beans.infrastructure.Host)
   */
  @Override
  public Host update(String envId, Host host) {
    Host savedHost = get(host.getAppId(), envId, host.getUuid());

    if (savedHost == null) {
      throw new InvalidRequestException("Host doesn't exist");
    }

    ImmutableMap.Builder builder = ImmutableMap.<String, Object>builder().put("hostConnAttr", host.getHostConnAttr());
    if (host.getBastionConnAttr() != null) {
      builder.put("bastionConnAttr", host.getBastionConnAttr());
    }
    if (host.getProperties() != null) {
      builder.put("properties", host.getProperties());
    }
    wingsPersistence.updateFields(Host.class, savedHost.getUuid(), builder.build());

    return get(savedHost.getAppId(), savedHost.getEnvId(), host.getUuid());
  }

  @Override
  public Host saveHost(Host appHost) {
    Host applicationHost = wingsPersistence.createQuery(Host.class)
                               .filter(HostKeys.serviceTemplateId, appHost.getServiceTemplateId())
                               .filter(HostKeys.hostName, appHost.getHostName())
                               .filter(HostKeys.publicDns, appHost.getPublicDns())
                               .filter("appId", appHost.getAppId())
                               .filter(HostKeys.envId, appHost.getEnvId())
                               .filter(HostKeys.infraMappingId, appHost.getInfraMappingId())
                               .filter(HostKeys.properties, appHost.getProperties())
                               .get();
    if (applicationHost != null) {
      if (applicationHost.getEc2Instance() != null && appHost.getEc2Instance() != null) {
        applicationHost.setEc2Instance(appHost.getEc2Instance());
        wingsPersistence.updateField(
            Host.class, applicationHost.getUuid(), HostKeys.ec2Instance, appHost.getEc2Instance());
      }
      if (isNotEmpty(applicationHost.getHostConnAttr())
          && !applicationHost.getHostConnAttr().equals(appHost.getHostConnAttr())) {
        applicationHost.setHostConnAttr(appHost.getHostConnAttr());
        wingsPersistence.updateField(
            Host.class, applicationHost.getUuid(), HostKeys.hostConnAttr, appHost.getHostConnAttr());
      }
      return applicationHost;
    } else {
      wingsPersistence.save(appHost);
      return appHost;
    }
  }

  @Override
  public boolean exist(String appId, String hostId) {
    return wingsPersistence.createQuery(Host.class).filter(ID_KEY, hostId).filter("appId", appId).getKey() != null;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#importHosts(java.lang.String, java.lang.String,
   * software.wings.utils.BoundedInputStream)
   */
  @Override
  public int importHosts(String appId, String envId, String infraId, BoundedInputStream inputStream) {
    List<Host> hosts = csvFileHelper.parseHosts(infraId, appId, envId, inputStream);
    return (int) hosts.stream()
        .map(host -> {
          host.setEnvId(envId);
          return saveHost(host);
        })
        .filter(Objects::nonNull)
        .count();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#getHostsByHostIds(java.lang.String, java.util.List)
   */
  @Override
  public List<Host> getHostsByHostIds(String appId, String envId, List<String> hostUuids) {
    return wingsPersistence.createQuery(Host.class)
        .filter("appId", appId)
        .filter(HostKeys.envId, envId)
        .field(ID_KEY)
        .hasAnyOf(hostUuids)
        .asList();
  }

  @Override
  public long getHostsCountByInfraMappingIds(String appId, List<String> infraMappingIds) {
    return wingsPersistence.createQuery(Host.class)
        .filter("appId", appId)
        .field(HostKeys.infraMappingId)
        .in(infraMappingIds)
        .count();
  }

  @Override
  public Host getHostByEnv(String appId, String envId, String hostId) {
    return wingsPersistence.createQuery(Host.class)
        .filter("appId", appId)
        .filter(HostKeys.envId, envId)
        .filter(ID_KEY, hostId)
        .get();
  }

  @Override
  public void delete(String appId, String envId, String hostId) {
    Host applicationHost = get(appId, envId, hostId);
    delete(applicationHost);
  }

  private boolean delete(Host host) {
    if (host == null) {
      return true;
    }
    pruneQueue.send(new PruneEvent(Host.class, host.getAppId(), host.getUuid()));
    return wingsPersistence.delete(host);
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String hostId) {
    List<OwnedByHost> services = ServiceClassLocator.descendingServices(this, HostServiceImpl.class, OwnedByHost.class);
    PruneEntityListener.pruneDescendingEntities(services, descending -> descending.pruneByHost(appId, hostId));
  }

  @Override
  public void deleteByEnvironment(String appId, String envId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(Host.class).filter("appId", appId).filter(HostKeys.envId, envId));
  }

  @Override
  public void deleteByDnsName(String appId, String infraMappingId, String dnsName) {
    wingsPersistence.delete(wingsPersistence.createQuery(Host.class)
                                .filter("appId", appId)
                                .filter(HostKeys.infraMappingId, infraMappingId)
                                .filter(HostKeys.publicDns, dnsName));
  }

  @Override
  public void updateHostConnectionAttrByInfraMapping(
      InfrastructureMapping infrastructureMapping, String hostConnectionAttrs) {
    String appId = infrastructureMapping.getAppId();
    String infraMappingId = infrastructureMapping.getUuid();
    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());

    Query<Host> query =
        wingsPersistence.createQuery(Host.class).filter("appId", appId).filter(HostKeys.infraMappingId, infraMappingId);

    UpdateOperations<Host> operations = DeploymentType.SSH == deploymentType
        ? wingsPersistence.createUpdateOperations(Host.class).set("hostConnAttr", hostConnectionAttrs)
        : wingsPersistence.createUpdateOperations(Host.class).set("winrmConnAttr", hostConnectionAttrs);
    wingsPersistence.update(query, operations);
  }

  @Override
  public void pruneByInfrastructureMapping(String appId, String infraMappingId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Host.class)
                                .filter("appId", appId)
                                .filter(HostKeys.infraMappingId, infraMappingId));
  }

  @Override
  public void deleteByService(String appId, String envId, String serviceTemplateId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Host.class)
                                .filter("appId", appId)
                                .filter(HostKeys.serviceTemplateId, serviceTemplateId));
  }
}
