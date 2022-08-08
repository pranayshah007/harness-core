/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.pms.approval.custom.CustomApprovalCallback;
import io.harness.pms.approval.jira.JiraApprovalCallback;
import io.harness.pms.approval.servicenow.ServiceNowApprovalCallback;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadata;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.preflight.entity.PreFlightEntity;

import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PMSPipelineMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(PipelineEntity.class);
    set.add(InputSetEntity.class);
    set.add(PreFlightEntity.class);
    set.add(PipelineMetadata.class);
    set.add(PipelineMetadataV2.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("pms.approval.jira", JiraApprovalCallback.class);
    h.put("pms.approval.servicenow", ServiceNowApprovalCallback.class);
    h.put("pms.approval.custom", CustomApprovalCallback.class);
  }
}
