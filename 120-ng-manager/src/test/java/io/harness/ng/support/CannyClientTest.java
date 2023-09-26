/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ng.support;

import static io.harness.rule.OwnerRule.ASHINSABU;

import static org.mockito.Mockito.*;

import io.harness.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.support.client.CannyClient;
import io.harness.ng.support.dto.CannyBoardsResponseDTO;
import io.harness.ng.support.dto.CannyPostResponseDTO;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import okhttp3.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

public class CannyClientTest extends NgManagerTestBase {
  @Spy @InjectMocks private CannyClient cannyClient;
  private AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetBoards() throws Exception {
    Request mockrequest = new Request.Builder().url("https://canny.io/api/v1/boards/list").build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .protocol(Protocol.HTTP_2)
            .message("success")
            .request(mockrequest)
            .body(ResponseBody.create(MediaType.parse("application/json"),
                "{\"boards\": [\n"
                    + "        {\n"
                    + "            \"created\": \"2023-05-01T20:37:10.890Z\",\n"
                    + "            \"id\": \"xyz\",\n"
                    + "            \"isPrivate\": true,\n"
                    + "            \"name\": \"BOARD\",\n"
                    + "            \"postCount\": 100,\n"
                    + "            \"privateComments\": false,\n"
                    + "            \"token\": \"1b22e58f-d510-eb72-2dca-453cdbf96762\",\n"
                    + "            \"url\": \"https://ideas.harness.io/admin/board/continuous-delivery\"\n"
                    + "        }]}"))
            .build();
    doReturn(responseSuccess).when(cannyClient).getCannyBoardsResponse();

    CannyBoardsResponseDTO boardsResponse = cannyClient.getBoards();
    List<CannyBoardsResponseDTO.Board> expectedBoardsList = new ArrayList<CannyBoardsResponseDTO.Board>() {
      { add(CannyBoardsResponseDTO.Board.builder().name("BOARD").id("xyz").build()); }
    };
    CannyBoardsResponseDTO expectedResponse = CannyBoardsResponseDTO.builder().boards(expectedBoardsList).build();

    Assert.assertNotNull(boardsResponse);
    Assert.assertEquals(boardsResponse, expectedResponse);
  }
}
