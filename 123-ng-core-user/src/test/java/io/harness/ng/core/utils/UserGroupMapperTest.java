/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;
import static io.harness.rule.OwnerRule.TEJAS;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.entities.EmailConfig;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rule.Owner;

import software.wings.beans.sso.SSOType;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UserGroupMapperTest {
  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testToDTOMapper() {
    UserGroup userGroup = getUserGroup();
    UserGroupDTO userGroupDTO = UserGroupMapper.toDTO(userGroup);
    verifyUserGroupDTO(userGroupDTO, userGroup);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testToEntityMapper() {
    UserGroupDTO userGroupDTO = getUserGroupDTO();
    UserGroup userGroup = UserGroupMapper.toEntity(userGroupDTO);
    verifyUserGroup(userGroup, userGroupDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testToEntityMapper_InvalidSSOType() {
    UserGroupDTO userGroupDTO = getUserGroupDTO();
    userGroupDTO.setLinkedSsoType(randomAlphabetic(10));
    UserGroupMapper.toEntity(userGroupDTO);
  }

  private void verifyUserGroup(UserGroup userGroup, UserGroupDTO userGroupDTO) {
    assertThat(userGroupDTO)
        .usingRecursiveComparison()
        .ignoringFields("linkedSsoType", "notificationConfigs", "tags")
        .isEqualTo(userGroup);

    assertEquals(convertToList(Optional.ofNullable(userGroupDTO.getTags()).orElse(emptyMap())), userGroup.getTags());
    assertEquals(userGroupDTO.isExternallyManaged(), (boolean) userGroup.getExternallyManaged());
    assertEquals(userGroupDTO.isSsoLinked(), (boolean) userGroup.getIsSsoLinked());
    assertEquals((Optional.ofNullable(userGroupDTO.getNotificationConfigs()).orElse(emptyList()))
                     .stream()
                     .map(UserGroupMapper::toEntity)
                     .collect(Collectors.toList()),
        userGroup.getNotificationConfigs());
    assertEquals(userGroupDTO.getLinkedSsoType(), userGroup.getLinkedSsoType().name());
  }

  private void verifyUserGroupDTO(UserGroupDTO userGroupDTO, UserGroup userGroup) {
    assertThat(userGroupDTO)
        .usingRecursiveComparison()
        .ignoringFields("linkedSsoType", "notificationConfigs", "tags")
        .isEqualTo(userGroup);
    assertEquals(convertToMap(userGroup.getTags()), userGroupDTO.getTags());
    assertEquals((boolean) userGroup.getExternallyManaged(), userGroupDTO.isExternallyManaged());
    assertEquals((boolean) userGroup.getIsSsoLinked(), userGroupDTO.isSsoLinked());
    assertEquals(emptyIfNull(userGroup.getNotificationConfigs())
                     .stream()
                     .map(UserGroupMapper::toDTO)
                     .collect(Collectors.toList()),
        userGroupDTO.getNotificationConfigs());
    assertEquals(userGroup.getLinkedSsoType().name(), userGroupDTO.getLinkedSsoType());
  }

  private UserGroup getUserGroup() {
    return UserGroup.builder()
        .accountIdentifier(randomAlphabetic(10))
        .orgIdentifier(randomAlphabetic(10))
        .projectIdentifier(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .description(randomAlphabetic(10))
        .tag(NGTag.builder().key(randomAlphabetic(10)).value(randomAlphabetic(10)).build())
        .name(randomAlphabetic(10))
        .ssoGroupId(randomAlphabetic(10))
        .ssoGroupName(randomAlphabetic(10))
        .externallyManaged(false)
        .linkedSsoDisplayName(randomAlphabetic(10))
        .linkedSsoId(randomAlphabetic(10))
        .isSsoLinked(false)
        .harnessManaged(false)
        .notificationConfigs(Arrays.asList(EmailConfig.builder().groupEmail(randomAlphabetic(10)).build()))
        .users(Arrays.asList(randomAlphabetic(10)))
        .linkedSsoType(SSOType.SAML)
        .build();
  }

  private UserGroupDTO getUserGroupDTO() {
    return UserGroupDTO.builder()
        .accountIdentifier(randomAlphabetic(10))
        .orgIdentifier(randomAlphabetic(10))
        .projectIdentifier(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .description(randomAlphabetic(10))
        .tags(Map.of(randomAlphabetic(10), randomAlphabetic(10)))
        .name(randomAlphabetic(10))
        .ssoGroupId(randomAlphabetic(10))
        .ssoGroupName(randomAlphabetic(10))
        .externallyManaged(false)
        .linkedSsoDisplayName(randomAlphabetic(10))
        .linkedSsoId(randomAlphabetic(10))
        .isSsoLinked(false)
        .harnessManaged(false)
        .notificationConfigs(Arrays.asList(EmailConfigDTO.builder().groupEmail(randomAlphabetic(10)).build()))
        .users(Arrays.asList(randomAlphabetic(10)))
        .linkedSsoType(SSOType.SAML.name())
        .build();
  }
}
