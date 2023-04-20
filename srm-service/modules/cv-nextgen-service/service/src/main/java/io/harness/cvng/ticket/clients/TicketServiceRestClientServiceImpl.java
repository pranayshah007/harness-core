/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.clients;

import io.harness.cvng.client.RequestExecutor;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.ticket.beans.TicketRequestDto;
import io.harness.cvng.ticket.beans.TicketResponseDto;

import com.google.inject.Inject;

public class TicketServiceRestClientServiceImpl implements TicketServiceRestClientService {
  @Inject private TicketServiceRestClient ticketServiceRestClient;
  @Inject private RequestExecutor requestExecutor;
  @Override
  public TicketResponseDto createTicket(
      ProjectPathParams projectPathParams, TicketRequestDto ticketRequestDto, String authToken) {
    return requestExecutor.execute(
        ticketServiceRestClient.createTicket(authToken, projectPathParams.getAccountIdentifier(),
            projectPathParams.getOrgIdentifier(), projectPathParams.getProjectIdentifier(), ticketRequestDto));
  }

  @Override
  public TicketResponseDto getTicket(ProjectPathParams projectPathParams, String ticketId, String authToken) {
    return requestExecutor.execute(
        ticketServiceRestClient.getTicket(authToken, ticketId, projectPathParams.getAccountIdentifier(),
            projectPathParams.getOrgIdentifier(), projectPathParams.getProjectIdentifier()));
  }
}
