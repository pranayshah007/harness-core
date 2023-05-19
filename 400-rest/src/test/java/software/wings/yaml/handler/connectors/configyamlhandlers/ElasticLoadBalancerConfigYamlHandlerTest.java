/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.ElasticLoadBalancerConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.loadbalancer.ElasticLoadBalancerConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.service.intfc.InfrastructureMappingService;

import com.amazonaws.regions.Regions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ElasticLoadBalancerConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private ElasticLoadBalancerConfigYamlHandler yamlHandler;
  @Mock InfrastructureMappingService infrastructureMappingService;
  @Mock InfrastructureMapping infrastructureMapping;

  public static final String loadBalancerName = "loadBalancer";

  private String invalidYamlContent = "region_invalid: eu-central-1\n"
      + "loadBalancerName: jk\n"
      + "accessKey: AKIAIKL7FYYF2TIYHCLQ\n"
      + "secretKey: safeharness:j6I-dh8xSSurpME_loAslQ\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: ELB";

  private Class yamlClass = ElasticLoadBalancerConfig.Yaml.class;

  @Before
  public void setUp() throws Exception {
    List<InfrastructureMapping> list = new ArrayList<>();
    PageResponse<InfrastructureMapping> pageResponse = new PageResponse<>();
    pageResponse.setResponse(list);
    doReturn(pageResponse).when(infrastructureMappingService).list(any(), any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String name = "ELB" + System.currentTimeMillis();

    // 1. Create ELB config
    SettingAttribute settingAttributeSaved = createELBVerificationProvider(name);
    assertThat(settingAttributeSaved.getName()).isEqualTo(name);

    testCRUD(generateSettingValueYamlConfig(name, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String name = "ELB" + System.currentTimeMillis();

    // 1. Create ELB config record
    SettingAttribute settingAttributeSaved = createELBVerificationProvider(name);
    testFailureScenario(generateSettingValueYamlConfig(name, settingAttributeSaved));
  }

  private SettingAttribute createELBVerificationProvider(String name) {
    // Generate appdynamics verification connector
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(aSettingAttribute()
                                    .withCategory(SettingCategory.CONNECTOR)
                                    .withName(name)
                                    .withAccountId(ACCOUNT_ID)
                                    .withValue(ElasticLoadBalancerConfig.builder()
                                                   .accountId(ACCOUNT_ID)
                                                   .region(Regions.CA_CENTRAL_1)
                                                   .loadBalancerName(loadBalancerName)
                                                   .accessKey(accesskey)
                                                   .secretKey(token.toCharArray())
                                                   .build())
                                    .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(loadBalancerYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(ElasticLoadBalancerConfig.class)
        .updateMethodName("setLoadBalancerName")
        .currentFieldValue(loadBalancerName)
        .build();
  }
}
