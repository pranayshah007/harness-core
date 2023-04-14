/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcb.models;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.helpers.ext.gcb.models.GcbBuildStatus.QUEUED;
import static software.wings.helpers.ext.gcb.models.GcbBuildStatus.WORKING;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@Data
@Builder
@TargetModule(HarnessModule._960_API_SERVICES)
public class GcbBuildDetails {
  private String id;
  private String projectId;
  private GcbBuildStatus status;
  private String statusDetail;
  private GcbBuildSource source;
  private List<BuildStep> steps;
  private GcbResult results;
  private String createTime;
  private String startTime;
  private String finishTime;
  private String timeout;
  private List<String> images;
  private String queueTtl;
  private GcbArtifacts artifacts;
  private String logsBucket;
  private SourceProvenance sourceProvenance;
  private String buildTriggerId;
  private BuildOptions options;
  private String logUrl;
  private Map<String, String> substitutions;
  private List<String> tags;
  private List<Map<String, Object>> secrets;
  private Map<String, TimeSpan> timing;

  /**
   * @return boolean
   */
  @JsonIgnore
  public boolean isWorking() {
    return status == WORKING || status == QUEUED;
  }
}
