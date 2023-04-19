/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.client;

import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.organization.remote.OrganizationClient;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FakeOrganizationClient implements OrganizationClient {
  static final String ACCOUNT_IDENTIFIER = "123";
  static final String TEST_ORG_IDENTIFIER = "orgId";
  static final String TEST_ORG_NAME = "orgName";
  static final String TEST_ORG_DESC = "orgDesc";

  @Override
  public Call<ResponseDTO<OrganizationResponse>> createOrganization(
      String accountIdentifier, OrganizationRequest organizationDTO) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<Optional<OrganizationResponse>>> getOrganization(
      String identifier, String accountIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<PageResponse<OrganizationResponse>>> listOrganization(
      String accountIdentifier, List<String> identifiers, String searchTerm, int page, int size, List<String> sort) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<OrganizationResponse>>> execute() {
        if (Objects.equals(accountIdentifier, ACCOUNT_IDENTIFIER)) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<OrganizationResponse>builder()
                  .content(Collections.singletonList(OrganizationResponse.builder()
                                                         .organization(OrganizationDTO.builder()
                                                                           .identifier(TEST_ORG_IDENTIFIER)
                                                                           .name(TEST_ORG_NAME)
                                                                           .description(TEST_ORG_DESC)
                                                                           .tags(null)
                                                                           .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<OrganizationResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<OrganizationResponse>>> callback) {}

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
      public Call<ResponseDTO<PageResponse<OrganizationResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }

  @Override
  public Call<ResponseDTO<PageResponse<OrganizationResponse>>> listOrganizations(
      String accountIdentifier, List<String> identifiers) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<Optional<OrganizationResponse>>> updateOrganization(
      String identifier, String accountIdentifier, OrganizationRequest organizationDTO) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<Boolean>> deleteOrganization(Long ifMatch, String identifier, String accountIdentifier) {
    throw new UnsupportedOperationException("mocked method - provide impl when required");
  }

  @Override
  public Call<ResponseDTO<PageResponse<OrganizationResponse>>> listAllOrganizations(
      String accountIdentifier, List<String> identifiers, String searchTerm) {
    return new Call<>() {
      @Override
      public Response<ResponseDTO<PageResponse<OrganizationResponse>>> execute() {
        if (Objects.equals(accountIdentifier, ACCOUNT_IDENTIFIER)) {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<OrganizationResponse>builder()
                  .content(Collections.singletonList(OrganizationResponse.builder()
                                                         .organization(OrganizationDTO.builder()
                                                                           .identifier(TEST_ORG_IDENTIFIER)
                                                                           .name(TEST_ORG_NAME)
                                                                           .description(TEST_ORG_DESC)
                                                                           .tags(null)
                                                                           .build())
                                                         .build()))
                  .totalItems(1)
                  .build()));
        } else {
          return Response.success(ResponseDTO.newResponse(
              PageResponse.<OrganizationResponse>builder().content(Collections.emptyList()).totalItems(0).build()));
        }
      }

      @Override
      public void enqueue(Callback<ResponseDTO<PageResponse<OrganizationResponse>>> callback) {}

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
      public Call<ResponseDTO<PageResponse<OrganizationResponse>>> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    };
  }
}
