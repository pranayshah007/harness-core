/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ApiCallLogDTO.class, name = "ApiCallLog")
  , @JsonSubTypes.Type(value = ExecutionLogDTO.class, name = "ExecutionLog"),
})
@OwnedBy(HarnessTeam.CV)
public abstract class CVNGLogDTO {
  private String accountId;
  private String traceableId;
  private List<CVNGLogTag> tags;
  private long createdAt;
  private long startTime;
  private long endTime;
  private TraceableType traceableType;
  public abstract CVNGLogType getType();
  public List<CVNGLogTag> getTags() {
    if (tags == null) {
      return new ArrayList<>();
    }
    return tags;
  }
}
