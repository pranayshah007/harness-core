/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
// TODO this should go to yaml commons
public interface SwaggerConstants {
  String STRING_CLASSPATH = "java.lang.String";
  String LONG_CLASSPATH = "java.lang.Long";
  String INTEGER_CLASSPATH = "java.lang.Integer";
  String DOUBLE_CLASSPATH = "java.lang.Double";
  String BOOLEAN_CLASSPATH = "java.lang.Boolean";
  String STRING_LIST_CLASSPATH = "[Ljava.lang.String;";
  String STRING_MAP_CLASSPATH = "Map[String,String]";
  String JSON_NODE_CLASSPATH = "com.fasterxml.jackson.databind.JsonNode";
  String INFRASTRUCTURE_DEFINITION_YAML_NODE_LIST_CLASSPATH =
      "[Lio.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;";
  String CLUSTER_YAML_NODE_LIST_CLASSPATH = "[Lio.harness.cdng.gitops.yaml.ClusterYaml;";

  String USE_FROM_STAGE = "io.harness.cdng.service.beans.ServiceUseFromStageV2";

  String SERVICE_YAML_LIST_CLASSPATH = "[Lio.harness.cdng.service.beans.ServiceYamlV2;";

  String ENVIRONMENT_YAML_LIST_CLASSPATH = "[Lio.harness.cdng.environment.yaml.EnvironmentYamlV2;";

  String AMI_TAG_LIST_CLASSPATH = "[Lio.harness.delegate.task.artifacts.ami.AMITag;";
  String AMI_FILTER_LIST_CLASSPATH = "[Lio.harness.delegate.task.artifacts.ami.AMIFilter;";

  String FILTER_YAML_LIST_CLASSPATH = "[Lio.harness.cdng.environment.filters.FilterYaml;";
  String JENKINS_PARAMETER_FIELD_CLASSPATH = "[Lio.harness.cdng.jenkins.jenkinsstep.JenkinsParameterField;";
  String INFRASTRUCTURE_DEFINITION_YAML_HOST_FILTER_CLASSPATH = "io.harness.cdng.infra.beans.host.HostFilter";
  String INFRASTRUCTURE_DEFINITION_YAML_ELASTIGROUP_CONFIGURATION_CLASSPATH =
      "io.harness.cdng.elastigroup.ElastigroupConfiguration";
  String INSTANCES_DEFINITION_YAML_ELASTIGROUP_CONFIGURATION_CLASSPATH =
      "io.harness.cdng.elastigroup.ElastigroupInstances";

  String FILTERS_MATCHTYPE_ENUM_CLASSPATH = "io.harness.cdng.environment.filters.MatchType";
  String JENKINS_PARAMETER_FIELD_TYPE_ENUM_CLASSPATH = "io.harness.cdng.jenkins.jenkinsstep.JenkinsParameterFieldType";
  String LOAD_BALANCER_CONFIGURATION_CLASSPATH = "[Lio.harness.cdng.elastigroup.LoadBalancer;";
  String CLOUD_PROVIDER_CONFIGURATION_CLASSPATH = "io.harness.cdng.elastigroup.CloudProvider";
  String TAS_COMMAND_SCRIPT_YAML_CONFIGURATION_CLASSPATH = "io.harness.cdng.tas.TasCommandScript";
  String FAILURE_STRATEGY_CONFIG_LIST_CLASSPATH = "[Lio.harness.yaml.core.failurestrategy.FailureStrategyConfig;";
  String STAGE_WHEN_CLASSPATH = "io.harness.when.beans.StageWhenCondition";
  String STEP_WHEN_CLASSPATH = "io.harness.when.beans.StepWhenCondition";
  String GITOPS_AGENT_DETAILS_LIST_CLASSPATH = "[Lio.harness.cdng.gitops.syncstep.AgentApplicationTargets;";

  String RESIZE_STRATEGY_TAS_CLASSPATH = "io.harness.delegate.beans.pcf.TasResizeStrategyType";
}
