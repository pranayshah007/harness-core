/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.entities;

import io.harness.cvng.ticket.beans.TicketResponseDto;

import java.util.Objects;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class Ticket {
  String id;
  String externalId;
  String url;

  public static Ticket fromTicketResponseDto(TicketResponseDto ticketResponseDto) {
    if (Objects.isNull(ticketResponseDto)) {
      return null;
    } else {
      return Ticket.builder()
          .url(ticketResponseDto.getUrl())
          .id(ticketResponseDto.getId())
          .externalId(ticketResponseDto.getExternalId())
          .build();
    }
  }
}