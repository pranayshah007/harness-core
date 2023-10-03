/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.support.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.ng.support.dto.CannyBoardsResponseDTO;
import io.harness.ng.support.dto.CannyPostResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CannyClient {
  private CannyConfig cannyConfig;

  public static final ObjectMapper objectMapper = new ObjectMapper();

  public OkHttpClient okHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();

  @Inject
  public CannyClient(@Named("cannyApiConfiguration") CannyConfig cannyConfig) {
    this.cannyConfig = cannyConfig;
  }

  public CannyBoardsResponseDTO getBoards() {
    try (Response response = getCannyBoardsResponse()) {
      if (!response.isSuccessful()) {
        String bodyString = (null != response.body()) ? response.body().string() : null;
        log.error("Failed to retrieve boards from Canny. Response body: {}", bodyString);
        throw new UnexpectedException("Failed to retrieve boards from Canny. Response body: " + bodyString);
      }

      JsonNode jsonResponse = objectMapper.readTree(response.body().byteStream());
      JsonNode boardsNode = jsonResponse.get(CannyClientConstants.BOARDS_NODE);
      List<CannyBoardsResponseDTO.Board> boardsList = new ArrayList<>();

      for (JsonNode boardNode : boardsNode) {
        String id = boardNode.get(CannyClientConstants.ID_NODE).asText();
        String name = boardNode.get(CannyClientConstants.NAME_NODE).asText();

        // Canny doesn't provide a way to check if a board is admin view only
        // TODO: A support ticket has been raised at Canny to add this feature, this section can be removed once this
        // is handled as this is the only admin-only board.
        if (Objects.equals(name, CannyClientConstants.ADMIN_BOARD_NAME)) {
          continue;
        }

        CannyBoardsResponseDTO.Board board = CannyBoardsResponseDTO.Board.builder().name(name).id(id).build();

        boardsList.add(board);
      }

      return CannyBoardsResponseDTO.builder().boards(boardsList).build();

    } catch (Exception e) {
      log.error("Exception occurred while fetching boards at getBoards(): {}", e);
      throw new UnexpectedException("Exception occurred while fetching boards from Canny: ", e);
    }
  }
  public CannyPostResponseDTO createPost(String emailId, String name, String title, String details, String boardId) {
    try {
      String authorId = getPostCreationAuthorId(emailId, name);

      Response response = createCannyPost(authorId, boardId, title, details);
      if (!response.isSuccessful()) {
        String responseBody = response.body().string();
        log.error("Request to canny failed trying to create post. Response body: {}", responseBody);
        throw new UnexpectedException("Request to canny failed trying to create post. Response body: " + responseBody);
      }

      String postId = objectMapper.readTree(response.body().byteStream()).get(CannyClientConstants.ID_NODE).asText();
      Response postDetailsResponse = retrieveCannyPostDetails(postId);
      if (!postDetailsResponse.isSuccessful()) {
        String responseBody = postDetailsResponse.body().string();
        log.error("Request to canny failed trying to retrieve post details. Response body: {}", responseBody);
        throw new UnexpectedException(
            "Request to canny failed trying to retrieve post details. Response body: " + responseBody);
      }
      String postUrl = objectMapper.readTree(postDetailsResponse.body().byteStream()).get("url").asText();
      return CannyPostResponseDTO.builder().postURL(postUrl).message("Post created successfully").build();

    } catch (Exception e) {
      log.error("Exception occurred while creating post at createPost()", e);
      throw new UnexpectedException("Exception occurred while creating post at createPost(): " + e.getMessage());
    }
  }

  public String getPostCreationAuthorId(String emailId, String name) {
    try {
      Response response = retrieveCannyUser(emailId);
      JsonNode jsonResponse = objectMapper.readTree(response.body().byteStream());

      // if user doesn't exist on canny(checked through email since it is a unique entity on harness), create user
      if (!response.isSuccessful() && response.code() == 400
          && CannyClientConstants.INVALID_EMAIL_ERROR.equals(
              jsonResponse.get(CannyClientConstants.ERROR_NODE).asText())) {
        // use harness emailId as unique Identifier for canny
        Response createUserResponse = createCannyUser(emailId, emailId, name);

        if (!createUserResponse.isSuccessful()) {
          String responseBody = createUserResponse.body().string();
          log.error("Request to canny failed trying to create user during getPostCreationAuthorId. Response body: {}",
              responseBody);
          throw new UnexpectedException(
              "Request to canny failed trying to create user during getPostCreationAuthorId." + responseBody);
        }

        JsonNode createUserResponseJson = objectMapper.readTree(createUserResponse.body().byteStream());
        JsonNode id = createUserResponseJson.get(CannyClientConstants.ID_NODE);

        return id.asText();

      } else {
        // user was successfully retrieved
        return jsonResponse.get(CannyClientConstants.ID_NODE).asText();
      }
    } catch (Exception e) {
      log.error("Exception occurred while retrieving user at getPostCreationAuthorId(): {}", e);
      throw new UnexpectedException(
          "Exception occurred while retrieving user at getPostCreationAuthorId(): " + e.getMessage());
    }
  }

  public Response getCannyBoardsResponse() {
    try {
      String jsonRequestBody = "{\"" + CannyClientConstants.API_KEY + "\":\"" + cannyConfig.token + "\"}";
      RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonRequestBody);
      Request request = new Request.Builder()
                            .url(CannyClientConstants.CANNY_API_BASE_URL + CannyClientConstants.BOARDS_LIST_PATH)
                            .post(requestBody)
                            .build();

      return okHttpClient.newCall(request).execute();

    } catch (Exception e) {
      log.error("Exception occurred while making call to canny to fetch boards: {}", e);
      throw new UnexpectedException("Exception occurred while making call to canny to fetch boards: ", e);
    }
  }

  public Response retrieveCannyUser(String emailId) {
    try {
      FormBody.Builder formBodyBuilder = new FormBody.Builder()
                                             .add(CannyClientConstants.API_KEY, cannyConfig.token)
                                             .add(CannyClientConstants.EMAIL_NODE, emailId);
      Request request = new Request.Builder()
                            .url(CannyClientConstants.CANNY_API_BASE_URL + CannyClientConstants.USERS_RETRIEVE_PATH)
                            .post(formBodyBuilder.build())
                            .build();
      return okHttpClient.newCall(request).execute();
    } catch (Exception e) {
      log.error("Exception occurred while retrieving user from canny at retrieveCannyUser(): {}", e);
      throw new UnexpectedException("Exception occurred while retrieving user from canny at retrieveCannyUser(): ", e);
    }
  }

  public Response createCannyUser(String userId, String email, String name) {
    try {
      FormBody.Builder formBodyBuiler = new FormBody.Builder()
                                            .add(CannyClientConstants.API_KEY, cannyConfig.token)
                                            .add(CannyClientConstants.USER_ID_NODE, userId)
                                            .add(CannyClientConstants.EMAIL_NODE, email)
                                            .add(CannyClientConstants.NAME_NODE, name);
      Request request = new Request.Builder()
                            .url(CannyClientConstants.CANNY_API_BASE_URL + CannyClientConstants.USERS_CREATE_PATH)
                            .post(formBodyBuiler.build())
                            .build();
      return okHttpClient.newCall(request).execute();
    } catch (Exception e) {
      log.error("Exception occurred while creating canny user at createCannyUser() : {}", e);
      throw new UnexpectedException("Exception occurred while creating canny user at createCannyUser() : ", e);
    }
  }

  public Response createCannyPost(String authorId, String boardId, String title, String details) {
    try {
      FormBody.Builder formBodyBuilder = new FormBody.Builder()
                                             .add(CannyClientConstants.API_KEY, cannyConfig.token)
                                             .add(CannyClientConstants.AUTHOR_ID_NODE, authorId)
                                             .add(CannyClientConstants.BOARD_ID_NODE, boardId)
                                             .add(CannyClientConstants.DETAILS_NODE, details)
                                             .add(CannyClientConstants.TITLE_NODE, title);
      Request request = new Request.Builder()
                            .url(CannyClientConstants.CANNY_API_BASE_URL + CannyClientConstants.POSTS_CREATE_PATH)
                            .post(formBodyBuilder.build())
                            .build();
      return okHttpClient.newCall(request).execute();
    } catch (Exception e) {
      log.error("Exception occurred while making request to create Canny Post(): {}", e);
      throw new UnexpectedException("Exception occurred while making request to create Canny Post(): ", e);
    }
  }

  public Response retrieveCannyPostDetails(String postId) {
    try {
      FormBody.Builder formBodyBuilder = new FormBody.Builder()
                                             .add(CannyClientConstants.API_KEY, cannyConfig.token)
                                             .add(CannyClientConstants.ID_NODE, postId);
      Request request = new Request.Builder()
                            .url(CannyClientConstants.CANNY_API_BASE_URL + CannyClientConstants.POSTS_RETRIEVE_PATH)
                            .post(formBodyBuilder.build())
                            .build();
      return okHttpClient.newCall(request).execute();
    } catch (Exception e) {
      log.error("Exception occurred while trying to retrieve post from Canny: {}", e);
      throw new UnexpectedException("Exception occurred while trying to retrieve post from Canny:", e);
    }
  }
}
