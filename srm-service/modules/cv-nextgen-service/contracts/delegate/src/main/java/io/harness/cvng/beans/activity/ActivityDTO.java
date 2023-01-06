/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.activity;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "ActivityDTOKeys")
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
// TODO - TO be deleted.
@Deprecated
public abstract class ActivityDTO {
  private String accountIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  @Deprecated private String serviceIdentifier;
  @Deprecated private String environmentIdentifier;

  @NotNull private String name;
  private List<VerificationJobRuntimeDetails> verificationJobRuntimeDetails;
  @NotNull private Long activityStartTime;
  private Long activityEndTime;
  private List<String> tags;

  public abstract ActivityType getType();

  @Data
  @Builder
  public static class VerificationJobRuntimeDetails {
    String verificationJobIdentifier;
    Map<String, String> runtimeValues;
  }
}
