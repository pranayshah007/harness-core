/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.entity;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("CommitsResponse")
@RecasterAlias("CommitsResponse")
public class CommitsResponse {
  @JsonProperty("id")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String id;
  @JsonProperty("tree_id") String tree_id;
  @JsonProperty("distinct") String distinct;
  @JsonProperty("message") String message;
  @JsonProperty("timestamp") String timestamp;
  @JsonProperty("url") String url;
  @JsonProperty("author") HashMap<String, String> author;
  @JsonProperty("committer") HashMap<String, String> committer;
  @JsonProperty("added") List<String> added;
  @JsonProperty("removed") List<String> removed;
  @JsonProperty("modified") List<String> modified;
}
