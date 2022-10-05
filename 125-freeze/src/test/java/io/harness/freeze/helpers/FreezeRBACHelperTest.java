/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(CDC)
public class FreezeRBACHelperTest extends CategoryTest {
  AccessControlClient accessControlClient = mock(AccessControlClient.class);

  private String yaml1 =
      "freeze:\n  identifier: \"idx\"\n  name: \"name\"\n  description: \"desc\"\n  orgIdentifier: \"default\"\n  projectIdentifier: \"Sample\"\n  status: \"Enabled\"\n  entityConfigs:\n    - name: \"rule1\"\n      entities:\n      - filterType: \"Equals\"\n        type: \"Service\"\n        entityRefs:\n          - \"s1\"";
  private String yaml2 =
      "freeze:\n  identifier: \"idx\"\n  name: \"name\"\n  description: \"desc\"\n  orgIdentifier: \"default\"\n  projectIdentifier: \"Sample\"\n  status: \"Enabled\"\n  entityConfigs:\n    - name: \"rule1\"\n      entities:\n      - filterType: \"All\"\n        type: \"Service\"";

  public final Map<FreezeEntityType, Pair<String, String>> entityTypeToResourceAndPermissionMap =
      Maps.of(FreezeEntityType.ENVIRONMENT, MutablePair.of("ENVIRONMENT", "core_environment_view"),
          FreezeEntityType.SERVICE, MutablePair.of("SERVICE", "core_service_view"), FreezeEntityType.ORG,
          MutablePair.of("ORGANIZATION", "core_organization_view"), FreezeEntityType.PROJECT,
          MutablePair.of("PROJECT", "core_project_view"));

  @Test(expected = NGAccessDeniedException.class)
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testCheckAccessNegative() {
    Mockito
        .doThrow(new NGAccessDeniedException("", USER, Collections.singletonList(PermissionCheckDTO.builder().build())))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of("kmpySmUISimoRrJL6NL73w", "default", "Sample"),
            Resource.of("SERVICE", "s1"), "core_service_view");

    FreezeRBACHelper.checkAccess("kmpySmUISimoRrJL6NL73w", "Sample", "default", yaml1, accessControlClient);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testCheckAccessPositive() {
    Mockito
        .doThrow(new NGAccessDeniedException("", USER, Collections.singletonList(PermissionCheckDTO.builder().build())))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of("kmpySmUISimoRrJL6NL73w", "default", "Sample"),
            Resource.of("SERVICE", "s1"), "core_service_view");

    FreezeRBACHelper.checkAccess("kmpySmUISimoRrJL6NL73w", "Sample", "default", yaml2, accessControlClient);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetResourceTypeAndPermissionPositive() {
    Optional<Pair<String, String>> result;
    for (Map.Entry<FreezeEntityType, Pair<String, String>> entry : entityTypeToResourceAndPermissionMap.entrySet()) {
      FreezeEntityType type = entry.getKey();
      result = FreezeRBACHelper.getResourceTypeAndPermission(type);
      assertThat(result.isPresent()).isEqualTo(true);
      assertThat(result.get().getKey()).isEqualTo(entry.getValue().getKey());
      assertThat(result.get().getValue()).isEqualTo(entry.getValue().getValue());
    }

    result = FreezeRBACHelper.getResourceTypeAndPermission(FreezeEntityType.ENV_TYPE);
    assertThat(result.isPresent()).isEqualTo(false);
  }
}
