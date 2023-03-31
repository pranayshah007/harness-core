/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SettingAttribute;
import software.wings.beans.security.UserGroup;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class StepYamlBuilderTestBase extends CategoryTest {
  protected static final String ACCOUNT_ID = "ACCOUNT_ID";
  protected static final String APP_ID = "APP_ID";
  protected static final String USER_GROUPS = "userGroups";
  protected static final String USER_GROUP_NAMES = "userGroupNames";
  protected static final String APPROVAL_STATE_PARAMS = "approvalStateParams";
  protected static final String JIRA_APPROVAL_PARAMS = "jiraApprovalParams";
  protected static final String SNOW_APPROVAL_PARAMS = "serviceNowApprovalParams";
  protected static final String JIRA_CONNECTOR_ID = "jiraConnectorId";
  protected static final String JIRA_CONNECTOR_NAME = "jiraConnectorName";
  protected static final String SNOW_CONNECTOR_ID = "snowConnectorId";
  protected static final String SNOW_CONNECTOR_NAME = "snowConnectorName";
  protected static final String USER_GROUP_ID = "userGroupId";
  protected static final String USER_GROUP_NAME = "userGroupName";
  protected static final String BAMBOO_CONFIG_ID = "bambooConfigId";
  protected static final String BAMBOO_CONFIG_NAME = "bambooConfigName";
  protected static final String JENKINS_ID = "jenkinsConfigId";
  protected static final String JENKINS_NAME = "jenkinsConfigName";
  protected static final String SERVICE_NOW_CREATE_UPDATE_PARAMS = "serviceNowCreateUpdateParams";
  protected static final String SSH_KEY_REF = "sshKeyRef";
  protected static final String SSH_KEY_REF_NAME = "sshKeyRefName";
  protected static final String CONNECTION_ATTRIBUTES = "connectionAttributes";
  protected static final String CONNECTION_ATTRIBUTE_NAME = "connectionAttributeName";
  protected static final String GCB_OPTIONS = "gcbOptions";
  protected static final String GCP_CONFIG_ID = "gcpConfigId";
  protected static final String GCP_CONFIG_NAME = "gcpConfigName";
  protected static final String REPOSITORY_SPEC = "repositorySpec";
  protected static final String GIT_CONFIG_ID = "gitConfigId";
  protected static final String GIT_CONFIG_NAME = "gitConfigName";
  protected static final String TEMPLATE_EXPRESSIONS = "templateExpressions";

  @Mock protected SettingsService settingsService;
  @Mock protected UserGroupService userGroupService;

  @Before
  public void setup() {
    initMocks(this);
    when(userGroupService.fetchUserGroupByName(eq(ACCOUNT_ID), any())).thenAnswer(invocation -> {
      String s = invocation.getArgument(1, String.class);
      return UserGroup.builder().uuid(USER_GROUP_ID + s.charAt(s.length() - 1)).name(s).build();
    });
    when(userGroupService.get(anyString())).thenAnswer(invocation -> {
      String s = invocation.getArgument(0, String.class);
      return UserGroup.builder().uuid(s).name(USER_GROUP_NAME + s.charAt(s.length() - 1)).build();
    });

    SettingAttribute jiraAttribute =
        aSettingAttribute().withName(JIRA_CONNECTOR_NAME).withUuid(JIRA_CONNECTOR_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, JIRA_CONNECTOR_NAME)).thenReturn(jiraAttribute);
    when(settingsService.get(JIRA_CONNECTOR_ID)).thenReturn(jiraAttribute);

    SettingAttribute snowAttribute =
        aSettingAttribute().withName(SNOW_CONNECTOR_NAME).withUuid(SNOW_CONNECTOR_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, SNOW_CONNECTOR_NAME)).thenReturn(snowAttribute);
    when(settingsService.get(SNOW_CONNECTOR_ID)).thenReturn(snowAttribute);

    SettingAttribute bambooAttribute =
        aSettingAttribute().withName(BAMBOO_CONFIG_NAME).withUuid(BAMBOO_CONFIG_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, BAMBOO_CONFIG_NAME)).thenReturn(bambooAttribute);
    when(settingsService.get(BAMBOO_CONFIG_ID)).thenReturn(bambooAttribute);

    SettingAttribute jenkinsAttribute = aSettingAttribute().withName(JENKINS_NAME).withUuid(JENKINS_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, JENKINS_NAME)).thenReturn(jenkinsAttribute);
    when(settingsService.get(JENKINS_ID)).thenReturn(jenkinsAttribute);

    SettingAttribute sshAttribute = aSettingAttribute().withName(SSH_KEY_REF_NAME).withUuid(SSH_KEY_REF).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, SSH_KEY_REF_NAME)).thenReturn(sshAttribute);
    when(settingsService.get(SSH_KEY_REF)).thenReturn(sshAttribute);

    SettingAttribute winrmAttribute =
        aSettingAttribute().withName(CONNECTION_ATTRIBUTE_NAME).withUuid(CONNECTION_ATTRIBUTES).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, CONNECTION_ATTRIBUTE_NAME)).thenReturn(winrmAttribute);
    when(settingsService.get(CONNECTION_ATTRIBUTES)).thenReturn(winrmAttribute);

    SettingAttribute gcpAttribute = aSettingAttribute().withName(GCP_CONFIG_NAME).withUuid(GCP_CONFIG_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, GCP_CONFIG_NAME)).thenReturn(gcpAttribute);
    when(settingsService.get(GCP_CONFIG_ID)).thenReturn(gcpAttribute);

    SettingAttribute gitAttribute = aSettingAttribute().withName(GIT_CONFIG_NAME).withUuid(GIT_CONFIG_ID).build();
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, GIT_CONFIG_NAME)).thenReturn(gitAttribute);
    when(settingsService.get(GIT_CONFIG_ID)).thenReturn(gitAttribute);
  }

  List<Map<String, Object>> getTemplateExpressions(String expression, String fieldName) {
    return Collections.singletonList(ImmutableMap.of("expression", expression, "fieldName", fieldName));
  }
}
