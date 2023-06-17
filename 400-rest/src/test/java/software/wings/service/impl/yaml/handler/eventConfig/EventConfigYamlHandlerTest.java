/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.eventConfig;

import static io.harness.rule.OwnerRule.MOUNIK;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.CgEventRule;
import io.harness.beans.WebHookEventConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.HarnessException;
import io.harness.rule.Owner;
import io.harness.service.EventConfigService;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class EventConfigYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private EventConfigYamlHandler yamlHandler;
  @Mock private Account account;
  @Mock private AccountService accountService;
  @Mock private YamlHelper yamlHelper;
  @Mock private EventConfigService eventConfigService;
  @Mock AppService appService;
  private final String APP_NAME = "app1";
  private final String CG_EVENT_CONFIG_NAME = "cgEventConfig1";
  private final String CG_EVENT_CONFIG_ID = "uuid1";
  private CgEventConfig cgEventConfig;
  private String validYamlFilePath =
      "Setup/Applications/" + APP_NAME + "/Event Rules/" + CG_EVENT_CONFIG_NAME + ".yaml";
  private String invalidYamlContent = "description: invalid application yaml\ntype: EVENT_RULES";
  private String validYamlContent = "eventRule:\n"
      + "  pipelineRule:\n"
      + "    allEvents: true\n"
      + "    allPipelines: false\n"
      + "    pipelineIds:\n"
      + "    - wjTVVznMQEevyXRRG960hA\n"
      + "  ruleType: PIPELINE\n"
      + "type: EVENT_RULE\n"
      + "enabled: false\n"
      + "webhookEventConfiguration:\n"
      + "  url: https://localhost:9000/";

  @Before
  public void setUp() throws IOException {
    when(accountService.get(anyString())).thenReturn(account);
    Application application =
        Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(anyString(), anyString())).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    cgEventConfig = getEventConfigSample();
    cgEventConfig.setUuid(CG_EVENT_CONFIG_ID);
    when(eventConfigService.getEventsConfig(eq(ACCOUNT_ID), eq(APP_ID), eq(CG_EVENT_CONFIG_ID)))
        .thenReturn(cgEventConfig);
    when(eventConfigService.getEventsConfigByName(eq(ACCOUNT_ID), eq(APP_ID), eq(CG_EVENT_CONFIG_NAME)))
        .thenReturn(cgEventConfig);
    when(yamlHelper.getEventConfig(eq(ACCOUNT_ID), eq(validYamlFilePath))).thenReturn(cgEventConfig);
  }

  private CgEventConfig getEventConfigSample() {
    CgEventRule eventRule = new CgEventRule();
    eventRule.setType(CgEventRule.CgRuleType.ALL);
    CgEventConfig cgEventConfig = CgEventConfig.builder()
                                      .name(CG_EVENT_CONFIG_NAME)
                                      .accountId(ACCOUNT_ID)
                                      .appId(APP_ID)
                                      .enabled(false)
                                      .rule(eventRule)
                                      .build();
    WebHookEventConfig config = new WebHookEventConfig();
    cgEventConfig.setConfig(config);
    cgEventConfig.getConfig().setUrl("url1");
    return cgEventConfig;
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testCRUDSuccess() throws IOException, HarnessException {
    // SUCCESSES
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);
    ChangeContext<CgEventConfig.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.EVENT_RULE);
    changeContext.setYamlSyncHandler(yamlHandler);
    CgEventConfig.Yaml yamlObject = (CgEventConfig.Yaml) getYaml(validYamlContent, CgEventConfig.Yaml.class);
    changeContext.setYaml(yamlObject);
    CgEventConfig savedConfig = yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    assertThat(savedConfig.getName().equals(cgEventConfig.getName()));
    assertThat(savedConfig.getAccountId().equals(cgEventConfig.getAccountId()));
    assertThat(savedConfig.getAppId().equals(cgEventConfig.getAppId()));
    CgEventConfig.Yaml yaml = yamlHandler.toYaml(savedConfig, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testCRUDFailures() throws IOException {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(invalidYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);
    ChangeContext<CgEventConfig.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.EVENT_RULE);
    changeContext.setYamlSyncHandler(yamlHandler);
    CgEventConfig.Yaml yamlObject = (CgEventConfig.Yaml) getYaml(invalidYamlContent, CgEventConfig.Yaml.class);
    changeContext.setYaml(yamlObject);
    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, Arrays.asList(changeContext)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = MOUNIK)
  @Category(UnitTests.class)
  public void testGetYaml() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(CgEventConfig.Yaml.class);
  }
}
