/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecardchecks.resources;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecardchecks.service.CheckService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckDetailsRequest;
import io.harness.spec.server.idp.v1.model.CheckListItem;
import io.harness.spec.server.idp.v1.model.CheckResponse;
import io.harness.spec.server.idp.v1.model.Rule;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(HarnessTeam.IDP)
public class ChecksApiImplTest extends CategoryTest {
  private ChecksApiImpl checksApiImpl;
  @Mock CheckService checkService;
  @Mock IdpCommonService idpCommonService;
  private static final String ACCOUNT_ID = "123";
  private static final String CHECK_ID = "readme_file_exists";
  private static final String CHECK_NAME = "Readme file exists";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    checksApiImpl = new ChecksApiImpl(checkService, idpCommonService);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetChecks() {
    when(checkService.getChecksByAccountId(any(), any(), any(), any())).thenReturn(getPageCheckEntity());
    when(idpCommonService.buildPageResponse(0, 10, 1, getCheckResponse()))
        .thenReturn(Response.ok().entity(getCheckResponse()).build());
    Response response = checksApiImpl.getChecks(true, ACCOUNT_ID, 0, 10, null, null);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCheckDetails() {
    when(checkService.getCheckDetails(ACCOUNT_ID, CHECK_ID, true)).thenReturn(getCheckDetails());
    Response response = checksApiImpl.getCheck(CHECK_ID, ACCOUNT_ID, true);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetScorecardThrowsException() {
    when(checkService.getCheckDetails(ACCOUNT_ID, CHECK_ID, false)).thenThrow(InvalidRequestException.class);
    Response response = checksApiImpl.getCheck(CHECK_ID, ACCOUNT_ID, false);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCheck() {
    doNothing().when(checkService).createCheck(any(), any());
    Response response = checksApiImpl.createCheck(new CheckDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCheckThrowsDuplicateKeyException() {
    doThrow(DuplicateKeyException.class).when(checkService).createCheck(any(), any());
    CheckDetailsRequest request = new CheckDetailsRequest();
    request.setCheckDetails(getCheckDetails());
    Response response = checksApiImpl.createCheck(request, ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateCheckThrowsException() {
    doThrow(InvalidRequestException.class).when(checkService).createCheck(any(), any());
    Response response = checksApiImpl.createCheck(new CheckDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCheck() {
    doNothing().when(checkService).deleteCustomCheck(ACCOUNT_ID, CHECK_ID, false);
    Response response = checksApiImpl.deleteCheck(CHECK_ID, ACCOUNT_ID, false);
    assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCheckThrowsException() {
    doThrow(InvalidRequestException.class).when(checkService).deleteCustomCheck(ACCOUNT_ID, CHECK_ID, false);
    Response response = checksApiImpl.deleteCheck(CHECK_ID, ACCOUNT_ID, false);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCheck() {
    doNothing().when(checkService).updateCheck(any(), any());
    Response response = checksApiImpl.updateCheck(CHECK_ID, new CheckDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCheckThrowsException() {
    doThrow(InvalidRequestException.class).when(checkService).updateCheck(any(), any());
    Response response = checksApiImpl.updateCheck(CHECK_ID, new CheckDetailsRequest(), ACCOUNT_ID);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
  }

  private Page<CheckEntity> getPageCheckEntity() {
    Rule rule = new Rule();
    rule.setDataSourceIdentifier("github");
    rule.setDataPointIdentifier("isBranchProtected");
    CheckEntity checkEntity = CheckEntity.builder()
                                  .accountIdentifier(ACCOUNT_ID)
                                  .identifier(CHECK_ID)
                                  .name(CHECK_NAME)
                                  .isCustom(true)
                                  .rules(List.of(rule))
                                  .build();
    return new PageImpl<>(List.of(checkEntity));
  }

  private List<CheckResponse> getCheckResponse() {
    List<CheckResponse> response = new ArrayList<>();
    CheckListItem checkListItem = new CheckListItem();
    checkListItem.setIdentifier(CHECK_ID);
    checkListItem.setName(CHECK_NAME);
    checkListItem.setDataSource(List.of("github"));
    checkListItem.setCustom(true);
    response.add(new CheckResponse().check(checkListItem));
    return response;
  }

  private CheckDetails getCheckDetails() {
    CheckDetails checkDetails = new CheckDetails();
    checkDetails.setName(CHECK_NAME);
    checkDetails.setIdentifier(CHECK_ID);
    checkDetails.setCustom(true);
    return checkDetails;
  }
}
