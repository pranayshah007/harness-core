/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.GcpOrganization;
import io.harness.ccm.config.GcpOrganizationService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.ValidationResult;
import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GcpOrganizationResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String gcpOrganizationUuid = "GCP_ORGANIZATION_UUID";
  private GcpOrganization gcpOrganization;

  private static GcpOrganizationService gcpOrganizationService = mock(GcpOrganizationService.class);
  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(new GcpOrganizationResource(gcpOrganizationService)).build();

  @Before
  public void setUp() {
    gcpOrganization = GcpOrganization.builder().accountId(accountId).build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldValidatePermission() {
    RESOURCES.client()
        .target(format("/gcp-organizations/validate-serviceaccount/?accountId=%s", accountId))
        .request()
        .post(
            entity(gcpOrganization, MediaType.APPLICATION_JSON), new GenericType<RestResponse<ValidationResult>>() {});
    verify(gcpOrganizationService).validate(gcpOrganization);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldSave() {
    RESOURCES.client()
        .target(format("/gcp-organizations/?accountId=%s", accountId))
        .request()
        .post(entity(gcpOrganization, MediaType.APPLICATION_JSON), new GenericType<RestResponse<GcpOrganization>>() {});
    verify(gcpOrganizationService).upsert(gcpOrganization);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldDelete() {
    RESOURCES.client()
        .target(format("/gcp-organizations/%s/?accountId=%s", gcpOrganizationUuid, accountId))
        .request()
        .delete();
    verify(gcpOrganizationService).delete(eq(accountId), eq(gcpOrganizationUuid));
  }
}
