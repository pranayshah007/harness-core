/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.invites.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.user.AddUsersDTO;
import io.harness.ng.core.user.AddUsersResponse;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(PL)
public interface NgInviteClient {
  String INVITE_POST_SIGNUP_API = "invites/complete/";
  String INVITE_JIT = "invites/complete-jit/";
  String INVITE_ACCEPT = "invites/accept/";
  String INVITE_GET_API = "invites/invite";
  String ADD_USERS_API = "user/users";
  String USERS_AGGREGATE_API = "user";

  @GET(INVITE_POST_SIGNUP_API) Call<ResponseDTO<Boolean>> completeInvite(@Query("token") String token);

  @POST(INVITE_JIT)
  Call<ResponseDTO<Boolean>> completeUserCreationForJIT(
      @Query("email") String email, @Query(ACCOUNT_KEY) String accountIdentifier);

  @GET(INVITE_ACCEPT) Call<ResponseDTO<InviteAcceptResponse>> accept(@Query("token") String token);

  @GET(INVITE_GET_API) Call<ResponseDTO<InviteDTO>> getInviteWithToken(@Query("jwttoken") String token);

  @POST(ADD_USERS_API)
  Call<ResponseDTO<AddUsersResponse>> addUsers(@Query(ACCOUNT_KEY) String accountIdentifier,
      @Query(ORG_KEY) String orgIdentifier, @Query(PROJECT_KEY) String projectIdentifier,
      @Body AddUsersDTO addUsersDTO);

  @PUT(USERS_AGGREGATE_API + "/update-user-metadata/{userId}")
  Call<ResponseDTO<UserMetadataDTO>> updateUserMetadata(
      @Path("userId") String userId, @Body UserMetadataDTO userMetadata);
}
