/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaFunctionWithActiveVersions {
  private String functionName;
  private String functionArn;
  private String runtime;
  private String role;
  private String handler;
  private Long codeSize;
  private String description;
  private Integer timeout;
  private Integer memorySize;
  private Date lastModified;
  private String codeSha256;
  private List<String> versions;
  private String kMSKeyArn;
  private String masterArn;
  private String revisionId;
  private Map<String, String> tags;
  private List<String> aliases;
}
