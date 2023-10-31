/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.support.client;

public class CannyClientConstants {
  public static final String API_KEY = "apiKey";
  public static final String CANNY_BASE_URL = "https://canny.io";
  public static final String CANNY_API_BASE_URL = CANNY_BASE_URL + "/api/v1";
  public static final String BOARDS_LIST_PATH = "/boards/list";
  public static final String USERS_RETRIEVE_PATH = "/users/retrieve";
  public static final String USERS_CREATE_PATH = "/users/create_or_update";
  public static final String POSTS_CREATE_PATH = "/posts/create";
  public static final String POSTS_RETRIEVE_PATH = "/posts/retrieve";

  public static final String ID_NODE = "id";
  public static final String USER_ID_NODE = "userID";
  public static final String EMAIL_NODE = "email";
  public static final String AUTHOR_ID_NODE = "authorID";
  public static final String BOARD_ID_NODE = "boardID";
  public static final String DETAILS_NODE = "details";
  public static final String TITLE_NODE = "title";

  public static final String NAME_NODE = "name";
  public static final String BOARDS_NODE = "boards";
  public static final String ERROR_NODE = "error";

  public static final String INVALID_EMAIL_ERROR = "invalid email";
  public static final String ADMIN_BOARD_NAME = "Test Board - only admins can see";
}
