/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.services;

import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.ticket.beans.TicketRequestDto;
import io.harness.cvng.ticket.beans.TicketResponseDto;
import io.harness.cvng.ticket.clients.TicketServiceRestClientService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TicketServiceImpl implements TicketService {
  @Inject TicketServiceRestClientService ticketServiceRestClientService;
  @Inject LogFeedbackService logFeedbackService;
  @Override
  public TicketResponseDto createTicketForFeedbackId(
      ProjectPathParams projectPathParams, String feedbackId, TicketRequestDto ticketRequestDto, String authToken) {
    log.info("Creating ticket for feedbackId {}", feedbackId);
    TicketResponseDto existingTicket = getTicketForFeedbackId(feedbackId);
    Preconditions.checkState(Objects.isNull(existingTicket), "Ticket already created for feedbackId " + feedbackId);
    TicketResponseDto createdTicket =
        ticketServiceRestClientService.createTicket(projectPathParams, ticketRequestDto, authToken);
    TicketResponseDto ticketResponseDto =
        ticketServiceRestClientService.getTicket(projectPathParams, createdTicket.getId(), authToken);
    updateLogFeedbackWithTicket(feedbackId, projectPathParams, ticketResponseDto);
    log.info("Created ticket for feedbackId {} with ticketId {}", feedbackId, createdTicket.getId());
    return ticketResponseDto;
  }

  private void updateLogFeedbackWithTicket(
      String feedbackId, ProjectPathParams projectPathParams, TicketResponseDto ticketResponseDto) {
    LogFeedback logFeedback = logFeedbackService.get(feedbackId);
    LogFeedback logFeedbackWithTicket = logFeedback.toBuilder().ticket(ticketResponseDto).build();
    logFeedbackService.update(projectPathParams, feedbackId, logFeedbackWithTicket);
  }

  @Override
  public TicketResponseDto getTicketForFeedbackId(String feedbackId) {
    LogFeedback logFeedback = logFeedbackService.get(feedbackId);
    Preconditions.checkNotNull(logFeedback, "LogFeedback does not exist for feedbackId " + feedbackId);
    return logFeedback.getTicket();
  }
}
