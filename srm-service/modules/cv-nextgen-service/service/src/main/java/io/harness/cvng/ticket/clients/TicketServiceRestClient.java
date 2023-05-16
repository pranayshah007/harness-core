/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.clients;

import io.harness.cvng.ticket.beans.TicketRequestDto;
import io.harness.cvng.ticket.beans.TicketResponseDto;
import io.harness.security.NextGenAuthenticationFilter;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TicketServiceRestClient {
  @GET("tickets/{ticketId}")
  Call<TicketResponseDto> getTicket(@Header(NextGenAuthenticationFilter.AUTHORIZATION_HEADER) String authToken,
      @Path("ticketId") String ticketId, @Query("accountId") String accountId, @Query("orgId") String orgId,
      @Query("projectId") String projectId);

  @POST("tickets")
  Call<TicketResponseDto> createTicket(@Header(NextGenAuthenticationFilter.AUTHORIZATION_HEADER) String authToken,
      @Query("accountId") String accountId, @Query("orgId") String orgId, @Query("projectId") String projectId,
      @Body TicketRequestDto ticketRequestDto);
}
