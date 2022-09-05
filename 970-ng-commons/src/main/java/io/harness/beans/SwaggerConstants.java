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

  String SERVICE_YAML_LIST_CLASSPATH = "[Lio.harness.cdng.service.beans.ServiceYamlV2;";

  String ENVIRONMENT_YAML_LIST_CLASSPATH = "[Lio.harness.cdng.environment.yaml.EnvironmentYamlV2;";
  String JENKINS_PARAMETER_FIELD_CLASSPATH = "[Lio.harness.cdng.jenkins.jenkinsstep.JenkinsParameterField;";
  String INFRASTRUCTURE_DEFINITION_YAML_HOST_FILTER_CLASSPATH = "io.harness.cdng.infra.beans.host.HostFilter";
}
