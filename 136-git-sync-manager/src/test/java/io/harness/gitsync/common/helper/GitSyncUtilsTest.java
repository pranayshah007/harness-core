/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class GitSyncUtilsTest extends CategoryTest {
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void getEntityTypeFromYaml() throws IOException {
    final String s =
        IOUtils.resourceToString("yaml/testyaml.yaml", StandardCharsets.UTF_8, getClass().getClassLoader());
    final EntityType entityTypeFromYaml = GitSyncUtils.getEntityTypeFromYaml(s);
    assertThat(entityTypeFromYaml).isEqualTo(EntityType.CONNECTORS);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetUserIdentifier() {
    MockedStatic<GlobalContextManager> mockedStatic = mockStatic(GlobalContextManager.class);
    mockedStatic.when(() -> GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT))
        .thenReturn(PrincipalContextData.builder()
                        .principal(new UserPrincipal("user", "user@harness.io", "user1", "accountId"))
                        .build());
    Optional<String> userId = GitSyncUtils.getUserIdentifier();
    assertEquals(userId.get(), "user");
  }
}
