/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "DelegateConfigurationKeys")
@Value
@Builder
public class DelegateConfiguration {
  private List<String> delegateVersions;
  private Action action;
  // Since we will always have one delegateversion for account adding validUntil for all of them at once.
  private Long validUntil;
  private boolean validTillNextRelease;

  public enum Action { SELF_DESTRUCT }
}
