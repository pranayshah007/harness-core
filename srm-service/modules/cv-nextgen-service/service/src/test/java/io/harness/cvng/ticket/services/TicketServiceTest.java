/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.ticket.services;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.ticket.beans.TicketRequestDto;
import io.harness.cvng.ticket.beans.TicketResponseDto;
import io.harness.cvng.ticket.clients.TicketServiceRestClientService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TicketServiceTest extends CvNextGenTestBase {
  @Inject private TicketServiceImpl ticketService;
  @Inject private TicketServiceRestClientService ticketServiceRestClientService;
  @Inject private LogFeedbackService logFeedbackService;

  private TicketServiceRestClientService spiedTicketServiceRestClientService;
  private LogFeedbackService spiedLogFeedbackService;
  private ProjectPathParams projectPathParams;
  private String feedbackId;
  private String authToken;

  private String baseUrl;
  private String ticketId;
  private String externalId;

  @Before
  public void setup() throws IllegalAccessException {
    BuilderFactory builderFactory = BuilderFactory.getDefault();
    spiedTicketServiceRestClientService = spy(ticketServiceRestClientService);
    spiedLogFeedbackService = spy(logFeedbackService);
    projectPathParams = ProjectPathParams.builder()
                            .accountIdentifier(builderFactory.getContext().getAccountId())
                            .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                            .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                            .build();
    feedbackId = UUID.randomUUID().toString();
    authToken = UUID.randomUUID().toString();
    baseUrl = UUID.randomUUID().toString();
    ticketId = UUID.randomUUID().toString();
    externalId = UUID.randomUUID().toString();
    FieldUtils.writeField(ticketService, "ticketServiceRestClientService", spiedTicketServiceRestClientService, true);
    FieldUtils.writeField(ticketService, "logFeedbackService", spiedLogFeedbackService, true);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateTicketForFeedbackId() {
    LogFeedback logFeedback = LogFeedback.builder().build();
    when(spiedLogFeedbackService.get(feedbackId)).thenReturn(logFeedback);
    doReturn(logFeedback).when(spiedLogFeedbackService).update(any(), any(), any());
    TicketRequestDto ticketRequestDto = TicketRequestDto.builder().build();
    TicketResponseDto ticketResponseDto =
        TicketResponseDto.builder().url(baseUrl).id(ticketId).externalId(externalId).build();
    doReturn(ticketResponseDto)
        .when(spiedTicketServiceRestClientService)
        .createTicket(projectPathParams, ticketRequestDto, authToken);
    doReturn(ticketResponseDto)
        .when(spiedTicketServiceRestClientService)
        .getTicket(projectPathParams, ticketResponseDto.getId(), authToken);
    TicketResponseDto createdTicketResponseDto =
        ticketService.createTicketForFeedbackId(projectPathParams, feedbackId, ticketRequestDto, authToken);

    assertThat(createdTicketResponseDto.getId()).isEqualTo(ticketResponseDto.getId());
    assertThat(createdTicketResponseDto.getExternalId()).isEqualTo(ticketResponseDto.getExternalId());
    assertThat(createdTicketResponseDto.getUrl()).isEqualTo(ticketResponseDto.getUrl());
    verify(spiedLogFeedbackService, times(1)).update(any(), any(), any());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateTicketForFeedbackId_feedbackDoesNotExist() {
    LogFeedback logFeedback = null;
    when(spiedLogFeedbackService.get(feedbackId)).thenReturn(logFeedback);
    TicketRequestDto ticketRequestDto = TicketRequestDto.builder().build();

    assertThatThrownBy(
        () -> ticketService.createTicketForFeedbackId(projectPathParams, feedbackId, ticketRequestDto, authToken))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("LogFeedback does not exist for feedbackId " + feedbackId);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateTicketForFeedbackId_ticketAlreadyExists() {
    LogFeedback logFeedback = LogFeedback.builder().ticket(TicketResponseDto.builder().build()).build();
    when(spiedLogFeedbackService.get(feedbackId)).thenReturn(logFeedback);
    TicketRequestDto ticketRequestDto = TicketRequestDto.builder().build();

    assertThatThrownBy(
        () -> ticketService.createTicketForFeedbackId(projectPathParams, feedbackId, ticketRequestDto, authToken))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Ticket already created for feedbackId " + feedbackId);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetTicketForFeedbackId_feedbackDoesNotExist() {
    LogFeedback logFeedback = null;
    when(spiedLogFeedbackService.get(feedbackId)).thenReturn(logFeedback);

    assertThatThrownBy(() -> ticketService.getTicketForFeedbackId(feedbackId))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("LogFeedback does not exist for feedbackId " + feedbackId);
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetTicketForFeedbackId() {
    LogFeedback logFeedback =
        LogFeedback.builder()
            .ticket(TicketResponseDto.builder().externalId(externalId).url(baseUrl).id(ticketId).build())
            .build();
    when(spiedLogFeedbackService.get(feedbackId)).thenReturn(logFeedback);

    TicketResponseDto ticketResponseDto = ticketService.getTicketForFeedbackId(feedbackId);
    assertThat(ticketResponseDto.getUrl()).isEqualTo(baseUrl);
    assertThat(ticketResponseDto.getId()).isEqualTo(ticketId);
    assertThat(ticketResponseDto.getExternalId()).isEqualTo(externalId);
  }
}
