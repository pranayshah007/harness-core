/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import static io.harness.annotations.dev.HarnessTeam.*;

import io.harness.annotation.*;
import io.harness.annotations.dev.*;
import io.harness.beans.*;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.pms.sdk.core.data.*;

import software.wings.sm.states.*;

import com.fasterxml.jackson.annotation.*;
import com.google.common.collect.*;
import java.util.*;
import lombok.*;
import org.springframework.data.annotation.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDC)
@JsonTypeName("bambooBuildOutcome")
@TypeAlias("bambooBuildOutcome")
@RecasterAlias("BambooBuildOutcome")
public class BambooBuildOutcome implements Outcome {
  private DelegateMetaInfo delegateMetaInfo;
  private String projectName;
  private String planName;
  private String planUrl;
  private String buildStatus;
  private String buildUrl;
  private String buildNumber;
  private ExecutionStatus executionStatus;
  private String errorMessage;
  private Map<String, String> parameters;
  private List<FilePathAssertionEntry> filePathAssertionMap;
}
