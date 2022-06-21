/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAction;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.CI)
public enum AzureRepoPRAction implements GitAction {
  @JsonProperty("Create") CREATE("create", "Create"),
  @JsonProperty("Update") UPDATE("update", "Update"),
  @JsonProperty("Merge") MERGE("merge", "Merge");

  private final String value;
  private final String parsedValue;

  AzureRepoPRAction(String parsedValue, String value) {
    this.parsedValue = parsedValue;
    this.value = value;
  }

  @Override
  public String getParsedValue() {
    return parsedValue;
  }

  @Override
  public String getValue() {
    return value;
  }
}
