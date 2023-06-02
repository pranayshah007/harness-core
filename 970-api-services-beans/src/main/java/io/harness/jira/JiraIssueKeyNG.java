/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

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
public class JiraIssueKeyNG {
  @NotNull String url;
  @NotNull String key;
  Map<String, Object> ticketFields;

  public JiraIssueKeyNG(@NotEmpty String baseUrl, @NotEmpty String key, Map<String, Object> ticketFields) {
    this.url = JiraIssueUtilsNG.prepareIssueUrl(baseUrl, key);
    this.key = key;
    this.ticketFields = ticketFields;
  }
}
