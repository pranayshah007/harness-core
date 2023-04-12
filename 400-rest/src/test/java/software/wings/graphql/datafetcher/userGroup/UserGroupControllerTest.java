/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.userGroup;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.security.AppFilter;
import software.wings.service.intfc.AppService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UserGroupControllerTest {
  @Mock private AppService appService;
  @InjectMocks private UserGroupController controller;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeAppPermissionsWithMultipleAppPermissions() {
    when(appService.exist("id1")).thenReturn(true);
    when(appService.exist("id3")).thenReturn(true);

    Set<AppPermission> appPerm = new HashSet<>();
    appPerm.add(createAppPermission("id1", "id2"));
    appPerm.add(createAppPermission("id3"));
    final UserGroup userGroup = UserGroup.builder().appPermissions(appPerm).build();

    controller.sanitizeAppPermissions(userGroup);
    verify(appService).exist("id1");
    verify(appService).exist("id2");
    verify(appService).exist("id3");

    assertThat(extractAppIds(userGroup)).containsOnly("id1", "id3");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeAppPermissionsWhenAppPermissionIsNull() {
    when(appService.exist("id1")).thenReturn(true);

    Set<AppPermission> appPerm = new HashSet<>();
    appPerm.add(createAppPermission("id1", "id2"));
    appPerm.add(null);
    final UserGroup userGroup = UserGroup.builder().appPermissions(appPerm).build();

    controller.sanitizeAppPermissions(userGroup);
    verify(appService).exist("id1");
    verify(appService).exist("id2");

    assertThat(extractAppIds(userGroup)).containsOnly("id1");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSanitizeAppPermissionsWhenAppFilterIsNull() {
    when(appService.exist("id1")).thenReturn(true);

    Set<AppPermission> appPerm = new HashSet<>();
    appPerm.add(createAppPermission("id1", "id2"));
    appPerm.add(AppPermission.builder().appFilter(null).build());
    final UserGroup userGroup = UserGroup.builder().appPermissions(appPerm).build();

    controller.sanitizeAppPermissions(userGroup);
    verify(appService).exist("id1");
    verify(appService).exist("id2");

    assertThat(extractAppIds(userGroup)).containsOnly("id1");
  }

  private AppPermission createAppPermission(String... ids) {
    return AppPermission.builder()
        .appFilter(AppFilter.builder().ids(new HashSet<>(Arrays.asList(ids))).build())
        .build();
  }

  private List<String> extractAppIds(UserGroup userGroup) {
    List<String> result = new ArrayList<>();
    userGroup.getAppPermissions().forEach(p -> {
      if (Objects.nonNull(p)) {
        if (Objects.nonNull(p.getAppFilter())) {
          result.addAll(p.getAppFilter().getIds());
        }
      }
    });
    return result;
  }
}
