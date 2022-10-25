/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcetypes;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationState;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationStateRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.reflection.util.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ResourceTypeManagementJobTest extends AccessControlTestBase {
  @Inject private ResourceTypeService resourceTypeService;
  @Inject private ConfigurationStateRepository configurationStateRepository;
  @Inject private ResourceTypeManagementJob resourceTypeManagementJob;
  private static final String RESOURCE_TYPES_CONFIG_FIELD = "resourceTypesConfig";
  private static final String VERSION_FIELD = "version";

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSave() {
    ResourceTypesConfig resourceTypesConfig =
        (ResourceTypesConfig) ReflectionUtils.getFieldValue(resourceTypeManagementJob, RESOURCE_TYPES_CONFIG_FIELD);
    ResourceTypesConfig resourceTypesConfigClone = (ResourceTypesConfig) HObjectMapper.clone(resourceTypesConfig);

    resourceTypeManagementJob.run();
    validate(resourceTypesConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws NoSuchFieldException, IllegalAccessException {
    Field f = resourceTypeManagementJob.getClass().getDeclaredField(RESOURCE_TYPES_CONFIG_FIELD);
    ResourceTypesConfig resourceTypesConfig =
        (ResourceTypesConfig) ReflectionUtils.getFieldValue(resourceTypeManagementJob, RESOURCE_TYPES_CONFIG_FIELD);
    ReflectionUtils.setObjectField(
        resourceTypesConfig.getClass().getDeclaredField(VERSION_FIELD), resourceTypesConfig, 2);
    ResourceTypesConfig latestResourceTypesConfig = (ResourceTypesConfig) HObjectMapper.clone(resourceTypesConfig);
    ResourceTypesConfig currentResourceTypesConfig = ResourceTypesConfig.builder()
                                                         .version(1)
                                                         .name(latestResourceTypesConfig.getName())
                                                         .resourceTypes(new HashSet<>())
                                                         .build();
    ResourceTypesConfig currentResourceTypesConfigClone =
        (ResourceTypesConfig) HObjectMapper.clone(currentResourceTypesConfig);
    ReflectionUtils.setObjectField(f, resourceTypeManagementJob, currentResourceTypesConfig);
    resourceTypeManagementJob.run();
    validate(currentResourceTypesConfigClone);

    ResourceTypesConfig latestResourceTypesConfigClone =
        (ResourceTypesConfig) HObjectMapper.clone(latestResourceTypesConfig);
    ReflectionUtils.setObjectField(f, resourceTypeManagementJob, latestResourceTypesConfig);
    resourceTypeManagementJob.run();
    validate(latestResourceTypesConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddNewResourceType() throws NoSuchFieldException, IllegalAccessException {
    resourceTypeManagementJob.run();

    ResourceTypesConfig resourceTypesConfig =
        (ResourceTypesConfig) ReflectionUtils.getFieldValue(resourceTypeManagementJob, RESOURCE_TYPES_CONFIG_FIELD);
    resourceTypesConfig.getResourceTypes().add(
        ResourceType.builder().identifier("randomIdentifier").permissionKey("randomPermissionKey").build());

    int currentVersion = resourceTypesConfig.getVersion();
    ReflectionUtils.setObjectField(
        resourceTypesConfig.getClass().getDeclaredField(VERSION_FIELD), resourceTypesConfig, currentVersion + 1);

    ResourceTypesConfig resourceTypesConfigClone = (ResourceTypesConfig) HObjectMapper.clone(resourceTypesConfig);

    resourceTypeManagementJob.run();
    validate(resourceTypesConfigClone);
  }

  private void validate(ResourceTypesConfig resourceTypesConfig) {
    Optional<ConfigurationState> optional = configurationStateRepository.getByIdentifier(resourceTypesConfig.getName());
    assertTrue(optional.isPresent());
    assertEquals(resourceTypesConfig.getVersion(), optional.get().getConfigVersion());

    Set<ResourceType> currentResourceTypes = new HashSet<>(resourceTypeService.list());
    assertEquals(currentResourceTypes, resourceTypesConfig.getResourceTypes());
  }
}
