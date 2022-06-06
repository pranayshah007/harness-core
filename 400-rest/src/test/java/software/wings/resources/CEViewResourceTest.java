/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.NIKUNJ;

import static software.wings.graphql.datafetcher.billing.CloudBillingHelper.unified;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.resources.views.CEViewResource;
import software.wings.utils.ResourceTestRule;

import java.io.IOException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CEViewResourceTest extends CategoryTest {
  private static CEViewService ceViewService = mock(CEViewService.class);
  private static ViewCustomFieldService viewCustomFieldService = mock(ViewCustomFieldService.class);
  private static CEReportScheduleService ceReportScheduleService = mock(CEReportScheduleService.class);
  private static BigQueryService bigQueryService = mock(BigQueryService.class);
  private static CloudBillingHelper cloudBillingHelper = mock(CloudBillingHelper.class);
  private static AwsAccountFieldHelper awsAccountFieldHelper = mock(AwsAccountFieldHelper.class);

  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new CEViewResource(ceViewService, ceReportScheduleService, viewCustomFieldService, bigQueryService,
              cloudBillingHelper, awsAccountFieldHelper))
          .build();

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String NAME = "VIEW_NAME";
  private final String VIEW_ID = "VIEW_ID";
  private final ViewState VIEW_STATE = ViewState.DRAFT;
  private final ViewType VIEW_TYPE = ViewType.CUSTOMER;
  private final String viewVersion = "v1";
  private final String NEW_NAME = "VIEW_NAME_NEW";
  private final String UNIFIED_TABLE = "unified";

  private CEView ceView;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    CEViewResource instance = (CEViewResource) RESOURCES.getInstances().iterator().next();
    FieldUtils.writeField(instance, "ceViewService", ceViewService, true);
    FieldUtils.writeField(instance, "cloudBillingHelper", cloudBillingHelper, true);
    ceView = CEView.builder()
                 .accountId(ACCOUNT_ID)
                 .viewState(VIEW_STATE)
                 .viewType(VIEW_TYPE)
                 .name(NAME)
                 .uuid(VIEW_ID)
                 .viewVersion(viewVersion)
                 .build();
    when(ceViewService.get(VIEW_ID)).thenReturn(ceView);
    when(ceViewService.save(ceView, false)).thenReturn(ceView);
    when(ceViewService.update(ceView)).thenReturn(ceView);
    when(cloudBillingHelper.getCloudProviderTableName(ACCOUNT_ID, unified)).thenReturn(UNIFIED_TABLE);
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void testCreateView() {
    RESOURCES.client()
        .target(format("/view?accountId=%s", ACCOUNT_ID))
        .request()
        .post(entity(ceView, MediaType.APPLICATION_JSON), new GenericType<Response>() {});
    verify(ceViewService).save(ceView, false);
    verify(ceViewService).updateTotalCost(ceView, bigQueryService.get(), UNIFIED_TABLE);
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void testCreateViewWithoutName() {
    ceView.setName("");
    Response r = RESOURCES.client()
                     .target(format("/view?accountId=%s", ACCOUNT_ID))
                     .request()
                     .post(entity(ceView, MediaType.APPLICATION_JSON), new GenericType<Response>() {});
    assertThat(r.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void testModifyView() {
    ceView.setName(NEW_NAME);
    RESOURCES.client()
        .target(format("/view?accountId=%s", ACCOUNT_ID))
        .request()
        .put(entity(ceView, MediaType.APPLICATION_JSON), new GenericType<Response>() {});
    verify(ceViewService).update(ceView);
    verify(ceViewService).updateTotalCost(ceView, bigQueryService.get(), UNIFIED_TABLE);
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void testDeleteReportSetting() {
    RESOURCES.client()
        .target(format("/view?accountId=%s&viewId=%s", ACCOUNT_ID, VIEW_ID))
        .request()
        .delete(new GenericType<Response>() {});
    verify(ceViewService).delete(VIEW_ID, ACCOUNT_ID);
  }
}
