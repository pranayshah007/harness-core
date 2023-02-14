/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import static software.wings.delegatetasks.cv.CVConstants.ML_RECORDS_TTL_MONTHS;

import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by rsingh on 08/30/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil", "values", "deeplinkMetadata", "deeplinkUrl"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "NewRelicMetricDataRecordKeys")
public class NewRelicMetricDataRecord {
  private String uuid;
  @NotNull @SchemaIgnore protected String appId;
  private EmbeddedUser createdBy;
  private long createdAt;

  private EmbeddedUser lastUpdatedBy;
  @NotNull private long lastUpdatedAt;

  /**
   * TODO: Add isDeleted boolean field to enable soft delete. @swagat
   */

  @JsonIgnore @SchemaIgnore private transient String entityYamlPath;

  public static String DEFAULT_GROUP_NAME = "default";

  @NotEmpty private DelegateStateType stateType;

  @NotEmpty private String name;

  private String workflowId;

  private String workflowExecutionId;

  private String serviceId;

  private String cvConfigId;

  private String stateExecutionId;

  @NotEmpty private long timeStamp;

  private int dataCollectionMinute;

  private String host;

  private ClusterLevel level;

  private String tag;

  private String groupName = DEFAULT_GROUP_NAME;

  private Map<String, Double> values = new HashMap<>();

  private Map<String, String> deeplinkMetadata = new HashMap<>();

  private transient Map<String, String> deeplinkUrl;

  @FdIndex private String accountId;

  @JsonIgnore
  @SchemaIgnore
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
}
