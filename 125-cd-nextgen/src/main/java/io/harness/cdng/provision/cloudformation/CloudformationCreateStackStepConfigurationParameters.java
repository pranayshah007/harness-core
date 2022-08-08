/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepConfigurationParameters")
public class CloudformationCreateStackStepConfigurationParameters {
  ParameterField<String> stackName;
  CloudformationTemplateFile templateFile;
  LinkedHashMap<String, CloudformationParametersFileSpec> parameters;
  ParameterField<String> connectorRef;
  ParameterField<String> region;
  ParameterField<String> roleArn;
  ParameterField<List<String>> capabilities;
  CloudformationTags tags;
  ParameterField<List<String>> skipOnStackStatuses;
  Map<String, Object> parameterOverrides;
}
