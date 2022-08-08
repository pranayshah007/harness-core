/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.mergeinputset.beans;

import io.harness.walktree.visitor.response.VisitorErrorResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MergeInputSetErrorResponse extends VisitorErrorResponse {
  String identifierOfErrorSource;

  @Builder(builderMethodName = "mergeErrorBuilder")
  public MergeInputSetErrorResponse(String fieldName, String message, String identifierOfErrorSource) {
    super(fieldName, message);
    this.identifierOfErrorSource = identifierOfErrorSource;
  }
}
