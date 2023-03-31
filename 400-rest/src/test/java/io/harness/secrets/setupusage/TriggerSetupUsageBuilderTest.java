/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.rule.OwnerRule.INDER;

import static software.wings.beans.trigger.WebHookTriggerCondition.WEBHOOK_SECRET;
import static software.wings.settings.SettingVariableTypes.TRIGGER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.secrets.setupusage.builders.TriggerSetupUsageBuilder;

import software.wings.WingsBaseTest;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookSource;
import software.wings.service.intfc.TriggerService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class TriggerSetupUsageBuilderTest extends WingsBaseTest {
  private final String SECRET_KEY = "SECRET_KEY";
  @Mock TriggerService triggerService;

  @Inject @InjectMocks TriggerSetupUsageBuilder triggerSetupUsageBuilder;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testBuildSecretSetupUsages() {
    WebHookTriggerCondition webHookTriggerCondition =
        WebHookTriggerCondition.builder().webhookSource(WebhookSource.GITHUB).webHookSecret(SECRET_KEY).build();
    Trigger trigger1 = Trigger.builder()
                           .uuid(TRIGGER_ID)
                           .accountId(ACCOUNT_ID)
                           .appId(APP_ID)
                           .condition(webHookTriggerCondition)
                           .build();
    Trigger trigger2 = Trigger.builder()
                           .uuid(TRIGGER_ID + 2)
                           .accountId(ACCOUNT_ID)
                           .appId(APP_ID)
                           .condition(webHookTriggerCondition)
                           .build();
    List<Trigger> triggerList = new ArrayList<>(asList(trigger1, trigger2));
    Map<String, Set<EncryptedDataParent>> parentByParentIds = new HashMap<>();
    parentByParentIds.put(trigger1.getUuid(), new HashSet<>());
    parentByParentIds.put(trigger2.getUuid(), new HashSet<>());
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(triggerList);
    when(triggerService.list(any(PageRequest.class), eq(false), any())).thenReturn(pageResponse);

    Set<SecretSetupUsage> secretSetupUsages = triggerSetupUsageBuilder.buildSecretSetupUsages(
        ACCOUNT_ID, SECRET_KEY, parentByParentIds, EncryptionDetail.builder().build());
    assertThat(secretSetupUsages).hasSize(2);
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntityId).collect(Collectors.toSet()))
        .isEqualTo(parentByParentIds.keySet());
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getType).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(TRIGGER));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getFieldName).collect(Collectors.toSet()))
        .isEqualTo(Sets.newHashSet(WEBHOOK_SECRET));
    assertThat(secretSetupUsages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toSet()))
        .isEqualTo(new HashSet<>(triggerList));
  }
}
