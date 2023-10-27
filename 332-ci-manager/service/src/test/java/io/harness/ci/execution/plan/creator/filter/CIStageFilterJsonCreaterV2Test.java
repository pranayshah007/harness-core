/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.rule.OwnerRule.SAHITHI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.K8InitializeTaskUtils;
import io.harness.ci.execution.utils.ValidationUtils;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class CIStageFilterJsonCreaterV2Test {
  @InjectMocks private CIStageFilterJsonCreatorV2 ciStageFilterJsonCreatorV2;

  @Mock private ConnectorUtils connectorUtils;
  @Mock private SimpleVisitorFactory simpleVisitorFactory;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Mock private ValidationUtils validationUtils;
  @Mock private CILicenseService ciLicenseService;

  @Mock private CodeBase ciCodeBase;

  private static final String ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";
  private static final String ORG_ID = "default";
  private static final String PROJECT_ID = "SahithiProject";

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void TestgetReferredEntities() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_secret_variable_test.yml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField("pipeline").getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlNode stage1Node = stagesNode.getNode().asArray().get(0).getField("stage").getNode();

    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .currentField(new YamlField("test", stage1Node))
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
            .build();

    YamlField stageField = stagesNode.getNode().asArray().get(0).getField("stage");

    IntegrationStageNode integrationStageNode =
        YamlUtils.read(stageField.getNode().toString(), IntegrationStageNode.class);

    Set<EntityDetailProtoDTO> result =
        ciStageFilterJsonCreatorV2.getReferredEntities(filterCreationContext, integrationStageNode);

    Map<String, String> map = new HashMap<>();
    map.put("fqn", "pipeline.stages.build.variables.buildsecr");

    EntityDetailProtoDTO resSecret =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setAccountIdentifier(StringValue.of("kmpySmUISimoRrJL6NL73w"))
                                  .setIdentifier(StringValue.of("SahithiSecret"))
                                  .putMetadata("fqn", "pipeline.stages.build.variables.buildsecr")
                                  .build())
            .setType(EntityTypeProtoEnum.SECRETS)
            .build();
    EntityDetailProtoDTO resConnector =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .setAccountIdentifier(StringValue.of("kmpySmUISimoRrJL6NL73w"))
                                  .setIdentifier(StringValue.of("SahithiK8"))
                                  .putMetadata("fqn", "pipeline.stages.build.spec.account.SahithiK8")
                                  .build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .build();

    assertThat(result).contains(resSecret);
    assertThat(result).contains(resConnector);
  }
}
