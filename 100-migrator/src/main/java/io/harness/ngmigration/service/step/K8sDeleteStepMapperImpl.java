/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.cdng.k8s.DeleteManifestPathSpec;
import io.harness.cdng.k8s.DeleteResourceNameSpec;
import io.harness.cdng.k8s.DeleteResourcesWrapper;
import io.harness.cdng.k8s.K8sDeleteStepInfo;
import io.harness.cdng.k8s.K8sDeleteStepNode;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sDelete;
import software.wings.yaml.workflow.StepYaml;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class K8sDeleteStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return null;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    K8sDelete state = new K8sDelete(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    K8sDelete state = (K8sDelete) getState(stepYaml);
    K8sDeleteStepNode k8sDeleteStepNode = new K8sDeleteStepNode();
    baseSetup(stepYaml, k8sDeleteStepNode);
    K8sDeleteStepInfo k8sDeleteStepInfo =
        K8sDeleteStepInfo.infoBuilder()
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .build();

    if (isNotBlank(state.getFilePaths())) {
      DeleteManifestPathSpec spec = new DeleteManifestPathSpec();
      spec.setManifestPaths(ParameterField.createValueField(Arrays.stream(state.getFilePaths().split(","))
                                                                .filter(StringUtils::isNotBlank)
                                                                .map(String::trim)
                                                                .collect(Collectors.toList())));
      k8sDeleteStepInfo.setDeleteResources(
          DeleteResourcesWrapper.builder().type(DeleteResourcesType.ManifestPath).spec(spec).build());
    }

    if (isNotBlank(state.getResources())) {
      DeleteResourceNameSpec spec = new DeleteResourceNameSpec();
      spec.setResourceNames(ParameterField.createValueField(Arrays.stream(state.getResources().split(","))
                                                                .filter(StringUtils::isNotBlank)
                                                                .map(String::trim)
                                                                .collect(Collectors.toList())));
      k8sDeleteStepInfo.setDeleteResources(
          DeleteResourcesWrapper.builder().type(DeleteResourcesType.ResourceName).spec(spec).build());
    }

    k8sDeleteStepNode.setK8sDeleteStepInfo(k8sDeleteStepInfo);
    return k8sDeleteStepNode;
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    K8sDelete state1 = (K8sDelete) getState(stepYaml1);
    K8sDelete state2 = (K8sDelete) getState(stepYaml2);

    if (!StringUtils.equals(state1.getFilePaths(), state2.getFilePaths())) {
      return false;
    }

    if (!StringUtils.equals(state1.getResources(), state2.getResources())) {
      return false;
    }

    return true;
  }
}
