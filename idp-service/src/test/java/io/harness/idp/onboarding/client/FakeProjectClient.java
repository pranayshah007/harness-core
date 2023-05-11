/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.client;

import io.harness.ModuleType;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.project.remote.ProjectClient;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FakeProjectClient implements ProjectClient {
  static final String ACCOUNT_IDENTIFIER = "123";
  static final String TEST_ORG_IDENTIFIER = "orgId";
  static final String TEST_PROJECT_IDENTIFIER = "projectId";
  static final String TEST_PROJECT_NAME = "projectName";
  static final String TEST_PROJECT_DESC = "projectDesc";

  @Override
  public Call<ResponseDTO<ProjectResponse>> createProject(
      String accountIdentifier, String orgIdentifier, ProjectRequest projectDTO) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<Optional<ProjectResponse>>> getProject(
      String identifier, String accountIdentifier, String orgIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<PageResponse<ProjectResponse>>> listProject(String accountIdentifier, String orgIdentifier,
      boolean hasModule, ModuleType moduleType, String searchTerm, int page, int size, List<String> sort) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<ProjectResponse>>> execute() {
        if (Objects.equals(accountIdentifier, ACCOUNT_IDENTIFIER)) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ProjectResponse>builder()
                  .content(Collections.singletonList(ProjectResponse.builder()
                                                         .project(ProjectDTO.builder()
                                                                      .identifier(TEST_PROJECT_IDENTIFIER)
                                                                      .name(TEST_PROJECT_NAME)
                                                                      .description(TEST_PROJECT_DESC)
                                                                      .tags(null)
                                                                      .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ProjectResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<ProjectResponse>>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<ResponseDTO<PageResponse<ProjectResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<ResponseDTO<PageResponse<ProjectResponse>>> listProjects(
      String accountIdentifier, String orgIdentifier, List<String> identifiers) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<PageResponse<ProjectResponse>>> listWithMultiOrg(String accountIdentifier,
      Set<String> orgIdentifiers, boolean hasModule, List<String> identifiers, ModuleType moduleType, String searchTerm,
      int page, int size, List<String> sort) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<ProjectResponse>>> execute() {
        if (Objects.equals(accountIdentifier, ACCOUNT_IDENTIFIER)) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ProjectResponse>builder()
                  .content(Collections.singletonList(ProjectResponse.builder()
                                                         .project(ProjectDTO.builder()
                                                                      .identifier(TEST_PROJECT_IDENTIFIER)
                                                                      .orgIdentifier(TEST_ORG_IDENTIFIER)
                                                                      .name(TEST_PROJECT_NAME)
                                                                      .description(TEST_PROJECT_DESC)
                                                                      .tags(null)
                                                                      .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ProjectResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<ProjectResponse>>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<ResponseDTO<PageResponse<ProjectResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<ResponseDTO<Optional<ProjectResponse>>> updateProject(
      String identifier, String accountIdentifier, String orgIdentifier, ProjectRequest projectDTO) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<Boolean>> deleteProject(
      Long ifMatch, String identifier, String accountIdentifier, String orgIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<List<ProjectDTO>>> getProjectList(String accountIdentifier, String searchTerm) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<List<ProjectDTO>>> execute() {
        if (Objects.equals(accountIdentifier, ACCOUNT_IDENTIFIER)) {
          return Response.success(
              ResponseDTO.newResponse(Collections.singletonList(ProjectDTO.builder()
                                                                    .identifier(TEST_PROJECT_IDENTIFIER)
                                                                    .orgIdentifier(TEST_ORG_IDENTIFIER)
                                                                    .name(TEST_PROJECT_NAME)
                                                                    .description(TEST_PROJECT_DESC)
                                                                    .tags(null)
                                                                    .build())));
        } else {
          return Response.success(ResponseDTO.newResponse(Collections.emptyList()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<List<ProjectDTO>>> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<ResponseDTO<List<ProjectDTO>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<ResponseDTO<ActiveProjectsCountDTO>> getAccessibleProjectsCount(
      String accountIdentifier, long startInterval, long endInterval) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }
}
