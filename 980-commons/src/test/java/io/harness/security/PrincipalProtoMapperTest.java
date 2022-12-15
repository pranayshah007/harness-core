/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.security.dto.PrincipalType.SERVICE;
import static io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.security.dto.PrincipalType.USER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;

import com.google.protobuf.StringValue;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PrincipalProtoMapperTest extends CategoryTest {
  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testPrincipalMapperForUserPrincipal() {
    String userId = "userId";
    String email = "email";
    String userName = "userName";
    UserPrincipal userPrincipalProto = UserPrincipal.newBuilder()
                                           .setUserId(StringValue.of(userId))
                                           .setEmail(StringValue.of(email))
                                           .setUserName(StringValue.of(userName))
                                           .build();
    io.harness.security.Principal principalProto =
        io.harness.security.Principal.newBuilder().setUserPrincipal(userPrincipalProto).build();
    Principal principal = PrincipalProtoMapper.toPrincipalDTO("accountId", principalProto);
    assertThat(principal).isNotNull();
    assertThat(principal.getType()).isEqualTo(USER);
    io.harness.security.dto.UserPrincipal userPrincipal = (io.harness.security.dto.UserPrincipal) principal;
    assertThat(userPrincipal.getAccountId()).isEqualTo("accountId");
    assertThat(userPrincipal.getEmail()).isEqualTo(email);
    assertThat(userPrincipal.getUsername()).isEqualTo(userName);
    assertThat(userPrincipal.getName()).isEqualTo(userId);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testPrincipalMapperForServicePrincipal() {
    ServicePrincipal servicePrincipalProto = ServicePrincipal.newBuilder().setName("GIT_SYNC_SERVICE").build();
    io.harness.security.Principal principalProto =
        io.harness.security.Principal.newBuilder().setServicePrincipal(servicePrincipalProto).build();
    Principal principal = PrincipalProtoMapper.toPrincipalDTO("accountId", principalProto);
    assertThat(principal).isNotNull();
    assertThat(principal.getType()).isEqualTo(SERVICE);
    io.harness.security.dto.ServicePrincipal servicePrincipal = (io.harness.security.dto.ServicePrincipal) principal;
    assertThat(servicePrincipal.getName()).isEqualTo("GIT_SYNC_SERVICE");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testPrincipalMapperForServiceAccountPrincipal() {
    io.harness.security.ServiceAccountPrincipal serviceAccountPrincipalProto =
        ServiceAccountPrincipal.newBuilder()
            .setName(StringValue.of("SERVICE_ACCOUNT"))
            .setEmail(StringValue.of("email"))
            .setUserName(StringValue.of("service account username"))
            .build();
    io.harness.security.Principal principalProto =
        io.harness.security.Principal.newBuilder().setServiceAccountPrincipal(serviceAccountPrincipalProto).build();
    Principal principal = PrincipalProtoMapper.toPrincipalDTO("accountId", principalProto);
    assertThat(principal).isNotNull();
    assertThat(principal.getType()).isEqualTo(SERVICE_ACCOUNT);
    io.harness.security.dto.ServiceAccountPrincipal serviceAccountPrincipal =
        (io.harness.security.dto.ServiceAccountPrincipal) principal;
    assertThat(serviceAccountPrincipal.getAccountId()).isEqualTo("accountId");
    assertThat(serviceAccountPrincipal.getEmail()).isEqualTo("email");
    assertThat(serviceAccountPrincipal.getUsername()).isEqualTo("service account username");
    assertThat(serviceAccountPrincipal.getName()).isEqualTo("SERVICE_ACCOUNT");
  }
}
