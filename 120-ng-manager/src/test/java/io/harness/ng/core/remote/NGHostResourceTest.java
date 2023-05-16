/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VLAD;
import static io.harness.secrets.SecretPermissions.SECRET_ACCESS_PERMISSION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.beans.HostValidationParams;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.validator.dto.HostValidationDTO;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class NGHostResourceTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String SECRET_IDENTIFIER = "secretIdentifier";

  @Mock NGHostValidationService hostValidationService;
  @Mock AccessControlClient accessControlClient;
  @InjectMocks NGHostResource ngHostResource;

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldValidateSshHosts() {
    String host1 = "host1";
    final List<String> hosts = Collections.singletonList(host1);
    final Set<String> tags = Collections.emptySet();
    HostValidationDTO hostValidationDTO =
        HostValidationDTO.builder().host(host1).status(HostValidationDTO.HostValidationStatus.SUCCESS).build();
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(SECRET_ACCESS_PERMISSION), any());
    doReturn(Collections.singletonList(hostValidationDTO))
        .when(hostValidationService)
        .validateHosts(hosts, ACCOUNT_IDENTIFIER, null, null, SECRET_IDENTIFIER, tags);

    ResponseDTO<List<HostValidationDTO>> result = ngHostResource.validateHost(ACCOUNT_IDENTIFIER, null, null,
        SECRET_IDENTIFIER,
        HostValidationParams.builder().hosts(Collections.singletonList(host1)).tags(Collections.emptyList()).build());

    assertThat(result.getData().get(0).getHost()).isEqualTo(host1);
    assertThat(result.getData().get(0).getStatus()).isEqualTo(HostValidationDTO.HostValidationStatus.SUCCESS);
    assertThat(result.getData().get(0).getError()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostsWithException() {
    final List<String> hosts = Collections.singletonList("host");
    final Set<String> tags = Collections.emptySet();
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(SECRET_ACCESS_PERMISSION), any());
    doThrow(new InvalidRequestException("Secret identifier is empty or null"))
        .when(hostValidationService)
        .validateHosts(hosts, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER, tags);
    assertThatThrownBy(
        ()
            -> ngHostResource.validateHost(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SECRET_IDENTIFIER,
                HostValidationParams.builder().hosts(hosts).tags(Collections.emptyList()).build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Secret identifier is empty or null");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateSshHostsWithNGAccessDeniedException() {
    final List<String> hosts = Collections.singletonList("host");
    doThrow(new NGAccessDeniedException("Not enough permission", USER, Collections.emptyList()))
        .when(accessControlClient)
        .checkForAccessOrThrow(any(ResourceScope.class), any(Resource.class), eq(SECRET_ACCESS_PERMISSION), any());

    assertThatThrownBy(()
                           -> ngHostResource.validateHost(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
                               SECRET_IDENTIFIER, HostValidationParams.builder().hosts(hosts).build()))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Not enough permission");
  }
}
