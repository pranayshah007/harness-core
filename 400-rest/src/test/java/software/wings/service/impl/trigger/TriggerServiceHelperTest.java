/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_COLLECTED;
import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_DEPLOYED;
import static software.wings.beans.trigger.ArtifactSelection.Type.WEBHOOK_VARIABLE;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildArtifactSelection;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildNexusArtifactStream;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildParameters;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildSimpleService;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWebhookToken;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.buildWorkflowWebhookTrigger;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.completePayload;
import static software.wings.service.impl.trigger.TriggerServiceTestHelper.simplePayload;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID_CHANGED;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Service;
import software.wings.beans.WebHookToken;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ManifestSelection;
import software.wings.beans.trigger.ManifestSelection.ManifestSelectionType;
import software.wings.beans.trigger.Trigger;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class TriggerServiceHelperTest extends WingsBaseTest {
  private static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  private static final String CATALOG_SERVICE_NAME = "Catalog";
  @Inject @InjectMocks private TriggerServiceHelper triggerServiceHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactCollectionService artifactCollectionService;
  Trigger workflowWebhookConditionTrigger = buildWorkflowWebhookTrigger();

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void constructWebhookTokenForParameterizedArtifactStream() {
    Mockito.when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(buildNexusArtifactStream());

    workflowWebhookConditionTrigger.setArtifactSelections(asList(ArtifactSelection.builder()
                                                                     .type(WEBHOOK_VARIABLE)
                                                                     .serviceId(SERVICE_ID)
                                                                     .artifactStreamId(ARTIFACT_STREAM_ID_1)
                                                                     .build()));
    workflowWebhookConditionTrigger.setUuid(TRIGGER_ID);
    Map<String, String> parameters = new HashMap<>();
    parameters.put("MyVar", "MyVar_placeholder");
    WebHookToken webHookToken = triggerServiceHelper.constructWebhookToken(workflowWebhookConditionTrigger, null,
        asList(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).accountId(ACCOUNT_ID).build()), true,
        parameters, Collections.emptyList());
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "artifacts", "parameters");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("artifacts"))
        .isNotNull()
        .toString()
        .contains(
            "[{service=Catalog, buildNumber=Catalog_BUILD_NUMBER_PLACE_HOLDER, artifactVariables={path=PATH_PLACE_HOLDER, groupId=GROUPID_PLACE_HOLDER}}]");
    assertThat(hashMap.get("parameters")).isNotNull().toString().contains("MyVar=MyVar_placeholder");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void verifyWebhookTokenContainsManifests() {
    workflowWebhookConditionTrigger.setManifestSelections(asList(ManifestSelection.builder()
                                                                     .type(ManifestSelectionType.WEBHOOK_VARIABLE)
                                                                     .serviceId(SERVICE_ID)
                                                                     .appManifestId(MANIFEST_ID)
                                                                     .build()));
    workflowWebhookConditionTrigger.setUuid(TRIGGER_ID);
    Mockito.when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);

    WebHookToken webHookToken = triggerServiceHelper.constructWebhookToken(workflowWebhookConditionTrigger, null,
        asList(Service.builder().uuid(SERVICE_ID).name(CATALOG_SERVICE_NAME).accountId(ACCOUNT_ID).build()), true,
        Collections.emptyMap(), Collections.singletonList("${service}"));
    assertThat(webHookToken).isNotNull();
    assertThat(webHookToken.getWebHookToken()).isNotNull();
    assertThat(webHookToken.getPayload()).isNotEmpty();

    HashMap<String, Object> hashMap = new Gson().fromJson(webHookToken.getPayload(), HashMap.class);
    assertThat(hashMap).containsKeys("application", "manifests");
    assertThat(hashMap.get("application")).isEqualTo(APP_ID);
    assertThat(hashMap.get("manifests"))
        .isNotNull()
        .toString()
        .contains(
            "[{service=Catalog, versionNumber=Catalog_VERSION_NUMBER_PLACE_HOLDER}], {service=${service}, versionNumber=${service}_VERSION_NUMBER_PLACE_HOLDER}");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFireArtifactCollection() {
    triggerServiceHelper.collectArtifactsForSelection(
        ArtifactSelection.builder().artifactStreamId(ARTIFACT_STREAM_ID).build(), APP_ID);
    verify(artifactCollectionService).collectNewArtifacts(APP_ID, ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetArtifactInfoWhenFFDisabled() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    Map<String, Object> artifactInfo = triggerServiceHelper.getArtifactInfo(
        buildSimpleService(SERVICE_ID), List.of(buildArtifactSelection(LAST_COLLECTED)));
    assertThat(artifactInfo.isEmpty()).isEqualTo(false);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetArtifactInfoWhenFFEnabled() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    Map<String, Object> artifactInfo = triggerServiceHelper.getArtifactInfo(
        buildSimpleService(SERVICE_ID), List.of(buildArtifactSelection(LAST_COLLECTED)));
    assertThat(artifactInfo.isEmpty()).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetArtifactInfoWhenFFEnabledAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    Map<String, Object> artifactInfo = triggerServiceHelper.getArtifactInfo(
        buildSimpleService(SERVICE_ID), List.of(buildArtifactSelection(LAST_DEPLOYED)));
    assertThat(artifactInfo.isEmpty()).isEqualTo(false);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetArtifactInfoWhenFFEnabledAndServiceIDNotEqualsAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    Map<String, Object> artifactInfo = triggerServiceHelper.getArtifactInfo(
        buildSimpleService(SERVICE_ID_CHANGED), List.of(buildArtifactSelection(LAST_DEPLOYED)));
    assertThat(artifactInfo.isEmpty()).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithoutTokenAndFFDisabledAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_DEPLOYED)), null,
            List.of(buildSimpleService(SERVICE_ID_CHANGED)), true, buildParameters(), null);
    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("POST");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithoutTokenAndFFEnabledAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_DEPLOYED)), null,
            List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);
    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("POST");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithoutTokenAndFFDisabledAndLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_COLLECTED)), null,
            List.of(buildSimpleService(SERVICE_ID_CHANGED)), true, buildParameters(), null);
    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("POST");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithoutTokenAndFFEnabledAndLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_COLLECTED)), null,
            List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);
    assertThat(webhook.getPayload()).isEqualTo(simplePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("POST");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithTokenWhenFFEnabledAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_DEPLOYED)),
            buildWebhookToken(), List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);

    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("GET");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithTokenWhenFFDisabledAndNotLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_DEPLOYED)),
            buildWebhookToken(), List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);

    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("GET");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithTokenWhenFFEnabledAndLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_COLLECTED)),
            buildWebhookToken(), List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);

    assertThat(webhook.getPayload()).isEqualTo(simplePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("GET");
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldConstructWebhookTokenWithTokenWhenFFDisabledAndLastCollectedType() {
    when(featureFlagService.isNotEnabled(FeatureName.SPG_GENERATE_CURL_WITHOUT_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WebHookToken webhook =
        triggerServiceHelper.constructWebhookToken(buildTrigger(buildArtifactSelection(LAST_COLLECTED)),
            buildWebhookToken(), List.of(buildSimpleService(SERVICE_ID)), true, buildParameters(), null);

    assertThat(webhook.getPayload()).isEqualTo(completePayload());
    assertThat(webhook.getWebHookToken()).isNotNull();
    assertThat(webhook.getHttpMethod()).isEqualTo("GET");
  }
}
