/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.approval.custom.CustomApprovalCallback;
import io.harness.pms.approval.jira.JiraApprovalCallback;
import io.harness.pms.approval.servicenow.ServiceNowApprovalCallback;
import io.harness.pms.async.plan.PartialPlanCreatorResponseData;
import io.harness.pms.async.plan.PartialPlanResponseCallback;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOValue;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsValue;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsWrapperValue;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.approval.step.ApprovalProgressData;

import com.esotericsoftware.kryo.Kryo;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineServiceKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(JiraApprovalCallback.class, 800001);
    kryo.register(PartialPlanResponseCallback.class, 800002);
    kryo.register(PartialPlanCreatorResponseData.class, 800003);
    kryo.register(ServiceNowApprovalCallback.class, 800004);
    kryo.register(PartialSchemaDTOValue.class, 800005);
    kryo.register(YamlSchemaDetailsWrapperValue.class, 800006);
    kryo.register(YamlSchemaDetailsValue.class, 800007);
    kryo.register(PartialSchemaDTOWrapperValue.class, 800008);
    kryo.register(CustomApprovalCallback.class, 800009);
    kryo.register(ApprovalProgressData.class, 10000525);
  }
}
