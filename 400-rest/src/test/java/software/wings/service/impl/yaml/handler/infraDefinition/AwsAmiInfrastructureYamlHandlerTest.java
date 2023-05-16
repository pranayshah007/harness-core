/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraDefinition;

import static io.harness.annotations.dev.HarnessModule._955_CG_YAML;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.SAINATH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.AmiDeploymentType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsAmiInfrastructureYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(_955_CG_YAML)
public class AwsAmiInfrastructureYamlHandlerTest extends YamlHandlerTestBase {
  @Mock SettingsService settingsService;
  @InjectMocks @Inject AwsAmiInfrastructureYamlHandler awsAmiInfrastructureYamlHandler;
  @Mock YamlHelper yamlHelper;

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testUpsertFromYaml() throws IOException {
    String cloudProviderName = "cloudProviderName";
    String region = "region";
    String autoScalingGroupName = "autoScalingGroupName";

    List<String> classicLoadBalancers = new ArrayList<>();
    classicLoadBalancers.add("classicLoadBalancer");

    List<String> targetGroupArns = new ArrayList<>();
    targetGroupArns.add("targetGroupArn");

    String hostNameConvention = "hostNameConvention";

    List<String> stageClassicLoadBalancers = new ArrayList<>();
    stageClassicLoadBalancers.add("stageClassicLoadBalancer");

    List<String> stageTargetGroupArns = new ArrayList<>();
    stageTargetGroupArns.add("stageTargetGroupArn");

    Map<String, String> expressions = new HashMap<>();
    expressions.put("expressionKey", "expressionValue");

    AmiDeploymentType amiDeploymentType = AmiDeploymentType.AWS_ASG;

    String spotinstElastiGroupJson = "spotinstElastiGroupJson";
    String spotinstCloudProviderName = "spotinstCloudProviderName";

    boolean asgIdentifiesWorkload = false;
    boolean useTrafficShift = false;

    AwsAmiInfrastructure.Yaml yaml = AwsAmiInfrastructure.Yaml.builder()
                                         .cloudProviderName(cloudProviderName)
                                         .region(region)
                                         .autoScalingGroupName(autoScalingGroupName)
                                         .classicLoadBalancers(classicLoadBalancers)
                                         .targetGroupArns(targetGroupArns)
                                         .hostNameConvention(hostNameConvention)
                                         .stageClassicLoadBalancers(stageClassicLoadBalancers)
                                         .stageTargetGroupArns(stageTargetGroupArns)
                                         .expressions(expressions)
                                         .amiDeploymentType(amiDeploymentType)
                                         .spotinstElastiGroupJson(spotinstElastiGroupJson)
                                         .spotinstCloudProviderName(spotinstCloudProviderName)
                                         .asgIdentifiesWorkload(asgIdentifiesWorkload)
                                         .useTrafficShift(useTrafficShift)
                                         .build();

    String accountId = "accountId";
    Change change = Change.Builder.aFileChange().withAccountId(accountId).build();

    ChangeContext<AwsAmiInfrastructure.Yaml> changeContext = ChangeContext.Builder.aChangeContext()
                                                                 .withYaml(yaml)
                                                                 .withYamlType(YamlType.INFRA_DEFINITION)
                                                                 .withChange(change)
                                                                 .build();

    String cloudProviderId = "cloudProviderId";

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withUuid(cloudProviderId).build();
    doReturn(settingAttribute).when(settingsService).getSettingAttributeByName(anyString(), anyString());
    doReturn("App1").when(yamlHelper).getAppId(any(), any());
    doNothing().when(settingsService).checkRbacOnSettingAttribute(any(), any());
    AwsAmiInfrastructure bean = awsAmiInfrastructureYamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getCloudProviderId()).isEqualTo(cloudProviderId);
    assertThat(bean.getRegion()).isEqualTo(region);
    assertThat(bean.getAutoScalingGroupName()).isEqualTo(autoScalingGroupName);
    assertThat(bean.getClassicLoadBalancers()).isEqualTo(classicLoadBalancers);
    assertThat(bean.getTargetGroupArns()).isEqualTo(targetGroupArns);
    assertThat(bean.getHostNameConvention()).isEqualTo(hostNameConvention);
    assertThat(bean.getStageClassicLoadBalancers()).isEqualTo(stageClassicLoadBalancers);
    assertThat(bean.getStageTargetGroupArns()).isEqualTo(stageTargetGroupArns);
    assertThat(bean.getAmiDeploymentType()).isEqualTo(amiDeploymentType);
    assertThat(bean.getSpotinstElastiGroupJson()).isEqualTo(spotinstElastiGroupJson);
    assertThat(bean.isAsgIdentifiesWorkload()).isEqualTo(asgIdentifiesWorkload);
    assertThat(bean.isUseTrafficShift()).isEqualTo(useTrafficShift);
    assertThat(bean.getExpressions()).isEqualTo(expressions);
  }
}
