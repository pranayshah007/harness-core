/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "CVActivityLogKeys")
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CVActivityLog {
  private String uuid;
  private String cvConfigId;
  private String stateExecutionId;
  @JsonProperty(value = "timestamp") private long createdAt;
  private long lastUpdatedAt;
  private long dataCollectionMinute;
  private String log;
  private LogLevel logLevel;
  private List<Long> timestampParams;
  private String accountId;

  @JsonIgnore
  public long getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public List<Long> getTimestampParams() {
    if (timestampParams == null) {
      return Collections.emptyList();
    }
    return timestampParams;
  }

  public enum LogLevel { INFO, WARN, ERROR }
}
