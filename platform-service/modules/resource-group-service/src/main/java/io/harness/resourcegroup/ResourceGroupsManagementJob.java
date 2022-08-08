/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationState;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationStateRepository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class ResourceGroupsManagementJob {
  private static final String RESOURCE_GROUPS_YAML_PATH = "io/harness/resourcegroup/managed-resourcegroups.yml";

  private final ResourceGroupService resourceGroupService;
  private final ConfigurationStateRepository configurationStateRepository;
  private final ResourceGroupsConfig resourceGroupsConfig;
  private final PersistentLocker persistentLocker;
  private static final String RESOURCE_GROUP_CONFIG_MANAGEMENT_LOCK = "RESOURCE_GROUP_CONFIG_MANAGEMENT_LOCK";

  @Inject
  public ResourceGroupsManagementJob(ResourceGroupService resourceGroupService,
      ConfigurationStateRepository configurationStateRepository, PersistentLocker persistentLocker) {
    this.resourceGroupService = resourceGroupService;
    this.configurationStateRepository = configurationStateRepository;
    this.persistentLocker = persistentLocker;
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(RESOURCE_GROUPS_YAML_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      this.resourceGroupsConfig = om.readValue(bytes, ResourceGroupsConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Resource Groups file path or format is invalid");
    }
  }

  public void run() {
    try (AcquiredLock<?> lock = acquireLock(RESOURCE_GROUP_CONFIG_MANAGEMENT_LOCK, true)) {
      if (lock != null) {
        runInternal();
      }
    } catch (InterruptedException e) {
      log.error(String.format("Interrupted while trying to acquire %s", RESOURCE_GROUP_CONFIG_MANAGEMENT_LOCK), e);
      Thread.currentThread().interrupt();
    } catch (IllegalMonitorStateException e) {
      log.error(String.format("Error while releasing the lock %s", RESOURCE_GROUP_CONFIG_MANAGEMENT_LOCK), e);
    }
  }

  protected AcquiredLock<?> acquireLock(String lockIdentifier, boolean retryIndefinitely) throws InterruptedException {
    AcquiredLock<?> lock = null;
    do {
      try {
        log.info("Trying to acquire {} lock with 5 seconds timeout", lockIdentifier);
        lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(lockIdentifier, Duration.ofSeconds(5));
      } catch (Exception ex) {
        log.warn("Unable to get {} lock, due to the exception. Will retry again", lockIdentifier, ex);
      }
      if (lock == null) {
        TimeUnit.SECONDS.sleep(60);
      }
    } while (lock == null && retryIndefinitely);
    return lock;
  }

  public void runInternal() {
    Optional<ConfigurationState> optional =
        configurationStateRepository.getByIdentifier(resourceGroupsConfig.getName());
    if (optional.isPresent() && optional.get().getConfigVersion() >= resourceGroupsConfig.getVersion()) {
      log.info("Managed resource groups are already updated in the database");
      return;
    }

    log.info("Updating resource groups in the database");

    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(100).build();
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().managedFilter(ManagedFilter.ONLY_MANAGED).build();

    Set<ResourceGroupConfig> latestResourceGroups = resourceGroupsConfig.getResourceGroups();
    Set<ResourceGroupConfig> currentResourceGroups =
        resourceGroupService.list(resourceGroupFilterDTO, pageRequest)
            .getContent()
            .stream()
            .map(resourceGroup -> ResourceGroupConfigMapper.toConfig(resourceGroup.getResourceGroup()))
            .collect(Collectors.toSet());
    Set<ResourceGroupConfig> addedOrUpdatedResourceGroups =
        Sets.difference(latestResourceGroups, currentResourceGroups);

    Set<String> latestIdentifiers =
        latestResourceGroups.stream().map(ResourceGroupConfig::getIdentifier).collect(Collectors.toSet());
    Set<String> currentIdentifiers =
        currentResourceGroups.stream().map(ResourceGroupConfig::getIdentifier).collect(Collectors.toSet());

    Set<String> addedIdentifiers = Sets.difference(latestIdentifiers, currentIdentifiers);
    Set<String> removedIdentifiers = Sets.difference(currentIdentifiers, latestIdentifiers);

    Set<ResourceGroupConfig> addedResourceGroups = addedOrUpdatedResourceGroups.stream()
                                                       .filter(p -> addedIdentifiers.contains(p.getIdentifier()))
                                                       .collect(Collectors.toSet());

    Set<ResourceGroupConfig> updatedResourceGroups = addedOrUpdatedResourceGroups.stream()
                                                         .filter(p -> !addedIdentifiers.contains(p.getIdentifier()))
                                                         .collect(Collectors.toSet());

    addedResourceGroups.forEach(
        resourceGroupConfig -> resourceGroupService.create(ResourceGroupConfigMapper.toDTO(resourceGroupConfig), true));
    updatedResourceGroups.forEach(
        resourceGroupConfig -> resourceGroupService.update(ResourceGroupConfigMapper.toDTO(resourceGroupConfig), true));
    removedIdentifiers.forEach(resourceGroupService::deleteManaged);

    ConfigurationState configurationState =
        optional.orElseGet(() -> ConfigurationState.builder().identifier(resourceGroupsConfig.getName()).build());
    configurationState.setConfigVersion(resourceGroupsConfig.getVersion());
    configurationStateRepository.upsert(configurationState);
  }
}
