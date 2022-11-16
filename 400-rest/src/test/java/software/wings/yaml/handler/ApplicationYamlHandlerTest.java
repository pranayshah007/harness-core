/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler;

import static io.harness.annotations.dev.HarnessModule._955_CG_YAML;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LALIT;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Application.Yaml;
import software.wings.beans.Event;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.rules.SetupScheduler;
import software.wings.service.impl.yaml.handler.app.ApplicationYamlHandler;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * @author rktummala on 1/9/18
 */
@SetupScheduler
@OwnedBy(CDC)
@TargetModule(_955_CG_YAML)
public class ApplicationYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject YamlHelper yamlHelper;
  @InjectMocks @Inject AppService appService;
  @InjectMocks @Inject private ApplicationYamlHandler yamlHandler;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private YamlPushService yamlPushService;
  @Mock private FeatureFlagService featureFlagService;

  private final String APP_NAME = "app1";
  private Application application;

  private String validYamlContent =
      "harnessApiVersion: '1.0'\ntype: APPLICATION\ndescription: valid application yaml\nisGitSyncEnabled: false";
  private String validYamlContentWithManualTriggerAuthorized =
      "harnessApiVersion: '1.0'\ntype: APPLICATION\ndescription: valid application yaml\nisGitSyncEnabled: false\nisManualTriggerAuthorized: true";
  private String validYamlContentWithWebHookSecretsMandated =
      "harnessApiVersion: '1.0'\ntype: APPLICATION\nareWebHookSecretsMandated: true\ndescription: valid application yaml\nisGitSyncEnabled: false";
  private String validYamlContentWithDisableTriggers =
      "harnessApiVersion: '1.0'\ntype: APPLICATION\ndescription: valid application yaml\ndisableTriggers: true\nisGitSyncEnabled: false";
  private String validYamlFilePath = "Setup/Applications/" + APP_NAME + "/Index.yaml";
  private String invalidYamlFilePath = "Setup/ApplicationsInvalid/" + APP_NAME + "/Index.yaml";

  @Before
  public void setUp() throws IOException {
    application = Application.Builder.anApplication()
                      .name(APP_NAME)
                      .uuid(APP_ID)
                      .accountId(ACCOUNT_ID)
                      .description("valid application yaml")
                      .build();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithGitSyncEnabled() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String validYamlContent =
        "harnessApiVersion: '1.0'\ntype: APPLICATION\nbranchName: master\ndescription: valid application yaml\ngitConnector: Helm Repo\nisGitSyncEnabled: true\nrepoName: test repo name";

    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Application.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION);
    changeContext.setYamlSyncHandler(yamlHandler);

    Application.Yaml yamlObject = (Application.Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);
    assertThat(yamlObject.getIsGitSyncEnabled()).isTrue();
    assertThat(yamlObject.getBranchName()).isEqualTo("master");
    assertThat(yamlObject.getGitConnector()).isEqualTo("Helm Repo");
    assertThat(yamlObject.getRepoName()).isEqualTo("test repo name");

    Application savedApplication = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareApp(application, savedApplication);

    Yaml yaml = yamlHandler.toYaml(this.application, APP_ID);
    yaml.setIsGitSyncEnabled(true);
    yaml.setBranchName("master");
    yaml.setGitConnector("Helm Repo");
    yaml.setRepoName("test repo name");

    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();
    assertThat(yamlObject.getIsGitSyncEnabled()).isTrue();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    Application applicationFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareApp(application, applicationFromGet);

    yamlHandler.delete(changeContext);

    Application application = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(application).isNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws IOException {
    testCRUDAndGet(validYamlContent);
  }

  private void testCRUDAndGet(String validYamlContent) throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Application.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION);
    changeContext.setYamlSyncHandler(yamlHandler);

    Application.Yaml yamlObject = (Application.Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    Application savedApplication = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareApp(application, savedApplication);

    Yaml yaml = yamlHandler.toYaml(this.application, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    Application applicationFromGet = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    compareApp(application, applicationFromGet);

    yamlHandler.delete(changeContext);

    Application application = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(application).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnCRUDFromYaml() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);
    gitFileChange.setSyncFromGit(true);

    ChangeContext<Application.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION);
    changeContext.setYamlSyncHandler(yamlHandler);

    Application.Yaml yamlObject = (Application.Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    Application savedApplication = yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    compareApp(application, savedApplication);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, null, savedApplication, Event.Type.CREATE, true, false);

    yamlHandler.delete(changeContext);
    verify(yamlPushService)
        .pushYamlChangeSet(application.getAccountId(), savedApplication, null, Event.Type.DELETE, true, false);

    Application application = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(application).isNull();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws IOException {
    // Invalid yaml path
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(invalidYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<Application.Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION);
    changeContext.setYamlSyncHandler(yamlHandler);

    Application.Yaml yamlObject = (Application.Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    thrown.expect(WingsException.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  private void compareApp(Application lhs, Application rhs) {
    assertThat(rhs.getName()).isEqualTo(lhs.getName());
    assertThat(rhs.getAccountId()).isEqualTo(lhs.getAccountId());
    assertThat(rhs.getDescription()).isEqualTo(lhs.getDescription());
    if (featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)) {
      assertThat(rhs.getIsManualTriggerAuthorized()).isEqualTo(lhs.getIsManualTriggerAuthorized());
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithManualTriggerAuthorizedField() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    application.setIsManualTriggerAuthorized(true);

    testCRUDAndGet(validYamlContentWithManualTriggerAuthorized);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithoutManualTriggerAuthorizedField() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);

    testCRUDAndGet(validYamlContent);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithWebHookSecretsMandatedField() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    application.setAreWebHookSecretsMandated(true);

    testCRUDAndGet(validYamlContentWithWebHookSecretsMandated);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithoutWebHookSecretsMandatedField() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);

    testCRUDAndGet(validYamlContent);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithDisableTriggers() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, ACCOUNT_ID)).thenReturn(true);
    application.setDisableTriggers(true);

    testCRUDAndGet(validYamlContentWithDisableTriggers);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithoutDisableTriggers() throws IOException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, ACCOUNT_ID)).thenReturn(true);

    testCRUDAndGet(validYamlContent);
  }
}
