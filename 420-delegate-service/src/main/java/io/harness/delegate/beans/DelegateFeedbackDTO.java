/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Getter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(DEL)
@Slf4j
public class DelegateFeedbackDTO {
  private String accountId;
  private String projectId;
  private String orgId;
  private String delegateName;
  private String delegateType;
  private String feedback;
}
