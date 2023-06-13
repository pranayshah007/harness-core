/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefBuilder;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.inputset.InputSetErrorDTOPMS;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.preflight.PreFlightCause;
import io.harness.pms.preflight.PreFlightDTO;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.connector.ConnectorPreflightHandler;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.entity.PreFlightEntity.PreFlightEntityKeys;
import io.harness.pms.preflight.inputset.PipelineInputResponse;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.repositories.preflight.PreFlightRepository;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PreflightServiceImplTest extends CategoryTest {
  @InjectMocks PreflightServiceImpl preflightService;
  @Mock PreFlightRepository preFlightRepository;
  @Mock ConnectorPreflightHandler connectorPreflightHandler;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Mock PipelineRbacService pipelineRbacServiceImpl;
  @Mock PipelineTelemetryHelper pipelineTelemetryHelper;

  private static final String accountId = "accountId";
  private static final String orgId = "orgId";
  private static final String projectId = "projectId";
  private static final String pipelineId = "basichttpFail";

  String pipelineYaml;
  PipelineEntity pipelineEntity;

  List<EntityDetail> entityDetails;
  List<EntityDetail> connUsages;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    String pipelineFile = "failure-strategy.yaml";
    pipelineYaml = readFile(pipelineFile);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(orgId)
                         .projectIdentifier(projectId)
                         .identifier(pipelineId)
                         .yaml(pipelineYaml)
                         .build();

    IdentifierRefBuilder identifierRefBuilder =
        IdentifierRef.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).scope(
            Scope.PROJECT);

    EntityDetail service = EntityDetail.builder()
                               .type(EntityType.SERVICE)
                               .entityRef(identifierRefBuilder.identifier("ManagerServiceLatest").build())
                               .build();

    EntityDetail environment = EntityDetail.builder()
                                   .type(EntityType.ENVIRONMENT)
                                   .entityRef(identifierRefBuilder.identifier("stagingInfra").build())
                                   .build();

    EntityDetail gitConnector = EntityDetail.builder()
                                    .type(EntityType.CONNECTORS)
                                    .entityRef(identifierRefBuilder.identifier("my_git_connector").build())
                                    .build();

    EntityDetail k8sConnector = EntityDetail.builder()
                                    .type(EntityType.CONNECTORS)
                                    .entityRef(identifierRefBuilder.identifier("myK8sConnector").build())
                                    .build();
    entityDetails = Arrays.asList(service, environment, gitConnector, k8sConnector);
    connUsages = Arrays.asList(gitConnector, k8sConnector);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testStartPreflightCheck() {
    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    assertThatThrownBy(() -> preflightService.startPreflightCheck(accountId, orgId, projectId, pipelineId, ""))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The given pipeline id [basichttpFail] does not exist");

    doReturn(Optional.of(pipelineEntity))
        .when(pmsPipelineService)
        .getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    doReturn(entityDetails)
        .when(pipelineSetupUsageHelper)
        .getReferencesOfPipeline(accountId, orgId, projectId, pipelineId, pipelineYaml, null);
    doNothing().when(pipelineRbacServiceImpl).validateStaticallyReferredEntities(entityDetails);

    // keeping any() here cuz saveInitialPreflightEntity has a separate test
    doReturn(Collections.emptyList()).when(connectorPreflightHandler).getConnectorCheckResponseTemplate(any());
    doReturn(PreFlightEntity.builder().uuid("preflightId").pipelineYaml(pipelineYaml).build())
        .when(preFlightRepository)
        .save(any());
    String preflightId = preflightService.startPreflightCheck(accountId, orgId, projectId, pipelineId, "");
    assertThat(preflightId).isEqualTo("preflightId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testSaveInitialPreflightEntity() {
    Map<String, InputSetErrorResponseDTOPMS> oneError = Collections.singletonMap("pipeline.stages.a1.this.fqn",
        InputSetErrorResponseDTOPMS.builder()
            .errors(
                Collections.singletonList(InputSetErrorDTOPMS.builder().fieldName("fqn").message("my message").build()))
            .build());
    List<PipelineInputResponse> pipelineInputResponses = preflightService.getPipelineInputResponses(oneError);

    doReturn(Collections.emptyList()).when(connectorPreflightHandler).getConnectorCheckResponseTemplate(connUsages);
    doReturn(PreFlightEntity.builder().uuid("preflightId").pipelineYaml(pipelineYaml).build())
        .when(preFlightRepository)
        .save(any());

    PreFlightEntity preFlightEntity = preflightService.saveInitialPreflightEntity(
        accountId, orgId, projectId, pipelineId, pipelineYaml, entityDetails, pipelineInputResponses);
    assertThat(preFlightEntity.getUuid()).isEqualTo("preflightId");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    String id = "preflightId";
    PreFlightEntityErrorInfo preFlightErrorInfo = PreFlightEntityErrorInfo.builder().summary("all fine here").build();
    preflightService.updateStatus(id, PreFlightStatus.IN_PROGRESS, preFlightErrorInfo);

    Criteria criteria = Criteria.where(PreFlightEntityKeys.uuid).is(id);
    Update update = new Update()
                        .set(PreFlightEntityKeys.preFlightStatus, PreFlightStatus.IN_PROGRESS)
                        .set(PreFlightEntityKeys.errorInfo, preFlightErrorInfo);
    verify(preFlightRepository, times(1)).update(criteria, update);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateConnectorCheckResponses() {
    String preflightEntityId = "preflightEntityId";

    ConnectorCheckResponse connResponse = ConnectorCheckResponse.builder().status(PreFlightStatus.SUCCESS).build();
    doReturn(Collections.singletonList(connResponse))
        .when(connectorPreflightHandler)
        .getConnectorCheckResponsesForReferredConnectors(
            accountId, orgId, projectId, Collections.emptyMap(), connUsages);

    preflightService.updateConnectorCheckResponses(
        accountId, orgId, projectId, preflightEntityId, Collections.emptyMap(), connUsages);

    Criteria criteria = Criteria.where(PreFlightEntityKeys.uuid).is(preflightEntityId);
    Update update =
        new Update().set(PreFlightEntityKeys.connectorCheckResponse, Collections.singletonList(connResponse));
    verify(preFlightRepository, times(1)).update(criteria, update);

    String exceptionMessage = "Runtime Exception";
    List<ConnectorCheckResponse> connectorCheckResponsesForFailure = Collections.singletonList(
        ConnectorCheckResponse.builder()
            .status(PreFlightStatus.FAILURE)
            .errorInfo(PreFlightEntityErrorInfo.builder()
                           .causes(Collections.singletonList(PreFlightCause.builder().cause(exceptionMessage).build()))
                           .summary(String.format(
                               "Exception encountered while checking connector responses. %s", exceptionMessage))
                           .build())
            .build());
    when(connectorPreflightHandler.getConnectorCheckResponsesForReferredConnectors(
             accountId, orgId, projectId, Collections.emptyMap(), connUsages))
        .thenThrow(new RuntimeException(exceptionMessage));

    List<ConnectorCheckResponse> responses = preflightService.updateConnectorCheckResponses(
        accountId, orgId, projectId, preflightEntityId, Collections.emptyMap(), connUsages);

    update = new Update().set(PreFlightEntityKeys.connectorCheckResponse, connectorCheckResponsesForFailure);
    verify(preFlightRepository, times(1)).update(criteria, update);
    assertThat(responses).isEqualTo(connectorCheckResponsesForFailure);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPreflightCheckResponse() {
    String wrongId = "wrongId";
    doReturn(Optional.empty()).when(preFlightRepository).findById(wrongId);

    String correctId = "correctId";
    PreFlightEntity preFlightEntity = PreFlightEntity.builder()
                                          .pipelineYaml(pipelineYaml)
                                          .pipelineInputResponse(Collections.emptyList())
                                          .connectorCheckResponse(Collections.emptyList())
                                          .build();
    doReturn(Optional.of(preFlightEntity)).when(preFlightRepository).findById(correctId);

    assertThatThrownBy(() -> preflightService.getPreflightCheckResponse(wrongId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not find pre flight check data corresponding to id:wrongId");

    PreFlightDTO preflightCheckResponse = preflightService.getPreflightCheckResponse(correctId);
    assertThat(preflightCheckResponse).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineInputResponses() {
    Map<String, InputSetErrorResponseDTOPMS> noErrors = new HashMap<>();
    List<PipelineInputResponse> emptyResponse = preflightService.getPipelineInputResponses(noErrors);
    assertThat(emptyResponse).isEmpty();
    noErrors.put("pipeline.stages.a1.this.fqn",
        InputSetErrorResponseDTOPMS.builder()
            .errors(
                Collections.singletonList(InputSetErrorDTOPMS.builder().fieldName("fqn").message("my message").build()))
            .build());
    List<PipelineInputResponse> pipelineInputResponses = preflightService.getPipelineInputResponses(noErrors);
    assertThat(pipelineInputResponses).hasSize(1);
    PipelineInputResponse response = pipelineInputResponses.get(0);
    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getStageName()).isEqualTo("a1");
    PreFlightEntityErrorInfo errorInfo = response.getErrorInfo();
    assertThat(errorInfo.getSummary()).isEqualTo("Runtime value provided for pipeline.stages.a1.this.fqn is wrong");
    assertThat(errorInfo.getCauses()).hasSize(1);
    PreFlightCause preFlightCause = errorInfo.getCauses().get(0);
    assertThat(preFlightCause.getCause()).isEqualTo("my message");
  }
}
