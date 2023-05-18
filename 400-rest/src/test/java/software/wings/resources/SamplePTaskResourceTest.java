/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.rule.OwnerRule.HANTANG;

import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.example.SamplePTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.utils.ResourceTestRule;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SamplePTaskResourceTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String taskId = "TASK_ID";
  private String countryName = "COUNTRY_NAME";
  private int population = 10;
  private static SamplePTaskService samplePTaskService = mock(SamplePTaskService.class);

  @ClassRule
  public static ResourceTestRule RESOURCES =
      ResourceTestRule.builder().instance(new SamplePTaskResource(samplePTaskService)).build();

  @Before
  public void setUp() {}

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testCreate() {
    RESOURCES.client()
        .target(format("/perpetual-task/?accountId=%s&country=%s&population=%s", accountId, countryName, population))
        .request()
        .post(
            entity(new PerpetualTaskRecord(), MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(samplePTaskService).create(eq(accountId), eq(countryName), eq(population));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testUpdate() {
    RESOURCES.client()
        .target(format("/perpetual-task/?accountId=%s&taskId=%s&country=%s&population=%s", accountId, taskId,
            countryName, population))
        .request()
        .put(
            entity(new PerpetualTaskRecord(), MediaType.APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    verify(samplePTaskService).update(eq(accountId), eq(taskId), eq(countryName), eq(population));
  }
}
