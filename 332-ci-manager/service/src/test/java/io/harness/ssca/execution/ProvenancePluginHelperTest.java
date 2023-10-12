/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.rule.OwnerRule.INDER;
import static io.harness.ssca.execution.ProvenancePluginHelper.COSIGN_PASSWORD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ProvenanceArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.provenance.BuildDefinition;
import io.harness.beans.provenance.InternalParameters;
import io.harness.beans.provenance.ProvenanceBuilder;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.RunDetails;
import io.harness.beans.provenance.RunDetailsMetadata;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.AttestationV1;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.provenance.DockerSourceSpec;
import io.harness.ssca.beans.provenance.GcrSourceSpec;
import io.harness.ssca.beans.provenance.ProvenanceSource;
import io.harness.ssca.beans.provenance.ProvenanceSourceType;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenancePluginHelperTest extends CIExecutionTestBase {
  @InjectMocks private ProvenancePluginHelper provenancePluginHelper;
  @Mock private OutcomeService outcomeService;
  ;

  private Ambiance ambiance;

  private static final String CONNECTOR = "connectorRef";
  private static final String DOCKER_REPO = "library/nginx";
  private static final String TAG_1 = "latest";
  private static final String DIGEST_1 = "digest";
  private static final String COSIGN_PASS = "cosignPass";
  private static final String COSIGN_PRIVATE_KEY = "cosignKey";
  private static final String STEP_IDENTIFIER = "stepIdentifier_Provenance_Step";
  private static final String GCR_HOST = "us.gcr.io";
  private static final String GCP_PROJECT = "my-project";

  @Before
  public void setup() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .setTriggerInfo(
                                        ExecutionTriggerInfo.newBuilder()
                                            .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("triggerBy").build())
                                            .build())
                                    .build())
                   .setPlanExecutionId("pipelineExecutionUuid")
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("identifierId")
                                  .setOriginalIdentifier("originalIdentifierId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetDockerProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(6);
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TAGS, TAG_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenancePluginHelper.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(ProvenancePluginHelper.PROVENANCE_PREDICATE, "");
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_DIGESTS, "");
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TYPE, "attest");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetGcrProvenanceStepEnvVariables() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForGcrSource();
    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariables(provenanceStepInfo, STEP_IDENTIFIER, ambiance);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(7);
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TAGS, TAG_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REGISTRY, GCR_HOST + "/" + GCP_PROJECT);
    expectedEnvMap.put(ProvenancePluginHelper.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(ProvenancePluginHelper.PROVENANCE_PREDICATE, "");
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_DIGESTS, "");
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TYPE, "attest");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetProvenanceSecretVars() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    Map<String, SecretNGVariable> secretNGVariableMap =
        provenancePluginHelper.getProvenanceStepSecretVariables(provenanceStepInfo);
    assertThat(secretNGVariableMap).isNotNull().isNotEmpty().hasSize(2);
    assertThat(secretNGVariableMap.get(COSIGN_PASSWORD)).isNotNull();
    SecretNGVariable variable = secretNGVariableMap.get(COSIGN_PASSWORD);
    assertThat(variable.getType()).isEqualTo(NGVariableType.SECRET);
    assertThat(variable.getName()).isEqualTo(COSIGN_PASSWORD);
    assertThat(variable.getValue()).isNotNull();
    SecretRefData secretRefData = variable.getValue().getValue();
    assertThat(secretRefData).isNotNull();
    assertThat(secretRefData.getScope()).isEqualTo(Scope.PROJECT);
    assertThat(secretRefData.toSecretRefStringValue()).isEqualTo(COSIGN_PASS);

    ProvenanceStepInfo accountLevelSecretStepInfo =
        ProvenanceStepInfo.builder()
            .attestation(AttestationV1.builder()
                             .type(AttestationType.COSIGN)
                             .spec(CosignAttestationV1.builder().password("account.test").private_key("key").build())
                             .build())
            .build();
    Map<String, SecretNGVariable> secretVariableMap1 =
        provenancePluginHelper.getProvenanceStepSecretVariables(accountLevelSecretStepInfo);
    assertThat(secretVariableMap1).isNotEmpty().hasSize(2);
    assertThat(secretVariableMap1.get(COSIGN_PASSWORD)).isNotNull();
    assertThat(secretVariableMap1.get(COSIGN_PASSWORD).getValue().getValue()).isNotNull();
    assertThat(secretVariableMap1.get(COSIGN_PASSWORD).getValue().getValue().getScope()).isEqualTo(Scope.ACCOUNT);
    assertThat(secretVariableMap1.get(COSIGN_PASSWORD).getValue().getValue().getIdentifier()).isEqualTo("test");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetDockerProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForDockerSource();
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(provenanceStepInfo, STEP_IDENTIFIER, ambiance);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(6);
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TAGS, TAG_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenancePluginHelper.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(ProvenancePluginHelper.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_DIGESTS, DIGEST_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TYPE, "attest");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetGcrProvenanceStepEnvVariablesAtRuntime() {
    ProvenanceStepInfo provenanceStepInfo = getProvenanceForGcrSource();
    ProvenancePredicate predicate = getProvenancePredicate();
    mockOutcomeService(predicate);

    Map<String, String> envMap =
        provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(provenanceStepInfo, STEP_IDENTIFIER, ambiance);
    assertThat(envMap).isNotNull().isNotEmpty();
    assertThat(envMap).hasSize(7);
    Map<String, String> expectedEnvMap = new HashMap<>();
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TAGS, TAG_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REPO, DOCKER_REPO);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_REGISTRY, GCR_HOST + "/" + GCP_PROJECT);
    expectedEnvMap.put(ProvenancePluginHelper.STEP_EXECUTION_ID, "runtimeId");
    expectedEnvMap.put(ProvenancePluginHelper.PROVENANCE_PREDICATE, JsonUtils.asJson(predicate));
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_DIGESTS, DIGEST_1);
    expectedEnvMap.put(ProvenancePluginHelper.PLUGIN_TYPE, "attest");
    assertThat(envMap).isEqualTo(expectedEnvMap);
  }

  private void mockOutcomeService(ProvenancePredicate predicate) {
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_stepIdentifier")))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(CIStepArtifactOutcome.builder()
                                     .stepArtifacts(
                                         StepArtifacts.builder()
                                             .provenanceArtifact(ProvenanceArtifact.builder()
                                                                     .predicate(predicate)
                                                                     .predicateType("slsaProvenance1")
                                                                     .build())
                                             .publishedImageArtifact(
                                                 PublishedImageArtifact.builder().digest(DIGEST_1).tag(TAG_1).build())
                                             .build())

                                     .build())
                        .build());
  }

  private ProvenanceStepInfo getProvenanceForDockerSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.DOCKER)
                    .spec(DockerSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .repo(ParameterField.createValueField(DOCKER_REPO))
                              .tags(ParameterField.createValueField(List.of(TAG_1)))
                              .build())
                    .build())
        .attestation(
            AttestationV1.builder()
                .type(AttestationType.COSIGN)
                .spec(CosignAttestationV1.builder().password(COSIGN_PASS).private_key(COSIGN_PRIVATE_KEY).build())
                .build())
        .build();
  }

  private ProvenanceStepInfo getProvenanceForGcrSource() {
    return ProvenanceStepInfo.builder()
        .source(ProvenanceSource.builder()
                    .type(ProvenanceSourceType.GCR)
                    .spec(GcrSourceSpec.builder()
                              .connector(ParameterField.createValueField(CONNECTOR))
                              .imageName(ParameterField.createValueField(DOCKER_REPO))
                              .host(ParameterField.createValueField(GCR_HOST))
                              .projectID(ParameterField.createValueField(GCP_PROJECT))
                              .tags(ParameterField.createValueField(List.of(TAG_1)))
                              .build())
                    .build())
        .attestation(
            AttestationV1.builder()
                .type(AttestationType.COSIGN)
                .spec(CosignAttestationV1.builder().password(COSIGN_PASS).private_key(COSIGN_PRIVATE_KEY).build())
                .build())
        .build();
  }

  private ProvenancePredicate getProvenancePredicate() {
    return ProvenancePredicate.builder()
        .buildDefinition(BuildDefinition.builder()
                             .buildType("https://developer.harness.io/docs/continuous-integration")
                             .internalParameters(InternalParameters.builder()
                                                     .accountId("accountId")
                                                     .pipelineExecutionId("pipelineExecutionId")
                                                     .pipelineIdentifier("pipelineId")
                                                     .build())
                             .build())
        .runDetails(
            RunDetails.builder()
                .builder(
                    ProvenanceBuilder.builder().id("https://developer.harness.io/docs/continuous-integration").build())
                .runDetailsMetadata(
                    RunDetailsMetadata.builder().invocationId("runtimeId").startedOn("0").finishedOn("1").build())
                .build())
        .build();
  }
}
