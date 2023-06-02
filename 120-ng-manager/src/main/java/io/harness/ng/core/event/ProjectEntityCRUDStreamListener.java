/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.services.ProjectService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
@Singleton
public class ProjectEntityCRUDStreamListener implements MessageListener {
  private final ProjectService projectService;
  private final EnvironmentService environmentService;
  private final ServiceEntityService serviceEntityService;
  private final ServiceOverrideService serviceOverrideService;
  private final InfrastructureEntityService infraService;
  private final ClusterService clusterService;
  private final EnvironmentGroupService environmentGroupService;

  private final InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private final InfrastructureMappingService infrastructureMappingService;

  @Inject
  public ProjectEntityCRUDStreamListener(ProjectService projectService, EnvironmentService environmentService,
      ServiceOverrideService serviceOverrideService, InfrastructureEntityService infraService,
      ServiceEntityService serviceEntityService, ClusterService clusterService,
      InfrastructureMappingService infrastructureMappingService,
      InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService,
      EnvironmentGroupService environmentGroupService) {
    this.projectService = projectService;
    this.environmentService = environmentService;
    this.serviceOverrideService = serviceOverrideService;
    this.serviceEntityService = serviceEntityService;
    this.infraService = infraService;
    this.clusterService = clusterService;
    this.environmentGroupService = environmentGroupService;
    this.infrastructureMappingService = infrastructureMappingService;
    this.instanceSyncPerpetualTaskInfoService = instanceSyncPerpetualTaskInfoService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (ORGANIZATION_ENTITY.equals(entityType)) {
          handleOrgEvent(message);
        } else if (PROJECT_ENTITY.equals(entityType)) {
          handleProjectEvent(message);
        }
      }
    }
    return true;
  }

  private void handleOrgEvent(Message message) {
    final Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = metadataMap.get(ACTION);
    if (action != null) {
      boolean status = processOrganizationEntityChangeEvent(organizationEntityChangeDTO, action);
      if (!status) {
        log.warn("failed to process org {} {}", organizationEntityChangeDTO, action);
      }
    }
  }

  private void handleProjectEvent(Message message) {
    final Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = metadataMap.get(ACTION);
    if (action != null) {
      switch (action) {
        case DELETE_ACTION:
          boolean status = processProjectDeleteEvent(projectEntityChangeDTO);
          if (!status) {
            log.warn("Failed to process project {} deletion event", projectEntityChangeDTO);
          }
          break;
        default:
      }
    }
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    final String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();
    final String orgIdentifier = projectEntityChangeDTO.getOrgIdentifier();
    final String projIdentifier = projectEntityChangeDTO.getIdentifier();

    boolean envDeleted = processQuietly(
        () -> environmentService.forceDeleteAllInProject(accountIdentifier, orgIdentifier, projIdentifier));
    boolean infraDeleted =
        processQuietly(() -> infraService.forceDeleteAllInProject(accountIdentifier, orgIdentifier, projIdentifier));
    boolean clustersDeleted =
        processQuietly(() -> clusterService.deleteAllFromProj(accountIdentifier, orgIdentifier, projIdentifier));
    boolean serviceDeleted = processQuietly(
        () -> serviceEntityService.forceDeleteAllInProject(accountIdentifier, orgIdentifier, projIdentifier));
    boolean serviceOverridesDeleted = processQuietly(
        () -> serviceOverrideService.deleteAllInProject(accountIdentifier, orgIdentifier, projIdentifier));
    boolean infraMappingDeleted = processQuietly(
        () -> infrastructureMappingService.deleteAllFromProj(accountIdentifier, orgIdentifier, projIdentifier));

    return envDeleted && infraDeleted && serviceDeleted && clustersDeleted && serviceOverridesDeleted
        && infraMappingDeleted;
  }

  boolean processQuietly(BooleanSupplier b) {
    try {
      b.getAsBoolean();
      // supplier processed
      return true;
    } catch (Exception ex) {
      log.error("failed to process entity deletion", ex);
      // ignore this
      return false;
    }
  }

  private boolean processOrganizationEntityChangeEvent(
      OrganizationEntityChangeDTO organizationEntityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        return processOrganizationDeleteEvent(organizationEntityChangeDTO);
      case RESTORE_ACTION:
        return processOrganizationRestoreEvent(organizationEntityChangeDTO);
      default:
    }
    return true;
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();
    String orgIdentifier = organizationEntityChangeDTO.getIdentifier();
    Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ProjectKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ProjectKeys.deleted)
                            .ne(Boolean.TRUE);
    List<Project> projects = projectService.list(criteria);
    AtomicBoolean success = new AtomicBoolean(true);
    projects.forEach(project -> {
      if (!projectService.delete(
              project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier(), null)) {
        log.error(String.format(
            "Delete operation failed for project with accountIdentifier %s, orgIdentifier %s and identifier %s",
            project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier()));
        success.set(false);
      }
    });
    if (success.get()) {
      log.info(String.format(
          "Successfully completed deletion for projects in organization having accountIdentifier %s and identifier %s",
          accountIdentifier, orgIdentifier));
    }

    boolean envDeleted = processQuietly(() -> environmentService.forceDeleteAllInOrg(accountIdentifier, orgIdentifier));
    boolean infraDeleted = processQuietly(() -> infraService.forceDeleteAllInOrg(accountIdentifier, orgIdentifier));
    // delete org level clusters when clusters are supported at org/account level
    boolean serviceDeleted =
        processQuietly(() -> serviceEntityService.forceDeleteAllInOrg(accountIdentifier, orgIdentifier));
    boolean serviceOverridesDeleted =
        processQuietly(() -> serviceOverrideService.deleteAllInOrg(accountIdentifier, orgIdentifier));
    boolean envGroupsDeleted =
        processQuietly(() -> environmentGroupService.deleteAllInOrg(accountIdentifier, orgIdentifier));

    return success.get() && envDeleted && infraDeleted && serviceDeleted && serviceOverridesDeleted && envGroupsDeleted;
  }

  private boolean processOrganizationRestoreEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();
    String orgIdentifier = organizationEntityChangeDTO.getIdentifier();
    Criteria criteria = Criteria.where(ProjectKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ProjectKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ProjectKeys.deleted)
                            .is(Boolean.TRUE);
    List<Project> projects = projectService.list(criteria);
    AtomicBoolean success = new AtomicBoolean(true);
    projects.forEach(project -> {
      if (!projectService.restore(
              project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier())) {
        log.error(String.format(
            "Restore operation failed for project with accountIdentifier %s, orgIdentifier %s and identifier %s",
            project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier()));
        success.set(false);
      }
    });
    if (success.get()) {
      log.info(String.format(
          "Successfully completed restoration for projects in organization with accountIdentifier %s and identifier %s",
          accountIdentifier, orgIdentifier));
    }
    return success.get();
  }
}
