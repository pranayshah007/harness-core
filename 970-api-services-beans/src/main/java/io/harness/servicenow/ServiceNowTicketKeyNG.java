/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceNowTicketKeyNG {
  @NotNull String url;
  @NotNull String key;
  @NotNull String ticketType;
  Map<String, String> ticketFields;

  public ServiceNowTicketKeyNG(
      @NotEmpty String baseUrl, @NotEmpty String key, @NotEmpty String ticketType, Map<String, String> ticketFields) {
    this.url = ServiceNowUtils.prepareTicketUrlFromTicketNumber(baseUrl, key, ticketType);
    this.key = key;
    this.ticketType = ticketType;
    this.ticketFields = ticketFields;
  }
}
