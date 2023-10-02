/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.utils;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.beans.DockerPublicImageTagResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class RetrofitUtilsTest extends CategoryTest {
  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetErrorBodyDetails() {
    Response<DockerPublicImageTagResponse> response = Response.error(
        ResponseBody.create(MediaType.parse("application/json"),
            "{\"message\":\"httperror 404: tag 'evrb' not found\",\"errinfo\":{\"namespace\":\"library\",\"repository\":\"nginx\",\"tag\":\"evrb\"}}\n"),
        new okhttp3.Response.Builder()
            .message("message")
            .code(400)
            .protocol(Protocol.HTTP_1_1)
            .request(new Request.Builder().url("http://localhost/").build())
            .build());
    String errorMessage = RetrofitUtils.getErrorBodyDetails(response, "message");
    assertThat(errorMessage).isEqualTo("httperror 404: tag 'evrb' not found");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetErrorBodyDetailsWithoutKey() {
    Response<DockerPublicImageTagResponse> response =
        Response.error(ResponseBody.create(MediaType.parse("application/json"),
                           "{\"errinfo\":{\"namespace\":\"library\",\"repository\":\"nginx\",\"tag\":\"evrb\"}}\n"),
            new okhttp3.Response.Builder()
                .message("message")
                .code(400)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .build());
    String errorMessage = RetrofitUtils.getErrorBodyDetails(response, "message");
    assertThat(errorMessage).isEqualTo("message");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGetErrorBodyDetailsWithEmptyErrorBody() {
    Response<DockerPublicImageTagResponse> response =
        Response.error(ResponseBody.create(MediaType.parse("application/json"), ""),
            new okhttp3.Response.Builder()
                .message("message")
                .code(400)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .build());
    String errorMessage = RetrofitUtils.getErrorBodyDetails(response, "message");
    assertThat(errorMessage).isEqualTo("message");
  }
}
