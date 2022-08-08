/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup;

import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.PersistentLocker;
import io.harness.ng.beans.PageRequest;
import io.harness.reflection.ReflectionUtils;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationState;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationStateRepository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class ResourceGroupsManagementJobTest extends ResourceGroupTestBase {
  @Inject private ResourceGroupService resourceGroupService;
  @Inject private ConfigurationStateRepository configurationStateRepository;
  @Inject private PersistentLocker persistentLocker;
  @Inject private ResourceGroupsManagementJob resourceGroupsManagementJob;
  private static final String RESOURCE_GROUPS_CONFIG_FIELD = "resourceGroupsConfig";
  private static final String VERSION_FIELD = "version";

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSave() {
    ResourceGroupsConfig resourceGroupsConfig =
        (ResourceGroupsConfig) ReflectionUtils.getFieldValue(resourceGroupsManagementJob, RESOURCE_GROUPS_CONFIG_FIELD);
    ResourceGroupsConfig resourceGroupsConfigClone = (ResourceGroupsConfig) HObjectMapper.clone(resourceGroupsConfig);

    resourceGroupsManagementJob.run();
    validate(resourceGroupsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws NoSuchFieldException, IllegalAccessException {
    Field f = resourceGroupsManagementJob.getClass().getDeclaredField(RESOURCE_GROUPS_CONFIG_FIELD);
    ResourceGroupsConfig resourceGroupsConfig =
        (ResourceGroupsConfig) ReflectionUtils.getFieldValue(resourceGroupsManagementJob, RESOURCE_GROUPS_CONFIG_FIELD);
    ReflectionUtils.setObjectField(
        resourceGroupsConfig.getClass().getDeclaredField(VERSION_FIELD), resourceGroupsConfig, 2);
    ResourceGroupsConfig latestResourceGroupsConfig = (ResourceGroupsConfig) HObjectMapper.clone(resourceGroupsConfig);
    ResourceGroupsConfig currentResourceGroupsConfig = ResourceGroupsConfig.builder()
                                                           .version(1)
                                                           .name(latestResourceGroupsConfig.getName())
                                                           .resourceGroups(new HashSet<>())
                                                           .build();
    ResourceGroupsConfig currentResourceGroupsConfigClone =
        (ResourceGroupsConfig) HObjectMapper.clone(currentResourceGroupsConfig);
    ReflectionUtils.setObjectField(f, resourceGroupsManagementJob, currentResourceGroupsConfig);
    resourceGroupsManagementJob.run();
    validate(currentResourceGroupsConfigClone);

    ResourceGroupsConfig latestResourceGroupsConfigClone =
        (ResourceGroupsConfig) HObjectMapper.clone(latestResourceGroupsConfig);
    ReflectionUtils.setObjectField(f, resourceGroupsManagementJob, latestResourceGroupsConfig);
    resourceGroupsManagementJob.run();
    validate(latestResourceGroupsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddNewResourceGroupConfig() throws NoSuchFieldException, IllegalAccessException {
    resourceGroupsManagementJob.run();

    ResourceGroupsConfig resourceGroupsConfig =
        (ResourceGroupsConfig) ReflectionUtils.getFieldValue(resourceGroupsManagementJob, RESOURCE_GROUPS_CONFIG_FIELD);
    resourceGroupsConfig.getResourceGroups().add(
        ResourceGroupConfig.builder().identifier("randomIdentifier").description("randomDescription").build());

    int currentVersion = resourceGroupsConfig.getVersion();
    ReflectionUtils.setObjectField(
        resourceGroupsConfig.getClass().getDeclaredField(VERSION_FIELD), resourceGroupsConfig, currentVersion + 1);

    ResourceGroupsConfig resourceGroupsConfigClone = (ResourceGroupsConfig) HObjectMapper.clone(resourceGroupsConfig);

    resourceGroupsManagementJob.run();
    validate(resourceGroupsConfigClone);
  }

  private void validate(ResourceGroupsConfig resourceGroupsConfig) {
    Optional<ConfigurationState> optional =
        configurationStateRepository.getByIdentifier(resourceGroupsConfig.getName());
    assertTrue(optional.isPresent());
    assertEquals(resourceGroupsConfig.getVersion(), optional.get().getConfigVersion());

    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(100).build();
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        ResourceGroupFilterDTO.builder().managedFilter(ManagedFilter.ONLY_MANAGED).build();

    Set<ResourceGroupConfig> currentResourceConfigs =
        resourceGroupService.list(resourceGroupFilterDTO, pageRequest)
            .getContent()
            .stream()
            .map(resourceGroup -> ResourceGroupConfigMapper.toConfig(resourceGroup.getResourceGroup()))
            .collect(Collectors.toSet());
    assertEquals(resourceGroupsConfig.getResourceGroups().size(), currentResourceConfigs.size());
    Map<String, ResourceGroupConfig> currentResourceGroupConfigsMap = new HashMap<>();
    currentResourceConfigs.forEach(resourceGroupConfig
        -> currentResourceGroupConfigsMap.put(resourceGroupConfig.getIdentifier(), resourceGroupConfig));

    resourceGroupsConfig.getResourceGroups().forEach(resourceGroupConfig -> {
      ResourceGroupConfig currentResourceGroupConfig =
          currentResourceGroupConfigsMap.get(resourceGroupConfig.getIdentifier());
      assertEquals(resourceGroupConfig, currentResourceGroupConfig);
    });
  }
}
