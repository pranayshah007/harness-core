/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.steps.CDPMSStepFilterJsonCreatorV2;
import io.harness.cdng.ssh.CommandStepInfo;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandUnitSpecType;
import io.harness.cdng.ssh.CommandUnitWrapper;
import io.harness.cdng.ssh.ScriptCommandUnitSpec;
import io.harness.cdng.ssh.TailFilePattern;
import io.harness.filestore.service.FileStoreService;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.shellscript.HarnessFileStoreSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CDPMSStepFilterJsonCreatorV2Test extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Mock FileStoreService fileStoreService;
  @Mock GenericStepPMSFilterJsonCreatorV2 genericStepPMSFilterJsonCreatorV2;

  @InjectMocks private CDPMSStepFilterJsonCreatorV2 cdpmsStepFilterJsonCreatorV2;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testHandleNode() throws IOException {
    doReturn(FilterCreationResponse.builder().referredEntities(Collections.emptyList()).build())
        .when(genericStepPMSFilterJsonCreatorV2)
        .handleNode(any(), any());
    doReturn(Optional.of(FileDTO.builder()
                             .parentIdentifier("Root")
                             .identifier("scriptsh")
                             .name("script")
                             .type(NGFileType.FILE)
                             .fileUsage(FileUsage.SCRIPT)
                             .build()))
        .when(fileStoreService)
        .getByPath(any(), any(), any(), any());
    List<CommandUnitWrapper> commandUnitWrapperList =
        List.of(CommandUnitWrapper.builder()
                    .type(CommandUnitSpecType.SCRIPT)
                    .name("Execute")
                    .spec(ScriptCommandUnitSpec.builder()
                              .tailFiles(Arrays.asList(TailFilePattern.builder()
                                                           .tailFile(ParameterField.createValueField("nohup.out"))
                                                           .tailPattern(ParameterField.createValueField("*Successfull"))
                                                           .build()))
                              .shell(ShellType.Bash)
                              .workingDirectory(ParameterField.createValueField("Dirs"))
                              .source(ShellScriptSourceWrapper.builder()
                                          .spec(HarnessFileStoreSource.builder()
                                                    .file(ParameterField.createValueField("/script.sh"))
                                                    .build())
                                          .type("Inline")
                                          .build())
                              .build())
                    .build());
    CommandStepNode commandStepNode = new CommandStepNode();
    commandStepNode.setName("commandStep");
    commandStepNode.setCommandStepInfo(CommandStepInfo.infoBuilder().commandUnits(commandUnitWrapperList).build());

    String yaml = "pipeline:\n"
        + "  identifier: Test_Pipline11\n"
        + "  variables:\n"
        + "    - name: port2\n"
        + "      type: String\n"
        + "      value: <+input>\n";
    YamlField yamlField = YamlUtils.readTree(yaml);

    FilterCreationContext filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
            .currentField(yamlField)
            .build();
    FilterCreationResponse filterCreationResponse =
        cdpmsStepFilterJsonCreatorV2.handleNode(filterCreationContext, commandStepNode);
    assertThat(filterCreationResponse.getReferredEntities().size()).isEqualTo(1);
    assertThat(filterCreationResponse.getReferredEntities().get(0).getName()).isEqualTo("");
    assertThat(filterCreationResponse.getReferredEntities().get(0).getIdentifierRef().getIdentifier().getValue())
        .isEqualTo("scriptsh");
  }
}
