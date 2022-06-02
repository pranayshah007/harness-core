/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("spotinstTrafficShiftAlbSetupElement")
@OwnedBy(CDP)
@TargetModule(_957_CG_BEANS)
public class SpotinstTrafficShiftAlbSetupElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private String appId;
  private String envId;
  private String serviceId;
  private String infraMappingId;
  private ElastiGroup newElastiGroupOriginalConfig;
  private ElastiGroup oldElastiGroupOriginalConfig;
  private List<LbDetailsForAlbTrafficShift> detailsWithTargetGroups;
  private String elastigroupNamePrefix;
  private int timeoutIntervalInMin;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.SPOTINST_SERVICE_SETUP;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "spotinstTrafficShiftAlbSetupElement";
  }
}
