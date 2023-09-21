/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.resources.resourcetypes.ResourceTypeService;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;
import io.harness.spec.server.accesscontrol.v1.model.Scope;

import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class PublicAccessApiImplTest extends AccessControlTestBase {
  @Mock private ResourceTypeService resourceTypeService;
  @Mock private PublicAccessService publicAccessService;
  @Mock private AccountClient accountClient;

  private PublicAccessApiImpl publicAccessApi;

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  private static final String ORG_IDENTIFIER = randomAlphabetic(10);
  private static final String PROJECT_IDENTIFIER = randomAlphabetic(10);
  private static final String RESOURCE_TYPE = "PIPELINE";
  private static final String RESOURCE_ID = randomAlphabetic(10);

  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.publicAccessApi = new PublicAccessApiImpl(resourceTypeService, publicAccessService, accountClient);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEnablePublicAccess_withDifferentAccountId() {
    PublicAccessRequest publicAccessRequest = getPublicAccessRequest();
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("harness-account and accountIdentifier should be equal");
    Response result = publicAccessApi.enablePublicAccess(publicAccessRequest, "randomAccountID");
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEnablePublicAccess_publicAccessOnAccountNotEnabled() {
    PublicAccessRequest publicAccessRequest = getPublicAccessRequest();
    final MockedStatic<CGRestUtils> mockStatic = mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(accountClient.getAccountDTO(ACCOUNT_IDENTIFIER)))
        .thenReturn(AccountDTO.builder().publicAccessEnabled(false).build());
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Public Access is not enabled for this account");
    Response result = publicAccessApi.enablePublicAccess(publicAccessRequest, ACCOUNT_IDENTIFIER);
    mockStatic.close();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testEnablePublicAccess_resourceTypeIsNotAllowedForPublicAccess() {
    PublicAccessRequest publicAccessRequest = getPublicAccessRequest();
    publicAccessRequest.setResourceType("CONNECTOR");
    final MockedStatic<CGRestUtils> mockStatic = mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(accountClient.getAccountDTO(ACCOUNT_IDENTIFIER)))
        .thenReturn(AccountDTO.builder().publicAccessEnabled(true).build());
    when(resourceTypeService.get("CONNECTOR"))
        .thenReturn(Optional.ofNullable(ResourceType.builder().isPublic(false).build()));
    exceptionRule.expect(InvalidRequestException.class);
    exceptionRule.expectMessage("Resource type does not support public access");
    Response result = publicAccessApi.enablePublicAccess(publicAccessRequest, ACCOUNT_IDENTIFIER);
    mockStatic.close();
  }

  private PublicAccessRequest getPublicAccessRequest() {
    PublicAccessRequest request = new PublicAccessRequest();
    Scope scope = new Scope();
    scope.account(ACCOUNT_IDENTIFIER);
    scope.org(ORG_IDENTIFIER);
    scope.project(PROJECT_IDENTIFIER);
    request.setResourceType(RESOURCE_TYPE);
    request.setResourceIdentifier(RESOURCE_ID);
    request.setResourceScope(scope);
    return request;
  }
}
