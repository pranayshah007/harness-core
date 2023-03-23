/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileYamlDTO implements YamlDTO {
  private String name;
  private String identifier;
  private String content;
  private String fileUsage;
  private String projectIdentifier;
  private String orgIdentifier;
  private String rootIdentifier;
  // Higher the value the more deep the file is
  private int depth;
  // This is the path in the folder structure. e.g /service/folder1/folder2/file.yaml
  private String filePath;
}
