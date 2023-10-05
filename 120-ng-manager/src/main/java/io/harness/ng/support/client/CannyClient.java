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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
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
  public static final String CANNY_BASE_URL = "https://canny.io";
  public static final String ID_NODE = "id";
  public static final String NAME_NODE = "name";
  public static final String BOARDS_NODE = "boards";
  public static final String ADMIN_BOARD_NAME = "Test Board - only admins can see";
  public static final ObjectMapper objectMapper = new ObjectMapper();
  private final OkHttpClient okHttpClient = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();

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
      JsonNode boardsNode = jsonResponse.get(BOARDS_NODE);
      List<CannyBoardsResponseDTO.Board> boardsList = new ArrayList<>();

      for (JsonNode boardNode : boardsNode) {
        String id = boardNode.get(ID_NODE).asText();
        String name = boardNode.get(NAME_NODE).asText();

        // Canny doesn't provide a way to check if a board is admin view only
        // TODO: A support ticket has been raised at Canny to add this feature, this section can be removed once this
        // is handled as this is the only admin-only board.
        if (Objects.equals(name, ADMIN_BOARD_NAME)) {
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

  private Response getCannyBoardsResponse() {
    try {
      String jsonRequestBody = "{\"apiKey\":\"" + cannyConfig.token + "\"}";
      RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonRequestBody);
      Request request = new Request.Builder().url(CANNY_BASE_URL + "/api/v1/boards/list").post(requestBody).build();

      return okHttpClient.newCall(request).execute();

    } catch (Exception e) {
      log.error("Exception occurred while making call to canny to fetch boards: {}", e);
      throw new UnexpectedException("Exception occurred while making call to canny to fetch boards: ", e);
    }
  }
}
