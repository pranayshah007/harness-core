/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.cv;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.checkIfStringIsValidUrl;
import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;
import static io.harness.ngmigration.utils.NGMigrationConstants.SECRET_FORMAT;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.connector.ConnectorDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.http.HttpHeaderConfig;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.plancreator.steps.http.HttpStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.NewRelicDeploymentMarkerState;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NewRelicDeploymentMarkerStepMapperImpl extends StepMapper {
  private static final String URL_FORMAT = "%sv2/applications/%s/deployments.json";

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.HTTP;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    NewRelicDeploymentMarkerState state = new NewRelicDeploymentMarkerState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    NewRelicDeploymentMarkerState state = (NewRelicDeploymentMarkerState) getState(graphNode);
    List<CgEntityId> references = new ArrayList<>();
    if (isNotEmpty(state.getAnalysisServerConfigId())) {
      references.add(
          CgEntityId.builder().id(state.getAnalysisServerConfigId()).type(NGMigrationEntityType.CONNECTOR).build());
    }

    return references;
  }

  private String getXApiKey(Map<CgEntityId, NGYamlFile> migratedEntities, String analysisServerConfigId) {
    Optional<NewRelicConnectorDTO> connector = getConnector(migratedEntities, analysisServerConfigId);
    if (connector.isPresent()) {
      return String.format(SECRET_FORMAT, connector.get().getApiKeyRef().toSecretRefStringValue());
    }
    return PLEASE_FIX_ME;
  }

  private String getBaseUrl(NewRelicDeploymentMarkerState state, Map<CgEntityId, NGYamlFile> migratedEntities) {
    Optional<NewRelicConnectorDTO> connector = getConnector(migratedEntities, state.getAnalysisServerConfigId());
    String url = PLEASE_FIX_ME;
    if (connector.isPresent()) {
      url = connector.get().getUrl();
      if (!url.endsWith("/")) {
        url = url + "/";
      }
      if (!checkIfStringIsValidUrl(url)) {
        url = "https://" + url;
      }
    }

    return url;
  }

  private Optional<NewRelicConnectorDTO> getConnector(
      Map<CgEntityId, NGYamlFile> migratedEntities, String serverConfigId) {
    if (isEmpty(serverConfigId)) {
      return Optional.empty();
    }
    CgEntityId entityId = CgEntityId.builder().id(serverConfigId).type(CONNECTOR).build();
    if (!migratedEntities.containsKey(entityId)) {
      return Optional.empty();
    }

    ConnectorDTO connectorDTO = (ConnectorDTO) migratedEntities.get(entityId).getYaml();
    NewRelicConnectorDTO connectorConfig = (NewRelicConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    return Optional.of(connectorConfig);
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    NewRelicDeploymentMarkerState state = (NewRelicDeploymentMarkerState) getState(graphNode);
    Map<CgEntityId, NGYamlFile> migratedEntities = context.getMigratedEntities();
    String baseUrl = getBaseUrl(state, migratedEntities);
    String applicationId = isEmpty(state.getApplicationId()) ? PLEASE_FIX_ME : state.getApplicationId();
    String url = String.format(URL_FORMAT, baseUrl, applicationId);

    HttpStepNode httpStepNode = new HttpStepNode();
    baseSetup(graphNode, httpStepNode, context.getIdentifierCaseFormat());

    HttpStepInfo httpStepInfo = HttpStepInfo.infoBuilder()
                                    .url(ParameterField.createValueField(url))
                                    .method(ParameterField.createValueField("POST"))
                                    .requestBody(ParameterField.createValueField(state.getBody()))
                                    .headers(Collections.singletonList(
                                        HttpHeaderConfig.builder()
                                            .key("X-Api-Key")
                                            .value(getXApiKey(migratedEntities, state.getAnalysisServerConfigId()))
                                            .build()))
                                    .build();

    httpStepNode.setHttpStepInfo(httpStepInfo);
    return httpStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    NewRelicDeploymentMarkerState state1 = (NewRelicDeploymentMarkerState) getState(stepYaml1);
    NewRelicDeploymentMarkerState state2 = (NewRelicDeploymentMarkerState) getState(stepYaml2);
    return Objects.equal(state1.getAnalysisServerConfigId(), state2.getAnalysisServerConfigId())
        && Objects.equal(state1.getBody(), state2.getBody())
        && Objects.equal(state1.getApplicationId(), state2.getApplicationId());
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }
}
