/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ipallowlist.resource;

import static io.harness.NGCommonEntityConstants.DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ipallowlist.IPAllowlistResourceUtils;
import io.harness.ipallowlist.entity.IPAllowlistEntity;
import io.harness.ipallowlist.service.IPAllowlistService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.AllowedSourceType;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfig;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigRequest;
import io.harness.spec.server.ng.v1.model.IPAllowlistConfigResponse;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import java.util.List;
import javax.validation.Validator;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class IpAllowlistApiImplTest extends CategoryTest {
  @Mock private AccessControlClient accessControlClient;
  @Mock private IPAllowlistService ipAllowlistService;

  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private IPAllowlistResourceUtils ipAllowlistResourceUtil;
  private Validator validator;
  private IpAllowlistApiImpl ipAllowlistApi;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);

  private static final String IDENTIFIER = randomAlphabetic(10);
  private static final String NAME = randomAlphabetic(10);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    validator = mock(Validator.class);
    this.ipAllowlistResourceUtil = new IPAllowlistResourceUtils(validator);
    this.ipAllowlistApi = new IpAllowlistApiImpl(
        ipAllowlistService, ipAllowlistResourceUtil, accessControlClient, ngFeatureFlagHelperService);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateIpAllowlistConfig() {
    mockIPAllowlistFFTrue();
    IPAllowlistConfigRequest request = getIpAllowlistConfigRequest();
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistService.create(ipAllowlistEntity)).thenReturn(ipAllowlistEntity);

    Response result = ipAllowlistApi.createIpAllowlistConfig(request, ACCOUNT_IDENTIFIER);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(201);
    assertThat(result.getEntity()).isEqualTo(getIpAllowlistConfigResponse());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testCreateIpAllowlistConfig_FFOFF() {
    mockIPAllowlistFFFalse();
    IPAllowlistConfigRequest request = getIpAllowlistConfigRequest();
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistService.create(ipAllowlistEntity)).thenReturn(ipAllowlistEntity);
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("IP Allowlist feature is not enabled for this account.");
    ipAllowlistApi.createIpAllowlistConfig(request, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetIpAllowlistConfig() {
    mockIPAllowlistFFTrue();
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistService.get(ACCOUNT_IDENTIFIER, IDENTIFIER)).thenReturn(ipAllowlistEntity);
    Response result = ipAllowlistApi.getIpAllowlistConfig(IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getEntity()).isEqualTo(getIpAllowlistConfigResponse());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdateIpAllowlistConfig() {
    mockIPAllowlistFFTrue();
    IPAllowlistConfigRequest request = getIpAllowlistConfigRequest();
    IPAllowlistEntity ipAllowlistEntity = getIPAllowlistEntity();
    when(ipAllowlistService.update(IDENTIFIER, ipAllowlistEntity)).thenReturn(ipAllowlistEntity);
    Response result = ipAllowlistApi.updateIpAllowlistConfig(IDENTIFIER, request, ACCOUNT_IDENTIFIER);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getEntity()).isEqualTo(getIpAllowlistConfigResponse());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testUpdateIpAllowlistConfig_forDifferentIdentifier() {
    mockIPAllowlistFFTrue();
    IPAllowlistConfigRequest request = getIpAllowlistConfigRequest();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage(DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM);
    ipAllowlistApi.updateIpAllowlistConfig("identifier", request, ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDeleteIpAllowlistConfig() {
    mockIPAllowlistFFTrue();
    when(ipAllowlistService.delete(ACCOUNT_IDENTIFIER, IDENTIFIER)).thenReturn(true);
    Response result = ipAllowlistApi.deleteIpAllowlistConfig(IDENTIFIER, ACCOUNT_IDENTIFIER);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(204);
    assertThat(result.getEntity()).isEqualTo(null);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDeleteIpAllowlistConfig_noAccess() {
    mockIPAllowlistFFTrue();
    doThrow(NGAccessDeniedException.class).when(accessControlClient).checkForAccessOrThrow(any(), any(), any());
    when(ipAllowlistService.delete(ACCOUNT_IDENTIFIER, IDENTIFIER)).thenReturn(true);
    exceptionRule.expect(NGAccessDeniedException.class);
    ipAllowlistApi.deleteIpAllowlistConfig(IDENTIFIER, ACCOUNT_IDENTIFIER);
  }

  private IPAllowlistConfigRequest getIpAllowlistConfigRequest() {
    IPAllowlistConfigRequest ipAllowlistConfigRequest = new IPAllowlistConfigRequest();

    ipAllowlistConfigRequest.ipAllowlistConfig(getIpAllowlistConfig());
    return ipAllowlistConfigRequest;
  }

  private IPAllowlistConfigResponse getIpAllowlistConfigResponse() {
    IPAllowlistConfigResponse ipAllowlistConfigResponse = new IPAllowlistConfigResponse();

    ipAllowlistConfigResponse.ipAllowlistConfig(getIpAllowlistConfig());
    ipAllowlistConfigResponse.created(null);
    ipAllowlistConfigResponse.updated(null);
    return ipAllowlistConfigResponse;
  }

  private IPAllowlistConfig getIpAllowlistConfig() {
    IPAllowlistConfig ipAllowlistConfig = new IPAllowlistConfig();
    ipAllowlistConfig.identifier(IDENTIFIER);
    ipAllowlistConfig.name(NAME);
    ipAllowlistConfig.description("description");
    ipAllowlistConfig.ipAddress("1.2.3.4");

    ipAllowlistConfig.allowedSourceType(List.of(AllowedSourceType.UI));
    ipAllowlistConfig.enabled(true);
    return ipAllowlistConfig;
  }

  private IPAllowlistEntity getIPAllowlistEntity() {
    return IPAllowlistEntity.builder()
        .id(null)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .identifier(IDENTIFIER)
        .name(NAME)
        .description("description")
        .allowedSourceType(List.of(AllowedSourceType.UI))
        .enabled(true)
        .ipAddress("1.2.3.4")
        .created(null)
        .updated(null)
        .lastUpdatedBy(null)
        .createdBy(null)
        .build();
  }

  private void mockIPAllowlistFFTrue() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.PL_IP_ALLOWLIST_NG)).thenReturn(true);
  }
  private void mockIPAllowlistFFFalse() {
    when(ngFeatureFlagHelperService.isEnabled(ACCOUNT_IDENTIFIER, FeatureName.PL_IP_ALLOWLIST_NG)).thenReturn(false);
  }
}
