/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import java.util.LinkedHashMap;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Builder
@RecasterAlias("io.harness.cdng.provision.cloudformation.beans.CloudFormationCreateStackPassThroughData")
public class CloudFormationCreateStackPassThroughData implements PassThroughData {
  String templateBody;
  String templateUrl;
  String tags;
  @Builder.Default LinkedHashMap<String, List<String>> parametersFilesContent = new LinkedHashMap<>();
  @Accessors(fluent = true) boolean hasGitFiles;
  @Accessors(fluent = true) boolean hasS3Files;
}
