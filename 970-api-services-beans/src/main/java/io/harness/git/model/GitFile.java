/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import io.harness.annotation.RecasterAlias;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.git.model.GitFile")
public class GitFile implements Serializable {
  private String filePath;
  private String fileContent;
}
