/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.rule.OwnerRule.AGORODETKI;

import static java.util.Collections.EMPTY_MAP;
import static java.util.Collections.singletonMap;
import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.exception.IncompleteStateException;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.workflow.StepYaml;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(HarnessTeam.CDC)
@RunWith(Parameterized.class)
public class GcbStepYamlBuilderTest extends CategoryTest {
  private final ChangeContext<StepYaml> changeContext;

  @Rule public final ExpectedException exception = ExpectedException.none();

  private final GcbStepYamlBuilder validator;
  @Mock private SettingsService settingsService;

  public GcbStepYamlBuilderTest(
      ChangeContext<StepYaml> changeContext, String message, Class<? extends Exception> expectedException) {
    this.changeContext = changeContext;
    this.validator = new GcbStepYamlBuilder();
    this.settingsService = Mockito.mock(SettingsService.class);
    if (expectedException != null) {
      exception.expect(expectedException);
      exception.expectMessage(message);
    }
  }

  @Before
  public void setUp() {
    validator.setSettingsService(settingsService);
    SettingAttribute settingAttribute = new SettingAttribute();
    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).build();
    settingAttribute.setValue(gitConfig);
    when(settingsService.getSettingAttributeByName(any(), any())).thenReturn(settingAttribute);
  }

  @Parameters
  public static Collection<Object[]> data() {
    // parameter
    Map<String, Object> propertiesWithoutGcbOptions = Collections.emptyMap();
    // parameter
    Map<String, Object> propertiesWithNullNonTemplatizedGcpConfig =
        ImmutableMap.of("templateExpressions", Collections.EMPTY_LIST, "gcbOptions", singletonMap("gcpConfigId", null));
    // parameter
    Map<String, Object> propertiesWithoutSpecSource = new HashMap<>();
    Map<String, Object> gcbOptions = new HashMap<>();
    gcbOptions.put("gcpConfigId", "id");
    gcbOptions.put("specSource", null);
    propertiesWithoutSpecSource.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithoutSpecSource.put("gcbOptions", gcbOptions);
    propertiesWithoutSpecSource.put("timeoutMillis", 1000);
    // parameter
    Map<String, Object> propertiesWithNullInlineSpec = new HashMap<>();
    Map<String, Object> inlineOptions = new HashMap<>();
    inlineOptions.put("gcpConfigId", "id");
    inlineOptions.put("specSource", "INLINE");
    inlineOptions.put("inlineSpec", null);
    propertiesWithNullInlineSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithNullInlineSpec.put("gcbOptions", inlineOptions);
    propertiesWithNullInlineSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithNullTriggerSpec = new HashMap<>();
    Map<String, Object> triggerOptions = new HashMap<>();
    triggerOptions.put("gcpConfigId", "id");
    triggerOptions.put("specSource", "TRIGGER");
    triggerOptions.put("triggerSpec", null);
    propertiesWithNullTriggerSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithNullTriggerSpec.put("gcbOptions", triggerOptions);
    propertiesWithNullTriggerSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesTriggerSpecWithoutSource = new HashMap<>();
    Map<String, Object> triggerOptionsWithoutSource = new HashMap<>();
    triggerOptionsWithoutSource.put("gcpConfigId", "id");
    triggerOptionsWithoutSource.put("specSource", "TRIGGER");
    triggerOptionsWithoutSource.put("triggerSpec", singletonMap("source", null));
    propertiesTriggerSpecWithoutSource.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesTriggerSpecWithoutSource.put("gcbOptions", triggerOptionsWithoutSource);
    propertiesTriggerSpecWithoutSource.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesTriggerSpecWithoutName = new HashMap<>();
    Map<String, Object> triggerOptionsWithoutName = new HashMap<>();
    Map<String, Object> triggerSpec = new HashMap<>();
    triggerSpec.put("source", "TAG");
    triggerSpec.put("name", null);
    triggerOptionsWithoutName.put("gcpConfigId", "id");
    triggerOptionsWithoutName.put("specSource", "TRIGGER");
    triggerOptionsWithoutName.put("triggerSpec", triggerSpec);
    propertiesTriggerSpecWithoutName.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesTriggerSpecWithoutName.put("gcbOptions", triggerOptionsWithoutName);
    propertiesTriggerSpecWithoutName.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesTriggerSpecWithoutSourceId = new HashMap<>();
    Map<String, Object> triggerOptionsWithoutSourceId = new HashMap<>();
    Map<String, Object> triggerSpecWithoutId = new HashMap<>();
    triggerSpecWithoutId.put("source", "TAG");
    triggerSpecWithoutId.put("sourceId", null);
    triggerSpecWithoutId.put("name", "triggerName");
    triggerOptionsWithoutSourceId.put("gcpConfigId", "id");
    triggerOptionsWithoutSourceId.put("specSource", "TRIGGER");
    triggerOptionsWithoutSourceId.put("triggerSpec", triggerSpecWithoutId);
    propertiesTriggerSpecWithoutSourceId.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesTriggerSpecWithoutSourceId.put("gcbOptions", triggerOptionsWithoutSourceId);
    propertiesTriggerSpecWithoutSourceId.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithNullRepoSpec = new HashMap<>();
    Map<String, Object> repoOptions = new HashMap<>();
    repoOptions.put("gcpConfigId", "id");
    repoOptions.put("specSource", "REMOTE");
    repoOptions.put("repositorySpec", null);
    propertiesWithNullRepoSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithNullRepoSpec.put("gcbOptions", repoOptions);
    propertiesWithNullRepoSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithNullGitConfig = new HashMap<>();
    Map<String, Object> repoOptionsWithoutGitConfig = new HashMap<>();
    repoOptionsWithoutGitConfig.put("gcpConfigId", "id");
    repoOptionsWithoutGitConfig.put("specSource", "REMOTE");
    repoOptionsWithoutGitConfig.put("repositorySpec", EMPTY_MAP);
    propertiesWithNullGitConfig.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithNullGitConfig.put("gcbOptions", repoOptionsWithoutGitConfig);
    propertiesWithNullGitConfig.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithoutFileSource = new HashMap<>();
    Map<String, Object> repoOptionsWithoutFileSource = new HashMap<>();
    Map<String, Object> repositorySpecWithoutFileSource = new HashMap<>();
    repositorySpecWithoutFileSource.put("gitConfigName", "config");
    repositorySpecWithoutFileSource.put("fileSource", null);
    repoOptionsWithoutFileSource.put("gcpConfigId", "id");
    repoOptionsWithoutFileSource.put("specSource", "REMOTE");
    repoOptionsWithoutFileSource.put("repositorySpec", repositorySpecWithoutFileSource);
    propertiesWithoutFileSource.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithoutFileSource.put("gcbOptions", repoOptionsWithoutFileSource);
    propertiesWithoutFileSource.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithoutFilePath = new HashMap<>();
    Map<String, Object> repoOptionsWithoutFilePath = new HashMap<>();
    Map<String, Object> repositorySpecWithoutFilePath = new HashMap<>();
    repositorySpecWithoutFilePath.put("gitConfigName", "config");
    repositorySpecWithoutFilePath.put("fileSource", "BRANCH");
    repositorySpecWithoutFilePath.put("filePath", null);
    repoOptionsWithoutFilePath.put("gcpConfigId", "id");
    repoOptionsWithoutFilePath.put("specSource", "REMOTE");
    repoOptionsWithoutFilePath.put("repositorySpec", repositorySpecWithoutFilePath);
    propertiesWithoutFilePath.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithoutFilePath.put("gcbOptions", repoOptionsWithoutFilePath);
    propertiesWithoutFilePath.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithoutSourceId = new HashMap<>();
    Map<String, Object> repoOptionsWithoutSourceId = new HashMap<>();
    Map<String, Object> repositorySpecWithoutSourceId = new HashMap<>();
    repositorySpecWithoutSourceId.put("gitConfigName", "config");
    repositorySpecWithoutSourceId.put("fileSource", "BRANCH");
    repositorySpecWithoutSourceId.put("filePath", "path");
    repositorySpecWithoutSourceId.put("sourceId", null);
    repoOptionsWithoutSourceId.put("gcpConfigId", "id");
    repoOptionsWithoutSourceId.put("specSource", "REMOTE");
    repoOptionsWithoutSourceId.put("repositorySpec", repositorySpecWithoutSourceId);
    propertiesWithoutSourceId.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithoutSourceId.put("gcbOptions", repoOptionsWithoutSourceId);
    propertiesWithoutSourceId.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithValidInlineSpec = new HashMap<>();
    Map<String, Object> inline = new HashMap<>();
    inline.put("gcpConfigId", "id");
    inline.put("specSource", "INLINE");
    inline.put("inlineSpec", "inlineSpec");
    propertiesWithValidInlineSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithValidInlineSpec.put("gcbOptions", inline);
    propertiesWithValidInlineSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithValidRemoteSpec = new HashMap<>();
    Map<String, Object> validRepoOptions = new HashMap<>();
    Map<String, Object> validRepoSpec = new HashMap<>();
    validRepoSpec.put("gitConfigName", "config");
    validRepoSpec.put("fileSource", "BRANCH");
    validRepoSpec.put("filePath", "path");
    validRepoSpec.put("sourceId", "id");
    validRepoSpec.put("repoName", "repoName");
    validRepoOptions.put("gcpConfigId", "id");
    validRepoOptions.put("specSource", "REMOTE");
    validRepoOptions.put("repositorySpec", validRepoSpec);
    propertiesWithValidRemoteSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithValidRemoteSpec.put("gcbOptions", validRepoOptions);
    propertiesWithValidRemoteSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithValidTriggerSpec = new HashMap<>();
    Map<String, Object> validTriggerOptions = new HashMap<>();
    Map<String, Object> validTriggerSpec = new HashMap<>();
    validTriggerSpec.put("source", "TAG");
    validTriggerSpec.put("sourceId", "id");
    validTriggerSpec.put("name", "triggerName");
    validTriggerOptions.put("gcpConfigId", "id");
    validTriggerOptions.put("specSource", "TRIGGER");
    validTriggerOptions.put("triggerSpec", validTriggerSpec);
    propertiesWithValidTriggerSpec.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithValidTriggerSpec.put("gcbOptions", validTriggerOptions);
    propertiesWithValidTriggerSpec.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithInvalidExpression = new HashMap<>();
    Map<String, Object> triggerOptionsExpressions = new HashMap<>();
    Map<String, Object> triggerSpecExpressions = new HashMap<>();
    triggerSpecExpressions.put("source", "TAG");
    triggerSpecExpressions.put("sourceId", "id");
    triggerSpecExpressions.put("name", "${triggerName");
    triggerOptionsExpressions.put("gcpConfigId", "id");
    triggerOptionsExpressions.put("specSource", "TRIGGER");
    triggerOptionsExpressions.put("triggerSpec", triggerSpecExpressions);
    propertiesWithInvalidExpression.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithInvalidExpression.put("gcbOptions", triggerOptionsExpressions);
    propertiesWithInvalidExpression.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithInvalidTriggerSourceIdExpression = new HashMap<>();
    Map<String, Object> triggerOptionsSourceIdExpressions = new HashMap<>();
    Map<String, Object> triggerSpecSourceIdExpressions = new HashMap<>();
    triggerSpecSourceIdExpressions.put("source", "TAG");
    triggerSpecSourceIdExpressions.put("sourceId", "${id");
    triggerSpecSourceIdExpressions.put("name", "triggerName");
    triggerOptionsSourceIdExpressions.put("gcpConfigId", "id");
    triggerOptionsSourceIdExpressions.put("specSource", "TRIGGER");
    triggerOptionsSourceIdExpressions.put("triggerSpec", triggerSpecSourceIdExpressions);
    propertiesWithInvalidTriggerSourceIdExpression.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithInvalidTriggerSourceIdExpression.put("gcbOptions", triggerOptionsSourceIdExpressions);
    propertiesWithInvalidTriggerSourceIdExpression.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithInvalidRemoteSpecNameExpression = new HashMap<>();
    Map<String, Object> invalidRepoExpressionOptions = new HashMap<>();
    Map<String, Object> invalidRepoSpecExpression = new HashMap<>();
    invalidRepoSpecExpression.put("gitConfigName", "config");
    invalidRepoSpecExpression.put("fileSource", "BRANCH");
    invalidRepoSpecExpression.put("filePath", "${path");
    invalidRepoSpecExpression.put("sourceId", "id");
    invalidRepoExpressionOptions.put("gcpConfigId", "id");
    invalidRepoExpressionOptions.put("specSource", "REMOTE");
    invalidRepoExpressionOptions.put("repositorySpec", invalidRepoSpecExpression);
    propertiesWithInvalidRemoteSpecNameExpression.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithInvalidRemoteSpecNameExpression.put("gcbOptions", invalidRepoExpressionOptions);
    propertiesWithInvalidRemoteSpecNameExpression.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithInvalidRemoteSpecSourceIdExpression = new HashMap<>();
    Map<String, Object> invalidRepoSourceIdExpressionOptions = new HashMap<>();
    Map<String, Object> invalidRepoSpecSourceIdExpression = new HashMap<>();
    invalidRepoSpecSourceIdExpression.put("gitConfigName", "config");
    invalidRepoSpecSourceIdExpression.put("fileSource", "BRANCH");
    invalidRepoSpecSourceIdExpression.put("filePath", "path");
    invalidRepoSpecSourceIdExpression.put("sourceId", "${variables.id");
    invalidRepoSourceIdExpressionOptions.put("gcpConfigId", "id");
    invalidRepoSourceIdExpressionOptions.put("specSource", "REMOTE");
    invalidRepoSourceIdExpressionOptions.put("repositorySpec", invalidRepoSpecSourceIdExpression);
    propertiesWithInvalidRemoteSpecSourceIdExpression.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithInvalidRemoteSpecSourceIdExpression.put("gcbOptions", invalidRepoSourceIdExpressionOptions);
    propertiesWithInvalidRemoteSpecSourceIdExpression.put("timeoutMillis", 1000);

    // parameter
    Map<String, Object> propertiesWithoutRemoteSpecRepoName = new HashMap<>();
    Map<String, Object> repoOptionsWithoutRepoName = new HashMap<>();
    Map<String, Object> repositorySpecWithoutRepoName = new HashMap<>();
    repositorySpecWithoutRepoName.put("gitConfigName", "config");
    repositorySpecWithoutRepoName.put("fileSource", "BRANCH");
    repositorySpecWithoutRepoName.put("filePath", "path");
    repositorySpecWithoutRepoName.put("sourceId", "id");
    repositorySpecWithoutRepoName.put("repoName", "");
    repoOptionsWithoutRepoName.put("gcpConfigId", "id");
    repoOptionsWithoutRepoName.put("specSource", "REMOTE");
    repoOptionsWithoutRepoName.put("repositorySpec", repositorySpecWithoutRepoName);
    propertiesWithoutRemoteSpecRepoName.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithoutRemoteSpecRepoName.put("gcbOptions", repoOptionsWithoutRepoName);

    // parameter
    Map<String, Object> propertiesWithRemoteSpecInvalidRepoName = new HashMap<>();
    Map<String, Object> repoOptionsWithInvalidRepoName = new HashMap<>();
    Map<String, Object> repositorySpecWithInvalidRepoName = new HashMap<>();
    repositorySpecWithInvalidRepoName.put("gitConfigName", "config");
    repositorySpecWithInvalidRepoName.put("fileSource", "BRANCH");
    repositorySpecWithInvalidRepoName.put("filePath", "path");
    repositorySpecWithInvalidRepoName.put("sourceId", "id");
    repositorySpecWithInvalidRepoName.put("repoName", "${repoName");
    repoOptionsWithInvalidRepoName.put("gcpConfigId", "id");
    repoOptionsWithInvalidRepoName.put("specSource", "REMOTE");
    repoOptionsWithInvalidRepoName.put("repositorySpec", repositorySpecWithInvalidRepoName);
    propertiesWithRemoteSpecInvalidRepoName.put("templateExpressions", Collections.EMPTY_LIST);
    propertiesWithRemoteSpecInvalidRepoName.put("gcbOptions", repoOptionsWithInvalidRepoName);

    // parameter
    Map<String, Object> propertiesWithTemplatizedRemoteSpecInvalidRepoName = new HashMap<>();
    Map<String, Object> repoOptionsInvalidRepoName = new HashMap<>();
    Map<String, Object> repositorySpecInvalidRepoName = new HashMap<>();
    Map<String, Object> templateExpressionGITConfigId = new HashMap<>();
    Map<String, Object> templateExpression = new HashMap<>();
    templateExpressionGITConfigId.put("fieldName", "gitConfigId");
    templateExpressionGITConfigId.put("expression", "${SourceRepository}");
    repositorySpecInvalidRepoName.put("gitConfigName", null);
    repositorySpecInvalidRepoName.put("fileSource", "BRANCH");
    repositorySpecInvalidRepoName.put("filePath", "path");
    repositorySpecInvalidRepoName.put("sourceId", "id");
    repositorySpecInvalidRepoName.put("repoName", "${repoName");
    repoOptionsInvalidRepoName.put("specSource", "REMOTE");
    repoOptionsInvalidRepoName.put("repositorySpec", repositorySpecInvalidRepoName);
    repoOptionsInvalidRepoName.put("gcpConfigName", "gcpConfigId");
    propertiesWithTemplatizedRemoteSpecInvalidRepoName.put(
        "templateExpressions", Arrays.asList(templateExpressionGITConfigId));
    propertiesWithTemplatizedRemoteSpecInvalidRepoName.put("gcbOptions", repoOptionsInvalidRepoName);

    return Arrays.asList(new Object[][] {
        {buildChangeContext(propertiesWithoutGcbOptions),
            "Google Cloud Build step is incomplete. Please, provide gcbOptions", IncompleteStateException.class},
        {buildChangeContext(propertiesWithNullNonTemplatizedGcpConfig),
            "\"gcpConfigName\" could not be empty or null. Please, provide gcpConfigName or add templateExpression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithoutSpecSource),
            "gcbOptions are incomplete. Please, provide specSource (INLINE, REMOTE, TRIGGER)",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithNullInlineSpec),
            "\"inlineSpec\" could not be empty or null within INLINE specSource, please provide value",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithNullTriggerSpec),
            "\"triggerSpec\" could not be empty or null within TRIGGER specSource, please provide value",
            IncompleteStateException.class},
        {buildChangeContext(propertiesTriggerSpecWithoutSource),
            "\"source\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesTriggerSpecWithoutName),
            "\"name\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesTriggerSpecWithoutSourceId),
            "\"sourceId\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesWithNullRepoSpec),
            "\"repositorySpec\" could not be empty or null within REMOTE specSource, please provide value",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithNullGitConfig),
            "\"gitConfigName\" could not be empty or null. Please, provide gitConfigName or add templateExpression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithoutFileSource),
            "\"fileSource\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesWithoutFilePath),
            "\"filePath\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesWithoutSourceId),
            "\"sourceId\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesWithValidInlineSpec), null, null},
        {buildChangeContext(propertiesWithValidRemoteSpec), null, null},
        {buildChangeContext(propertiesWithValidTriggerSpec), null, null},
        {buildChangeContext(propertiesWithInvalidExpression),
            "Invalid expression for \"name\". Please, provide value or valid expression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithInvalidTriggerSourceIdExpression),
            "Invalid expression for \"sourceId\". Please, provide value or valid expression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithInvalidRemoteSpecNameExpression),
            "Invalid expression for \"filePath\". Please, provide value or valid expression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithInvalidRemoteSpecSourceIdExpression),
            "Invalid expression for \"sourceId\". Please, provide value or valid expression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithoutRemoteSpecRepoName),
            "\"repoName\" could not be empty or null. Please, provide value", IncompleteStateException.class},
        {buildChangeContext(propertiesWithRemoteSpecInvalidRepoName),
            "Invalid expression for \"repoName\". Please, provide value or valid expression",
            IncompleteStateException.class},
        {buildChangeContext(propertiesWithTemplatizedRemoteSpecInvalidRepoName),
            "Invalid expression for \"repoName\". Please, provide value or valid expression",
            IncompleteStateException.class}});
  }

  private static ChangeContext buildChangeContext(Map<String, Object> parameters) {
    return ChangeContext.Builder.aChangeContext()
        .withYaml(StepYaml.builder().properties(parameters).build())
        .withChange(Change.Builder.aFileChange().withAccountId(WingsTestConstants.ACCOUNT_ID).build())
        .build();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldValidateStepYaml() {
    validator.validate(changeContext);
  }
}
