/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.entities;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_EMPTY)
public class CIBuildPRHook {
  private Long id;
  private String link;
  private String title;
  private String body;
  private String sourceRepo;
  private String sourceBranch;
  private String targetBranch;
  private String state;
  private List<CIBuildCommit> commits;
}
