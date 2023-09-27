/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ng.support;

import static io.harness.rule.OwnerRule.ASHINSABU;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.ng.support.client.CannyClient;
import io.harness.ng.support.dto.CannyBoardsResponseDTO;
import io.harness.ng.support.dto.CannyPostResponseDTO;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
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
                    + "            \"url\": \"https://ideas.harness.io/xyz/board/BOARD\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "            \"created\": \"2023-05-01T20:37:10.890Z\",\n"
                    + "            \"id\": \"abc\",\n"
                    + "            \"isPrivate\": true,\n"
                    + "            \"name\": \"Test Board - only admins can see\",\n"
                    + "            \"postCount\": 50,\n"
                    + "            \"privateComments\": true,\n"
                    + "            \"token\": \"3a45f76f-e910-45f1-8cba-2dca-453cdbf96762\",\n"
                    + "            \"url\": \"https://ideas.harness.io/xyz/board/admin-only-board\"\n"
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

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testCreatePostUserExists() throws Exception {
    // this tests the following flow - retrieve user -> user exists -> create post -> retrieve post
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response userExistsResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockUserExistsRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"user123\"}"))
            .build();
    doReturn(userExistsResponse).when(cannyClient).retrieveCannyUser(anyString());

    Request mockCreatePostRequest = new Request.Builder().url("https://canny.io/api/v1/posts/create").build();
    Response createPostResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockCreatePostRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"post123\"}"))
            .build();
    doReturn(createPostResponse).when(cannyClient).createCannyPost(anyString(), anyString(), anyString(), anyString());

    Request mockRetrievePostRequest = new Request.Builder().url("https://canny.io/api/v1/posts/retrieve").build();
    Response retrievePostResponse = new Response.Builder()
                                        .code(200)
                                        .message("OK")
                                        .protocol(Protocol.HTTP_2)
                                        .request(mockRetrievePostRequest)
                                        .body(ResponseBody.create(MediaType.parse("application/json"),
                                            "{\"id\": \"post123\", \"url\": \"https://canny.io/post/123\"}"))
                                        .build();
    doReturn(retrievePostResponse).when(cannyClient).retrieveCannyPostDetails(anyString());

    CannyPostResponseDTO expectedPostResponseDTO = CannyPostResponseDTO.builder()
                                                       .postURL("https://canny.io/post/123")
                                                       .message("Post created successfully")
                                                       .build();

    CannyPostResponseDTO postResponseDTO =
        cannyClient.createPost("test@example.com", "Test User", "Test Title", "Test Details", "board123");

    Assert.assertNotNull(postResponseDTO);
    Assert.assertEquals(expectedPostResponseDTO, postResponseDTO);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testCreatePostUserDoesNotExist() throws Exception {
    // this tests the following flow - retrieve user -> user does not exist -> create user and get id -> create post ->
    // retrieve post
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response userDoesNotExistResponse =
        new Response.Builder()
            .code(404)
            .message("Not Found")
            .protocol(Protocol.HTTP_2)
            .request(mockUserExistsRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"invalid email\"}"))
            .build();

    doReturn(userDoesNotExistResponse).when(cannyClient).retrieveCannyUser(anyString());

    Request mockCreateUserRequest = new Request.Builder().url("https://canny.io/api/v1/users/create_or_update").build();
    Response createUserResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockCreateUserRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"user123\"}"))
            .build();

    doReturn(createUserResponse).when(cannyClient).createCannyUser(anyString(), anyString(), anyString());

    Request mockCreatePostRequest = new Request.Builder().url("https://canny.io/api/v1/posts/create").build();
    Response createPostResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockCreatePostRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"post123\"}"))
            .build();

    doReturn(createPostResponse).when(cannyClient).createCannyPost(anyString(), anyString(), anyString(), anyString());

    Request mockRetrievePostRequest = new Request.Builder().url("https://canny.io/api/v1/posts/retrieve").build();
    Response retrievePostResponse = new Response.Builder()
                                        .code(200)
                                        .message("OK")
                                        .protocol(Protocol.HTTP_2)
                                        .request(mockRetrievePostRequest)
                                        .body(ResponseBody.create(MediaType.parse("application/json"),
                                            "{\"id\": \"post123\", \"url\": \"https://canny.io/post/123\"}"))
                                        .build();

    doReturn(retrievePostResponse).when(cannyClient).retrieveCannyPostDetails(anyString());

    CannyPostResponseDTO expectedPostResponseDTO = CannyPostResponseDTO.builder()
                                                       .postURL("https://canny.io/post/123")
                                                       .message("Post created successfully")
                                                       .build();

    CannyPostResponseDTO postResponseDTO =
        cannyClient.createPost("test@example.com", "Test User", "Test Title", "Test Details", "board123");

    Assert.assertNotNull(postResponseDTO);
    Assert.assertEquals(expectedPostResponseDTO, postResponseDTO);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testPostCreationRetrieveUserFail() throws Exception {
    // this tests the following flow
    // retrieve user(fails and throws exception) -> post creation throws exception (resource can return 500 now)
    doThrow(new UnexpectedException("Exception occurred while retrieving user from canny at retrieveCannyUser()"))
        .when(cannyClient)
        .retrieveCannyUser(anyString());

    try {
      cannyClient.createPost("test@example.com", "Test User", "Test Title", "Test Details", "board123");
      Assert.fail("Expected an exception to be thrown");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("Exception occurred while creating post at createPost():"));
    }
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testPostCreationCreatePostFail() throws Exception {
    // this tests the following flow
    // retrieve user(success) -> create post(fails and throws exception) -> post creation throws exception (resource can
    // return 500 now)
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("OK")
            .request(mockUserExistsRequest)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"author123\"}"))
            .build();
    doReturn(responseSuccess).when(cannyClient).retrieveCannyUser(anyString());

    doThrow(
        new UnexpectedException("Exception occurred while making createPost request to Canny at createCannyPost():"))
        .when(cannyClient)
        .createCannyPost(anyString(), anyString(), anyString(), anyString());

    try {
      cannyClient.createPost("test@test.com", "Test User", "Test Title", "Test Details", "board123");
      Assert.fail("Expected an exception to be thrown");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("Exception occurred while creating post at createPost():"));
    }
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testPostCreationRetrievePostFail() throws Exception {
    // this tests the flow
    // retrieve user(success) -> create post(success) -> retrieve post(fails and throws exception) -> post creation
    // throws exception (resource can return 500 now)
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("OK")
            .request(mockUserExistsRequest)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"author123\"}"))
            .build();
    doReturn(responseSuccess).when(cannyClient).retrieveCannyUser(anyString());

    Request mockCreatePostRequest = new Request.Builder().url("https://canny.io/api/v1/posts/create").build();
    Response createPostResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockCreatePostRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"post123\"}"))
            .build();
    doReturn(createPostResponse).when(cannyClient).createCannyPost(anyString(), anyString(), anyString(), anyString());

    doThrow(
        new UnexpectedException("Exception occurred while retrieving post from canny at retrieveCannyPostDetails()"))
        .when(cannyClient)
        .retrieveCannyPostDetails(anyString());

    try {
      cannyClient.createPost("test@test.com", "Test User", "Test Title", "Test Details", "board123");
      Assert.fail("Expected an exception to be thrown");
    } catch (Exception e) {
      Assert.assertTrue(e.getMessage().contains("Exception occurred while creating post at createPost():"));
    }
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetPostCreationAuthorIdUserExists() throws Exception {
    // this tests the following flow
    // retrieve user(success) -> user exists(success) -> get author id
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response responseSuccess =
        new Response.Builder()
            .code(200)
            .message("OK")
            .request(mockUserExistsRequest)
            .protocol(Protocol.HTTP_2)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"author123\"}"))
            .build();
    doReturn(responseSuccess).when(cannyClient).retrieveCannyUser(anyString());

    String authorId = cannyClient.getPostCreationAuthorId("test@example.com", "Test User");

    Assert.assertNotNull(authorId);
    Assert.assertEquals("author123", authorId);
  }

  @Test
  @Owner(developers = ASHINSABU)
  @Category(UnitTests.class)
  public void testGetPostCreationAuthorIdUserDoesntExist() throws Exception {
    // this tests the following flow
    // retrieve user -> user does not exist -> create user and get author id
    Request mockUserExistsRequest = new Request.Builder().url("https://canny.io/api/v1/users/retrieve").build();
    Response userDoesNotExistResponse =
        new Response.Builder()
            .code(400)
            .message("Not Found")
            .protocol(Protocol.HTTP_2)
            .request(mockUserExistsRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"error\": \"invalid email\"}"))
            .build();

    doReturn(userDoesNotExistResponse).when(cannyClient).retrieveCannyUser(anyString());

    Request mockCreateUserRequest = new Request.Builder().url("https://canny.io/api/v1/users/create_or_update").build();
    Response createUserResponse =
        new Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_2)
            .request(mockCreateUserRequest)
            .body(ResponseBody.create(MediaType.parse("application/json"), "{\"id\": \"author123\"}"))
            .build();

    doReturn(createUserResponse).when(cannyClient).createCannyUser(anyString(), anyString(), anyString());

    String authorId = cannyClient.getPostCreationAuthorId("test@test.com", "Test User");
    Assert.assertNotNull(authorId);
    Assert.assertEquals("author123", authorId);
  }
}
