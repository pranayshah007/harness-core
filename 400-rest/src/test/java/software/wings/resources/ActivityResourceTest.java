/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.Log.Builder.aLog;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMMAND_UNIT_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.LOG_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.Activity;
import software.wings.beans.Log;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.dl.WingsPersistence;
import software.wings.exception.WingsExceptionMapper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.LogService;
import software.wings.utils.ResourceTestRule;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Verifier;

/**
 * Created by peeyushaggarwal on 4/1/16.
 */
public class ActivityResourceTest extends CategoryTest {
  private static final ActivityService ACTIVITY_SERVICE = mock(ActivityService.class);
  private static final LogService LOG_SERVICE = mock(LogService.class);
  private static final DataStoreService LOG_DATA_STORE_SERVICE = mock(DataStoreService.class);
  private static final WingsPersistence WINGS_PERSISTENCE = mock(WingsPersistence.class);

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new ActivityResource(ACTIVITY_SERVICE, LOG_SERVICE, LOG_DATA_STORE_SERVICE, WINGS_PERSISTENCE))
          .type(WingsExceptionMapper.class)
          .build();

  /**
   * The constant ACTUAL_ACTIVITY.
   */
  public static final Activity ACTUAL_ACTIVITY = Activity.builder().build();
  /**
   * The constant ACTUAL_LOG.
   */
  public static final Log ACTUAL_LOG = aLog().build();

  /**
   * The Test folder.
   */
  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  /**
   * The Verifier.
   */
  @Rule
  public Verifier verifier = new Verifier() {
    @Override
    protected void verify() throws Throwable {
      verifyNoMoreInteractions(ACTIVITY_SERVICE, LOG_SERVICE);
    }
  };

  /**
   * Sets the up.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setUp() throws IOException {
    reset(ACTIVITY_SERVICE, LOG_SERVICE);
    PageResponse<Activity> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Lists.newArrayList(ACTUAL_ACTIVITY));
    pageResponse.setTotal(1l);
    when(ACTIVITY_SERVICE.list(any(PageRequest.class))).thenReturn(pageResponse);
    when(ACTIVITY_SERVICE.get(anyString(), anyString())).thenReturn(ACTUAL_ACTIVITY);
    PageResponse<Log> logPageResponse = new PageResponse<>();
    logPageResponse.setResponse(Lists.newArrayList(ACTUAL_LOG));
    logPageResponse.setTotal(1l);
    when(LOG_SERVICE.list(anyString(), any(PageRequest.class))).thenReturn(logPageResponse);
  }

  /**
   * Should list activities.
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldListActivities() {
    RestResponse<PageResponse<Activity>> restResponse =
        RESOURCES.client()
            .target("/activities?appId=" + APP_ID + "&envId=" + ENV_ID)
            .request()
            .get(new GenericType<RestResponse<PageResponse<Activity>>>() {});

    assertThat(restResponse.getResource()).isInstanceOf(PageResponse.class);
    PageRequest<Activity> expectedPageRequest = new PageRequest<>();
    expectedPageRequest.addFilter("environmentId", Operator.EQ, ENV_ID);
    expectedPageRequest.setOffset("0");
    expectedPageRequest.setLimit(ResourceConstants.DEFAULT_RUNTIME_ENTITY_PAGESIZE_STR);

    verify(ACTIVITY_SERVICE).list(expectedPageRequest);
  }

  /**
   * Should get activity.
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldGetActivity() {
    RestResponse<Activity> restResponse = RESOURCES.client()
                                              .target("/activities/" + ACTIVITY_ID + "?appId=" + APP_ID)
                                              .request()
                                              .get(new GenericType<RestResponse<Activity>>() {});

    assertThat(restResponse.getResource()).isInstanceOf(Activity.class);

    verify(ACTIVITY_SERVICE).get(ACTIVITY_ID, APP_ID);
  }

  /**
   * Should list command units.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListCommandUnits() {
    when(ACTIVITY_SERVICE.getCommandUnits(APP_ID, ACTIVITY_ID))
        .thenReturn(asList(
            CommandUnitDetails.builder().name(COMMAND_UNIT_NAME).commandUnitType(CommandUnitType.COMMAND).build()));

    RestResponse<List<CommandUnitDetails>> restResponse =
        RESOURCES.client()
            .target(format("/activities/%s/units?appId=%s", ACTIVITY_ID, APP_ID))
            .request()
            .get(new GenericType<RestResponse<List<CommandUnitDetails>>>() {});
    assertThat(restResponse.getResource()).isInstanceOf(List.class);
    assertThat(restResponse.getResource().size()).isEqualTo(1);
    assertThat(restResponse.getResource().get(0)).isInstanceOf(CommandUnitDetails.class);
    assertThat(restResponse.getResource().get(0).getCommandUnitType()).isEqualTo(CommandUnitType.COMMAND);
    verify(ACTIVITY_SERVICE).getCommandUnits(APP_ID, ACTIVITY_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListCommandUnitLogs() {
    PageResponse<Log> pageResponse = new PageResponse<>();
    pageResponse.setResponse(Lists.newArrayList(Log.Builder.aLog()
                                                    .uuid(LOG_ID)
                                                    .appId(APP_ID)
                                                    .activityId(ACTIVITY_ID)
                                                    .commandUnitName(COMMAND_UNIT_NAME)
                                                    .build()));
    pageResponse.setTotal(1l);
    when(LOG_SERVICE.list(anyString(), any(PageRequest.class))).thenReturn(pageResponse);

    RestResponse<PageResponse<Log>> restResponse =
        RESOURCES.client()
            .target(format("/activities/%s/logs?appId=%s&unitName=%s", ACTIVITY_ID, APP_ID, COMMAND_UNIT_NAME))
            .request()
            .get(new GenericType<RestResponse<PageResponse<Log>>>() {});

    assertThat(restResponse.getResource()).isInstanceOf(List.class);
    assertThat(restResponse.getResource().size()).isEqualTo(1);
    assertThat(restResponse.getResource().get(0)).isInstanceOf(Log.class);
    assertThat(restResponse.getResource().get(0).getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(restResponse.getResource().get(0).getCommandUnitName()).isEqualTo(COMMAND_UNIT_NAME);
    verify(LOG_SERVICE).list(anyString(), any(PageRequest.class));
  }

  /**
   * Should download activity log file.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDownloadActivityLogFile() throws IOException {
    when(LOG_SERVICE.exportLogs(APP_ID, ACTIVITY_ID)).thenReturn(testFolder.newFile("FILE_NAME"));
    Response response =
        RESOURCES.client().target(format("/activities/%s/all-logs?appId=%s", ACTIVITY_ID, APP_ID)).request().get();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getHeaderString("Content-Disposition")).isEqualTo("attachment; filename=FILE_NAME");
    assertThat(response.getHeaderString("Content-type")).isEqualTo("application/x-unknown");
    verify(LOG_SERVICE).exportLogs(APP_ID, ACTIVITY_ID);
  }
}
