/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.S3UploadNode;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class S3UploadStepVariableCreatorTest extends CategoryTest {
  @Inject S3UploadStepVariableCreator s3UploadStepVariableCreator = new S3UploadStepVariableCreator();
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("s3UploadJsonStep.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
                              .getField("stages")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("stage")
                              .getNode()
                              .getField("spec")
                              .getNode()
                              .getField("execution")
                              .getNode()
                              .getField("steps")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("step");
    // yaml input expressions
    S3UploadNode s3UploadNode = YamlUtils.read(stepField.getNode().toString(), S3UploadNode.class);
    VariableCreationResponse variablesForParentNodeV2 = s3UploadStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(), s3UploadNode);

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.stripPrefix",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.connectorRef",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.endpoint",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.bucket",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.runAsUser",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.target",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.timeout",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.region",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.description",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.sourcePath",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.name",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get(s3UploadNode.getUuid()) // step uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.type",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.identifier",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.startTs",
            "pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.endTs");

    List<String> fqnExtraPropertiesList1 = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get(s3UploadNode.getUploadToS3StepInfo().getUuid()) // step uuid
                                               .getPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList1)
        .containsOnly("pipeline.stages.s3_upload_jexl_success.spec.execution.steps.upload.spec.resources");
  }
}
