/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateInputsRefreshHelper;
import io.harness.template.helpers.TemplateInputsValidator;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TemplateRefreshServiceImplTest extends TemplateServiceTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projId";
  private static final String TEMPLATE_IDENTIFIER = "TEMPLATE_ID";
  @InjectMocks TemplateRefreshServiceImpl templateRefreshService;
  @Mock NGTemplateService templateService;
  @Mock TemplateInputsRefreshHelper templateInputsRefreshHelper;
  @Mock TemplateInputsValidator templateInputsValidator;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRefreshAndUpdateTemplateWhenTemplateDoesnotExist() {
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> templateRefreshService.refreshAndUpdateTemplate(ACCOUNT_ID, ORG_ID, PROJECT_ID, TEMPLATE_IDENTIFIER, "1"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            String.format("Template with the Identifier %s and versionLabel %s does not exist or has been deleted",
                TEMPLATE_IDENTIFIER, "1"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldRefreshAndUpdateTemplate() {
    String yaml = "Some yaml, as actual yaml not required for test";
    String updatedYaml = readFile("stage-template.yaml");
    String stageTemplateIdentifier = "stageTemplate";
    when(templateService.get(anyString(), anyString(), anyString(), anyString(), anyString(), eq(false)))
        .thenReturn(Optional.of(TemplateEntity.builder().yaml(yaml).build()));
    when(templateInputsRefreshHelper.refreshTemplates(ACCOUNT_ID, ORG_ID, PROJECT_ID, yaml)).thenReturn(updatedYaml);

    templateRefreshService.refreshAndUpdateTemplate(ACCOUNT_ID, ORG_ID, PROJECT_ID, stageTemplateIdentifier, "1");

    ArgumentCaptor<TemplateEntity> templateEntityArgumentCaptor = ArgumentCaptor.forClass(TemplateEntity.class);
    verify(templateService)
        .updateTemplateEntity(
            templateEntityArgumentCaptor.capture(), eq(ChangeType.MODIFY), eq(false), eq("Refreshed template inputs"));
    TemplateEntity templateEntity = templateEntityArgumentCaptor.getValue();
    assertThat(templateEntity).isNotNull();
    assertThat(templateEntity.getYaml()).isEqualTo(updatedYaml);
  }
}
