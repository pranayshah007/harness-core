/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.aws.s3;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsS3FetchFilesResponse implements DelegateTaskNotifyResponseData {
  Map<String, List<S3FileDetailResponse>> s3filesDetails;
  CommandExecutionStatus commandExecutionStatus;
  UnitProgressData unitProgressData;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  Map<String, Map<String, String>> keyVersionMap;
}
