/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@RecasterAlias("io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo")
public class RemoteTerraformVarFileInfo
    implements RemoteTerraformFileInfo, TerraformVarFileInfo, NestedAnnotationResolver {
  GitFetchFilesConfig gitFetchFilesConfig;
  FileStoreFetchFilesConfig filestoreFetchFilesConfig;
}
