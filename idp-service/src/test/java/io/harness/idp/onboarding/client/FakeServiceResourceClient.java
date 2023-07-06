/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.client;

import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.artifact.ArtifactSourceYamlRequestDTO;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.service.remote.ServiceResourceClient;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FakeServiceResourceClient implements ServiceResourceClient {
  static final String ACCOUNT_IDENTIFIER = "123";
  static final String TEST_ORG_IDENTIFIER = "orgId";
  static final String TEST_PROJECT_IDENTIFIER = "projectId";
  static final String TEST_SERVICE_IDENTIFIER = "serviceId";
  static final String TEST_SERVICE_NAME = "serviceName";
  static final String TEST_SERVICE_DESC = "serviceDesc";

  @Override
  public Call<ResponseDTO<PageResponse<ServiceResponse>>> listServicesForProject(int page, int size, String accountId,
      String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers, List<String> sort) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<ServiceResponse>>> execute() {
        if (Objects.equals(accountId, ACCOUNT_IDENTIFIER) && page < 1) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ServiceResponse>builder()
                  .content(Collections.singletonList(ServiceResponse.builder()
                                                         .service(ServiceResponseDTO.builder()
                                                                      .identifier(TEST_SERVICE_IDENTIFIER)
                                                                      .accountId(ACCOUNT_IDENTIFIER)
                                                                      .orgIdentifier(TEST_ORG_IDENTIFIER)
                                                                      .projectIdentifier(TEST_PROJECT_IDENTIFIER)
                                                                      .name(TEST_SERVICE_NAME)
                                                                      .description(TEST_SERVICE_DESC)
                                                                      .tags(null)
                                                                      .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ServiceResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<ServiceResponse>>> callback) {}

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
      public Call<ResponseDTO<PageResponse<ServiceResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<ResponseDTO<List<EntityDetailProtoDTO>>> getEntityReferencesForArtifactSourceTemplate(
      String accountId, String orgId, String projectId, ArtifactSourceYamlRequestDTO artifactSourceYamlRequestDTO) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<ServiceResponse>> getService(
      String serviceIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<NGEntityTemplateResponseDTO>> getServiceRuntimeInputs(
      String serviceIdentifier, String accountId, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<PageResponse<ServiceResponse>>> getAllServicesList(String accountId, String orgIdentifier,
      String projectIdentifier, String searchTerm, int page, int size, List<String> sort) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<ServiceResponse>>> execute() {
        if (Objects.equals(accountId, ACCOUNT_IDENTIFIER) && page < 1) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ServiceResponse>builder()
                  .content(Collections.singletonList(ServiceResponse.builder()
                                                         .service(ServiceResponseDTO.builder()
                                                                      .identifier(TEST_SERVICE_IDENTIFIER)
                                                                      .accountId(ACCOUNT_IDENTIFIER)
                                                                      .orgIdentifier(TEST_ORG_IDENTIFIER)
                                                                      .projectIdentifier(TEST_PROJECT_IDENTIFIER)
                                                                      .name(TEST_SERVICE_NAME)
                                                                      .description(TEST_SERVICE_DESC)
                                                                      .tags(null)
                                                                      .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<ServiceResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<ServiceResponse>>> callback) {}

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
      public Call<ResponseDTO<PageResponse<ServiceResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }
}
