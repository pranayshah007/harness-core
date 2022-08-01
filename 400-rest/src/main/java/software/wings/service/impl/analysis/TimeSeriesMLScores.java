/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.annotation.HarnessEntity;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.sm.StateType;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by sriram_parthasarathy on 10/17/17.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "timeSeriesMLScores", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "TimeSeriesMLScoresKeys")
public class TimeSeriesMLScores extends Base implements AccountAccess {
  @NotEmpty private StateType stateType;

  @NotEmpty private String workflowId;

  @NotEmpty private String workflowExecutionId;

  @NotEmpty @FdIndex private String stateExecutionId;

  @NotEmpty private int analysisMinute;

  @FdIndex private String accountId;

  @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  private Map<String, TimeSeriesMLTxnScores> scoresMap;

  @Builder
  public TimeSeriesMLScores(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, StateType stateType, String workflowId,
      String workflowExecutionId, String stateExecutionId, int analysisMinute,
      Map<String, TimeSeriesMLTxnScores> scoresMap, String accountId) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.stateType = stateType;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.stateExecutionId = stateExecutionId;
    this.analysisMinute = analysisMinute;
    this.accountId = accountId;
    this.scoresMap = scoresMap;
  }
}
