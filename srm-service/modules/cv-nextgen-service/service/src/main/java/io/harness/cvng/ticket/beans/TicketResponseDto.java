/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.beans;

import io.harness.cvng.ticket.entities.Ticket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketResponseDto {
  String id;
  String externalId;
  String url;
  public static TicketResponseDto fromTicket(Ticket ticket) {
    if (Objects.isNull(ticket)) {
      return null;
    } else {
      return TicketResponseDto.builder()
          .url(ticket.getUrl())
          .id(ticket.getId())
          .externalId(ticket.getExternalId())
          .build();
    }
  }
}
