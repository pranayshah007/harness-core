/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.status.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.status.enums.StatusType;
import io.harness.idp.status.service.StatusInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.StatusInfo;
import io.harness.spec.server.idp.v1.model.StatusInfoRequest;
import io.harness.spec.server.idp.v1.model.StatusInfoResponse;
import io.harness.spec.server.idp.v1.model.StatusInfoV2;

import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class StatusInfoApiImplTest {
  @InjectMocks private StatusInfoApiImpl statusInfoApiImpl;
  @Mock private StatusInfoService statusInfoService;

  private static final String ACCOUNT_ID = "123";
  private String type;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetStatusByType() {
    type = StatusType.ONBOARDING.toString();
    StatusInfo statusInfo = initializeStatusInfo();
    when(statusInfoService.findByAccountIdentifierAndType(ACCOUNT_ID, type)).thenReturn(Optional.of(statusInfo));
    Response response = statusInfoApiImpl.getStatusInfoByType(type, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertNotNull(((StatusInfoResponse) response.getEntity()).getStatus());

    when(statusInfoService.findByAccountIdentifierAndType(ACCOUNT_ID, type)).thenReturn(Optional.empty());
    response = statusInfoApiImpl.getStatusInfoByType(type, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetStatusByTypeV2() {
    type = StatusType.ONBOARDING.toString();
    StatusInfoV2 statusInfov2 = initializeStatusInfoV2(type);
    when(statusInfoService.findByAccountIdentifierAndTypeV2(ACCOUNT_ID, type)).thenReturn(statusInfov2);
    Response response = statusInfoApiImpl.getStatusInfoTypeV2(type, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertNotNull(((StatusInfoV2) response.getEntity()).get(type.toLowerCase()));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetStatusByTypeV2ThrowsException() {
    type = StatusType.ONBOARDING.toString();
    when(statusInfoService.findByAccountIdentifierAndTypeV2(ACCOUNT_ID, type)).thenThrow(InvalidRequestException.class);
    Response response = statusInfoApiImpl.getStatusInfoTypeV2(type, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveStatusInfoByType() {
    type = StatusType.ONBOARDING.toString();
    StatusInfo statusInfo = initializeStatusInfo();
    when(statusInfoService.save(statusInfo, ACCOUNT_ID, type)).thenReturn(statusInfo);
    StatusInfoRequest statusInfoRequest = new StatusInfoRequest();
    statusInfoRequest.setStatus(statusInfo);
    Response response = statusInfoApiImpl.saveStatusInfoByType(type, statusInfoRequest, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertNotNull(((StatusInfoResponse) response.getEntity()).getStatus());
  }

  StatusInfo initializeStatusInfo() {
    StatusInfo statusInfo = new StatusInfo();
    statusInfo.setCurrentStatus(StatusInfo.CurrentStatusEnum.COMPLETED);
    statusInfo.setReason("completed successfully");
    statusInfo.setUpdatedAt(System.currentTimeMillis());
    return statusInfo;
  }

  StatusInfoV2 initializeStatusInfoV2(String type) {
    StatusInfoV2 statusInfoV2 = new StatusInfoV2();
    statusInfoV2.put(type.toLowerCase(), initializeStatusInfo());
    return statusInfoV2;
  }
}
