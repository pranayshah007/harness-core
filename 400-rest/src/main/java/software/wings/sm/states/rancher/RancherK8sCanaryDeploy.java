/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.rancher;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.KryoSerializerWrapper;

import software.wings.api.RancherClusterElement;
import software.wings.api.k8s.K8sElement;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.k8s.K8sCanaryDeploy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
@Slf4j
public class RancherK8sCanaryDeploy extends K8sCanaryDeploy {
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private KryoSerializerWrapper kryoSerializerWrapper;
  @Inject public RancherStateHelper rancherStateHelper;

  public RancherK8sCanaryDeploy(String name) {
    super(name);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (!k8sStateHelper.isRancherInfraMapping(context)) {
      return rancherStateHelper.getInvalidInfraDefFailedResponse();
    }

    if (Objects.isNull(context.getContextElement())
        || !(context.getContextElement() instanceof RancherClusterElement)) {
      return rancherStateHelper.getResolvedClusterElementNotFoundResponse();
    }

    return super.execute(context);
  }

  @Override
  public ContextElementType getRequiredContextElementType() {
    return ContextElementType.RANCHER_K8S_CLUSTER_CRITERIA;
  }

  @Override
  public void saveK8sElement(ExecutionContext context, K8sElement k8sElement) {
    try {
      sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                     .name("k8s")
                                     .output(kryoSerializerWrapper.asDeflatedBytes(k8sElement))
                                     .build());
    } catch (InvalidRequestException e) {
      if (e.getCause() instanceof DuplicateKeyException) {
        log.warn("Skipping writing K8sElement to Sweeping Output as it might've been already written");
      } else {
        throw e;
      }
    }
  }
}
