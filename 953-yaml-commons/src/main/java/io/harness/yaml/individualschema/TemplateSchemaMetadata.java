/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import io.harness.data.structure.EmptyPredicate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateSchemaMetadata extends IndividualSchemaMetadata {
  private String nodeGroup; // Group of the node template. eg; Step template, Stage template, SecretManager template
  private String nodeType; // type of the node eg: DeploymentStage_template, HttpStep_template

  // Generate unique key for the given nodeType/group. This key will be used to store the calculated results
  // in the map and for lookup.
  @Override
  String generateSchemaKey() {
    return nodeGroup + (EmptyPredicate.isEmpty(nodeType) ? "" : "/" + nodeType);
  }
}
