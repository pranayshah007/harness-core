/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.connector.bamboo.BambooAuthenticationDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class BambooBuildStepHelperServiceImplTest extends CDNGTestBase {
  @InjectMocks private BambooBuildStepHelperServiceImpl bambooBuildStepHelperService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer referenceFalseKryoSerializer;

  private static final String ACCOUNT = "account";
  private static final String ORG = "org";
  private static final String PROJECT = "project";
  private static final String CONNECTOR = "connector";
  private static final String URL = "url";
  private static final String TIME_OUT = "10m";

  private static final TaskSelectorYaml TASK_SELECTOR_YAML = getTaskSelectorYaml("step", "step-selector");
  private static final ParameterField DELEGATE_SELECTORS = ParameterField.createValueField(List.of(TASK_SELECTOR_YAML));
  private static final List<TaskSelector> TASK_SELECTORS = TaskSelectorYaml.toTaskSelector(DELEGATE_SELECTORS);
  private static final Map<String, String> ACCOUNT_ORG_PROJECT = Map.of(SetupAbstractionKeys.accountId, ACCOUNT,
      SetupAbstractionKeys.orgIdentifier, ORG, SetupAbstractionKeys.projectIdentifier, PROJECT);
  private static final Ambiance AMBIANCE = Ambiance.newBuilder().putAllSetupAbstractions(ACCOUNT_ORG_PROJECT).build();
  private static final ConnectorDTO BAMBOO_CONNECTOR =
      ConnectorDTO.builder()
          .connectorInfo(ConnectorInfoDTO.builder()
                             .identifier(CONNECTOR)
                             .connectorConfig(BambooConnectorDTO.builder()
                                                  .bambooUrl(URL)
                                                  .auth(BambooAuthenticationDTO.builder()
                                                            .credentials(BambooUserNamePasswordDTO.builder().build())
                                                            .build())
                                                  .build())
                             .build())
          .build();

  private static TaskSelectorYaml getTaskSelectorYaml(String origin, String delegateSelector) {
    TaskSelectorYaml taskSelectorYaml = new TaskSelectorYaml();
    taskSelectorYaml.setDelegateSelectors(delegateSelector);
    taskSelectorYaml.setOrigin(origin);
    return taskSelectorYaml;
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testPrepareTaskRequest() throws IOException {
    Call mockCall = mock(Call.class);
    doReturn(mockCall).when(connectorResourceClient).get(any(), any(), any(), any());
    doReturn(mockCall).when(mockCall).clone();
    doReturn(Response.success(ResponseDTO.newResponse(Optional.of(BAMBOO_CONNECTOR)))).when(mockCall).execute();
    doReturn(List.of())
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), any(BambooUserNamePasswordDTO.class));

    TaskRequest taskRequest = bambooBuildStepHelperService.prepareTaskRequest(
        BambooArtifactDelegateRequest.builder(), AMBIANCE, CONNECTOR, TIME_OUT, "task", TASK_SELECTORS);
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getSelector())
        .isEqualTo(TASK_SELECTOR_YAML.getDelegateSelectors());
    assertThat(taskRequest.getDelegateTaskRequest().getRequest().getSelectors(0).getOrigin())
        .isEqualTo(TASK_SELECTOR_YAML.getOrigin());
    verify(connectorResourceClient).get(CONNECTOR, ACCOUNT, ORG, PROJECT);
  }
}