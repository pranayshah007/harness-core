/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationState;
import io.harness.accesscontrol.commons.bootstrap.ConfigurationStateRepository;
import io.harness.accesscontrol.resources.resourcetypes.ResourceTypeManagementJob;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.util.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PermissionsManagementJobTest extends AccessControlTestBase {
  @Inject private PermissionService permissionService;
  @Inject private ConfigurationStateRepository configurationStateRepository;
  @Inject private ResourceTypeManagementJob resourceTypeManagementJob;
  @Inject private PermissionsManagementJob permissionsManagementJob;
  private static final String PERMISSIONS_CONFIG_FIELD = "permissionsConfig";
  private static final String VERSION_FIELD = "version";
  private static final String ACCOUNT_SCOPE_LEVEL = "account";
  private static final String RANDOM_NAME = "randomName";
  private static final String NEW_PERMISSION = "core_project_randomIdentifier";

  @Before
  public void setUp() {
    resourceTypeManagementJob.run();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSave() {
    PermissionsConfig permissionsConfig =
        (PermissionsConfig) ReflectionUtils.getFieldValue(permissionsManagementJob, PERMISSIONS_CONFIG_FIELD);
    PermissionsConfig permissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(permissionsConfig);

    permissionsManagementJob.run();
    validate(permissionsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws NoSuchFieldException, IllegalAccessException {
    Field f = permissionsManagementJob.getClass().getDeclaredField(PERMISSIONS_CONFIG_FIELD);
    PermissionsConfig permissionsConfig =
        (PermissionsConfig) ReflectionUtils.getFieldValue(permissionsManagementJob, PERMISSIONS_CONFIG_FIELD);
    ReflectionUtils.setObjectField(permissionsConfig.getClass().getDeclaredField(VERSION_FIELD), permissionsConfig, 2);
    PermissionsConfig latestPermissionsConfig = (PermissionsConfig) HObjectMapper.clone(permissionsConfig);
    PermissionsConfig currentPermissionsConfig = PermissionsConfig.builder()
                                                     .version(1)
                                                     .name(latestPermissionsConfig.getName())
                                                     .permissions(new HashSet<>())
                                                     .build();
    PermissionsConfig currentPermissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(currentPermissionsConfig);
    ReflectionUtils.setObjectField(f, permissionsManagementJob, currentPermissionsConfig);
    permissionsManagementJob.run();
    validate(currentPermissionsConfigClone);

    PermissionsConfig latestPermissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(latestPermissionsConfig);
    ReflectionUtils.setObjectField(f, permissionsManagementJob, latestPermissionsConfig);
    permissionsManagementJob.run();
    validate(latestPermissionsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddNewPermission() throws NoSuchFieldException, IllegalAccessException {
    permissionsManagementJob.run();

    PermissionsConfig permissionsConfig =
        (PermissionsConfig) ReflectionUtils.getFieldValue(permissionsManagementJob, PERMISSIONS_CONFIG_FIELD);
    Set<Permission> currentPermissions = permissionsConfig.getPermissions();
    Set<String> allowedScopeLevels = new HashSet<>();
    allowedScopeLevels.add(ACCOUNT_SCOPE_LEVEL);
    currentPermissions.add(Permission.builder()
                               .identifier(NEW_PERMISSION)
                               .name(RANDOM_NAME)
                               .status(PermissionStatus.ACTIVE)
                               .includeInAllRoles(true)
                               .allowedScopeLevels(allowedScopeLevels)
                               .build());

    int currentVersion = permissionsConfig.getVersion();
    ReflectionUtils.setObjectField(
        permissionsConfig.getClass().getDeclaredField(VERSION_FIELD), permissionsConfig, currentVersion + 1);

    PermissionsConfig permissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(permissionsConfig);

    permissionsManagementJob.run();
    validate(permissionsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testRemoveOldPermission() throws NoSuchFieldException, IllegalAccessException {
    Field f = permissionsManagementJob.getClass().getDeclaredField(PERMISSIONS_CONFIG_FIELD);
    PermissionsConfig currentPermissionsConfig =
        (PermissionsConfig) ReflectionUtils.getFieldValue(permissionsManagementJob, PERMISSIONS_CONFIG_FIELD);
    PermissionsConfig latestPermissionsConfig = (PermissionsConfig) HObjectMapper.clone(currentPermissionsConfig);
    ReflectionUtils.setObjectField(latestPermissionsConfig.getClass().getDeclaredField(VERSION_FIELD),
        latestPermissionsConfig, currentPermissionsConfig.getVersion() + 1);
    Set<String> allowedScopeLevels = new HashSet<>();
    allowedScopeLevels.add(ACCOUNT_SCOPE_LEVEL);
    currentPermissionsConfig.getPermissions().add(Permission.builder()
                                                      .identifier(NEW_PERMISSION)
                                                      .name(RANDOM_NAME)
                                                      .status(PermissionStatus.ACTIVE)
                                                      .includeInAllRoles(true)
                                                      .allowedScopeLevels(allowedScopeLevels)
                                                      .build());

    PermissionsConfig currentPermissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(currentPermissionsConfig);
    permissionsManagementJob.run();
    validate(currentPermissionsConfigClone);

    ReflectionUtils.setObjectField(f, permissionsManagementJob, latestPermissionsConfig);
    PermissionsConfig latestPermissionsConfigClone = (PermissionsConfig) HObjectMapper.clone(latestPermissionsConfig);
    permissionsManagementJob.run();
    validate(latestPermissionsConfigClone);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testInvalidPermission() {
    PermissionsConfig currentPermissionsConfig =
        (PermissionsConfig) ReflectionUtils.getFieldValue(permissionsManagementJob, PERMISSIONS_CONFIG_FIELD);
    PermissionsConfig latestPermissionsConfig = (PermissionsConfig) HObjectMapper.clone(currentPermissionsConfig);
    Set<String> allowedScopeLevels = new HashSet<>();
    allowedScopeLevels.add(ACCOUNT_SCOPE_LEVEL);
    currentPermissionsConfig.getPermissions().add(Permission.builder()
                                                      .identifier("core_missingResource_randomIdentifier")
                                                      .name(RANDOM_NAME)
                                                      .status(PermissionStatus.ACTIVE)
                                                      .includeInAllRoles(true)
                                                      .allowedScopeLevels(allowedScopeLevels)
                                                      .build());
    try {
      permissionsManagementJob.run();
      fail();
    } catch (InvalidRequestException exception) {
      // hope
    }

    currentPermissionsConfig = latestPermissionsConfig;
    currentPermissionsConfig.getPermissions().add(Permission.builder()
                                                      .identifier("core_wrongPattern")
                                                      .name(RANDOM_NAME)
                                                      .status(PermissionStatus.ACTIVE)
                                                      .includeInAllRoles(true)
                                                      .allowedScopeLevels(allowedScopeLevels)
                                                      .build());
    try {
      permissionsManagementJob.run();
      fail();
    } catch (InvalidRequestException exception) {
      // hope
    }
  }

  private void validate(PermissionsConfig permissionsConfig) {
    Optional<ConfigurationState> optional = configurationStateRepository.getByIdentifier(permissionsConfig.getName());
    assertTrue(optional.isPresent());
    assertEquals(permissionsConfig.getVersion(), optional.get().getConfigVersion());

    PermissionFilter permissionFilter = PermissionFilter.builder().build();

    Set<Permission> currentPermissions = new HashSet<>(permissionService.list(permissionFilter));
    assertEquals(permissionsConfig.getPermissions(), currentPermissions);
  }
}
