/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcomeAbstract;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostNamesFilterDTO;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.k8s.HarnessRelease;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class InfrastructureMapperTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks private InfrastructureMapper infrastructureMapper;
  private final EnvironmentOutcome environment =
      EnvironmentOutcome.builder().identifier("env").type(EnvironmentType.Production).build();
  private final ServiceStepOutcome serviceOutcome = ServiceStepOutcome.builder().identifier("service").build();

  AutoCloseable mocks;
  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);

    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder().connector(ConnectorInfoDTO.builder().name("my_connector").build()).build()))
        .when(connectorService)
        .getByRef(anyString(), anyString(), anyString(), anyString());
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToOutcome() {
    K8SDirectInfrastructure k8SDirectInfrastructure = K8SDirectInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .namespace(ParameterField.createValueField("namespace"))
                                                          .releaseName(ParameterField.createValueField("release"))
                                                          .build();
    k8SDirectInfrastructure.setInfraName("infraName");

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .environment(environment)
            .infrastructureKey("11f6673d11711af46238bf33972cb99a4a869244")
            .infrastructureKeyShort("11f667")
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        k8SDirectInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    InfrastructureOutcomeAbstract infrastructureOutcomeAbstract = (InfrastructureOutcomeAbstract) infrastructureOutcome;
    assertThat(infrastructureOutcomeAbstract.getName()).isEqualTo("infraName");
    assertThat(infrastructureOutcome).isEqualTo(k8sDirectInfrastructureOutcome);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToOutcomeConnectorNotFound() {
    doReturn(Optional.empty()).when(connectorService).getByRef(anyString(), anyString(), anyString(), anyString());

    K8SDirectInfrastructure k8SDirectInfrastructure = K8SDirectInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("connectorId"))
                                                          .namespace(ParameterField.createValueField("namespace"))
                                                          .releaseName(ParameterField.createValueField("release"))
                                                          .build();

    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome =
        K8sDirectInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .environment(environment)
            .infrastructureKey("11f6673d11711af46238bf33972cb99a4a869244")
            .infrastructureKeyShort("11f667")
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        k8SDirectInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(k8sDirectInfrastructureOutcome);
    assertThat(infrastructureOutcome.getConnector()).isNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testK8sGcpInfraMapper() {
    K8sGcpInfrastructure k8SGcpInfrastructure = K8sGcpInfrastructure.builder()
                                                    .connectorRef(ParameterField.createValueField("connectorId"))
                                                    .namespace(ParameterField.createValueField("namespace"))
                                                    .releaseName(ParameterField.createValueField("release"))
                                                    .cluster(ParameterField.createValueField("cluster"))
                                                    .build();

    K8sGcpInfrastructureOutcome k8sGcpInfrastructureOutcome =
        K8sGcpInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("54874007d7082ff0ab54cd51865954f5e78c5c88")
            .infrastructureKeyShort("548740")
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        k8SGcpInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(k8sGcpInfrastructureOutcome);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testServerlessAwsInfraMapper() {
    ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
        ServerlessAwsLambdaInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .region(ParameterField.createValueField("region"))
            .stage(ParameterField.createValueField("stage"))
            .build();

    ServerlessAwsLambdaInfrastructureOutcome expectedOutcome =
        ServerlessAwsLambdaInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .region("region")
            .stage("stage")
            .environment(environment)
            .infrastructureKey("ad53b5ff347a533d21b0d02bab1ae1d62506068c")
            .build();

    expectedOutcome.setConnector(Connector.builder().name("my_connector").build());

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(serverlessAwsLambdaInfrastructure,
        null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(expectedOutcome);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testElastigroupInfraMapper() {
    InlineStoreConfig storeConfig =
        InlineStoreConfig.builder().content(ParameterField.createValueField("this is content")).build();
    ElastigroupInfrastructure infrastructure =
        ElastigroupInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .configuration(
                ElastigroupConfiguration.builder()
                    .store(StoreConfigWrapper.builder().type(StoreConfigType.INLINE).spec(storeConfig).build())
                    .build())
            .build();

    ElastigroupInfrastructureOutcome expectedOutcome =
        ElastigroupInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .environment(environment)
            .infrastructureKey("bef2304c702e57cf3f33982f5473222df5c1731f")
            .build();

    expectedOutcome.setConnector(Connector.builder().name("my_connector").build());

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        infrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(expectedOutcome);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureWithConnectorToOutcome() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .hostFilter(HostFilter.builder()
                            .type(HostFilterType.HOST_NAMES)
                            .spec(HostNamesFilter.builder()
                                      .value(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                      .build())
                            .build())
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        infrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());

    PdcInfrastructureOutcome outcome =
        PdcInfrastructureOutcome.builder()
            .credentialsRef("ssh-key-ref")
            .connectorRef("connector-ref")
            .hostFilter(HostFilterDTO.builder()
                            .type(HostFilterType.HOST_NAMES)
                            .spec(HostNamesFilterDTO.builder()
                                      .value(ParameterField.createValueField(Arrays.asList("host1", "host2")))
                                      .build())
                            .build())
            .environment(environment)
            .build();
    outcome.setConnector(Connector.builder().name("my_connector").build());

    assertThat(infrastructureOutcome).isEqualToIgnoringGivenFields(outcome, "infrastructureKey");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testPdcInfrastructureWithHostsToOutcome() {
    PdcInfrastructure infrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("ssh-key-ref"))
            .hosts(ParameterField.createValueField(Arrays.asList("host1", "host2", "host3")))
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        infrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    PdcInfrastructureOutcome pdcInfrastructureOutcome =
        PdcInfrastructureOutcome.builder()
            .credentialsRef("ssh-key-ref")
            .hosts(Arrays.asList("host1", "host2", "host3"))
            .environment(environment)
            .hostFilter(
                HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(HostFilterType.ALL).build())
            .build();

    assertThat(infrastructureOutcome).isEqualToIgnoringGivenFields(pdcInfrastructureOutcome, "infrastructureKey");
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testSshWinRmAzureInfrastructureToOutcome() {
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(any(), any());

    SshWinRmAzureInfrastructure infrastructure =
        SshWinRmAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connector-ref"))
            .credentialsRef(ParameterField.createValueField("credentials-ref"))
            .resourceGroup(ParameterField.createValueField("res-group"))
            .subscriptionId(ParameterField.createValueField("sub-id"))
            .tags(ParameterField.createValueField(Collections.singletonMap("tag", "val")))
            .hostConnectionType(ParameterField.createValueField("Hostname"))
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(infrastructure, null, environment,
        serviceOutcome, "accountId", "projId", "orgId", Map.of("entityKey", "entityValue"));

    SshWinRmAzureInfrastructureOutcome outcome = SshWinRmAzureInfrastructureOutcome.builder()
                                                     .connectorRef("connector-ref")
                                                     .credentialsRef("credentials-ref")
                                                     .resourceGroup("res-group")
                                                     .subscriptionId("sub-id")
                                                     .hostTags(Collections.singletonMap("tag", "val"))
                                                     .hostConnectionType("Hostname")
                                                     .environment(environment)
                                                     .build();
    outcome.setConnector(Connector.builder().name("my_connector").build());
    // outcomes contains merged tags from aws and entity
    outcome.setTags(Map.of("tag", "val", "entityKey", "entityValue"));
    assertThat(infrastructureOutcome).isEqualToIgnoringGivenFields(outcome, "infrastructureKey");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testK8sAzureInfraMapper() {
    K8sAzureInfrastructure k8SAzureInfrastructure =
        K8sAzureInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .namespace(ParameterField.createValueField("namespace"))
            .releaseName(ParameterField.createValueField("release"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .cluster(ParameterField.createValueField("cluster"))
            .useClusterAdminCredentials(ParameterField.createValueField(true))
            .build();

    K8sAzureInfrastructureOutcome k8sAzureInfrastructureOutcome =
        K8sAzureInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .subscription("subscriptionId")
            .resourceGroup("resourceGroup")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("8f62fc4abbc11a8400589ccac4b76f32ba0f7df2")
            .infrastructureKeyShort("8f62fc")
            .useClusterAdminCredentials(true)
            .build();

    assertThat(infrastructureMapper.toOutcome(k8SAzureInfrastructure, null, environment, serviceOutcome, "accountId",
                   "projId", "orgId", new HashMap<>()))
        .isEqualTo(k8sAzureInfrastructureOutcome);

    k8SAzureInfrastructure = K8sAzureInfrastructure.builder()
                                 .connectorRef(ParameterField.createValueField("connectorId"))
                                 .namespace(ParameterField.createValueField("namespace"))
                                 .releaseName(ParameterField.createValueField("release"))
                                 .subscriptionId(ParameterField.createValueField("subscriptionId"))
                                 .resourceGroup(ParameterField.createValueField("resourceGroup"))
                                 .cluster(ParameterField.createValueField("cluster"))
                                 .useClusterAdminCredentials(ParameterField.createValueField(false))
                                 .build();

    k8sAzureInfrastructureOutcome = K8sAzureInfrastructureOutcome.builder()
                                        .connectorRef("connectorId")
                                        .namespace("namespace")
                                        .releaseName("release")
                                        .subscription("subscriptionId")
                                        .resourceGroup("resourceGroup")
                                        .cluster("cluster")
                                        .environment(environment)
                                        .infrastructureKey("8f62fc4abbc11a8400589ccac4b76f32ba0f7df2")
                                        .infrastructureKeyShort("8f62fc")
                                        .useClusterAdminCredentials(false)
                                        .build();

    assertThat(infrastructureMapper.toOutcome(k8SAzureInfrastructure, null, environment, serviceOutcome, "accountId",
                   "projId", "orgId", new HashMap<>()))
        .isEqualTo(k8sAzureInfrastructureOutcome);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testAzureWebAppInfraMapper() {
    AzureWebAppInfrastructure azureWebAppInfrastructure =
        AzureWebAppInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .subscriptionId(ParameterField.createValueField("subscriptionId"))
            .resourceGroup(ParameterField.createValueField("resourceGroup"))
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        azureWebAppInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    AzureWebAppInfrastructureOutcome outcome = AzureWebAppInfrastructureOutcome.builder()
                                                   .connectorRef("connectorId")
                                                   .subscription("subscriptionId")
                                                   .resourceGroup("resourceGroup")
                                                   .environment(environment)
                                                   .build();
    outcome.setConnector(Connector.builder().name("my_connector").build());
    assertThat(infrastructureOutcome).isEqualToIgnoringGivenFields(outcome, "infrastructureKey");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testTanzuApplicationServiceInfraMapper() {
    String pcfConnector = "connectorId";
    String org = "devtest";
    String space = "devspace";

    TanzuApplicationServiceInfrastructure tanzuApplicationServiceInfrastructure =
        TanzuApplicationServiceInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(pcfConnector))
            .organization(ParameterField.createValueField(org))
            .space(ParameterField.createValueField(space))
            .build();

    TanzuApplicationServiceInfrastructureOutcome expectedOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder()
            .connectorRef(pcfConnector)
            .organization(org)
            .space(space)
            .environment(environment)
            .infrastructureKey("8d27ebf01280cd9d93840db85e22bc910b604418")
            .build();

    expectedOutcome.setConnector(Connector.builder().name("my_connector").build());

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(tanzuApplicationServiceInfrastructure,
        null, environment, serviceOutcome, "accountId", "orgId", "projectId", new HashMap<>());

    assertThat(infrastructureOutcome).isEqualTo(expectedOutcome);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testEcsInfraMapper() {
    EcsInfrastructure ecsInfrastructure = EcsInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .region(ParameterField.createValueField("region"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .build();

    EcsInfrastructureOutcome expectedOutcome = EcsInfrastructureOutcome.builder()
                                                   .connectorRef("connectorId")
                                                   .region("region")
                                                   .cluster("cluster")
                                                   .environment(environment)
                                                   .infrastructureKey("4e88dbd8bc5e4694fe1c72d90371e127a8bc3d1c")
                                                   .build();

    expectedOutcome.setConnector(Connector.builder().name("my_connector").build());

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        ecsInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(expectedOutcome);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAsgInfraMapper() {
    final String baseAsg = "baseAsg";
    AsgInfrastructure asgInfrastructure = AsgInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .region(ParameterField.createValueField("region"))
                                              .baseAsgName(ParameterField.createValueField(baseAsg))
                                              .build();

    AsgInfrastructureOutcome expectedOutcome = AsgInfrastructureOutcome.builder()
                                                   .connectorRef("connectorId")
                                                   .region("region")
                                                   .environment(environment)
                                                   .infrastructureKey("f9497b14ff1b27911470c2fe1683bd66358ebd4f")
                                                   .baseAsgName(baseAsg)
                                                   .build();

    expectedOutcome.setConnector(Connector.builder().name("my_connector").build());

    AsgInfrastructureOutcome infrastructureOutcome = (AsgInfrastructureOutcome) infrastructureMapper.toOutcome(
        asgInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome.getRegion()).isEqualTo(expectedOutcome.getRegion());
    assertThat(infrastructureOutcome.getBaseAsgName()).isEqualTo(expectedOutcome.getBaseAsgName());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testK8sAwsInfraMapper() {
    K8sAwsInfrastructure k8sAwsInfrastructure = K8sAwsInfrastructure.builder()
                                                    .connectorRef(ParameterField.createValueField("connectorId"))
                                                    .namespace(ParameterField.createValueField("namespace"))
                                                    .releaseName(ParameterField.createValueField("release"))
                                                    .cluster(ParameterField.createValueField("cluster"))
                                                    .build();

    K8sAwsInfrastructureOutcome k8sAwsInfrastructureOutcome =
        K8sAwsInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("54874007d7082ff0ab54cd51865954f5e78c5c88")
            .infrastructureKeyShort("548740")
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        k8sAwsInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(k8sAwsInfrastructureOutcome);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGoogleFunctionsInfraMapper() {
    GoogleFunctionsInfrastructure googleFunctionsInfrastructure =
        GoogleFunctionsInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorId"))
            .region(ParameterField.createValueField("region"))
            .project(ParameterField.createValueField("project"))
            .build();

    GoogleFunctionsInfrastructureOutcome googleFunctionsInfrastructureOutcome =
        GoogleFunctionsInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .region("region")
            .project("project")
            .environment(environment)
            .infrastructureKey("9de2869e6ff4a3ec81fa7805b9a6fed5267906fe")
            .build();

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(googleFunctionsInfrastructure, null,
        environment, serviceOutcome, "accountId", "projId", "orgId", new HashMap<>());
    assertThat(infrastructureOutcome).isEqualTo(googleFunctionsInfrastructureOutcome);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testSetInfraIdentifierAndName_InfrastructureDetailsAbstract() {
    InfrastructureOutcomeAbstract k8SDirectInfrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    infrastructureMapper.setInfraIdentifierAndName(k8SDirectInfrastructureOutcome, "Identifier", "Name");
    assertThat(k8SDirectInfrastructureOutcome.getInfraIdentifier()).isEqualTo("Identifier");
    assertThat(k8SDirectInfrastructureOutcome.getInfraName()).isEqualTo("Name");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testK8sGcpInfraMapperWithTags() {
    Map<String, String> tags = Map.of("type", "k8sGCP", "geo", "india", "prod", "");

    K8sGcpInfrastructure k8SGcpInfrastructure = K8sGcpInfrastructure.builder()
                                                    .connectorRef(ParameterField.createValueField("connectorId"))
                                                    .namespace(ParameterField.createValueField("namespace"))
                                                    .releaseName(ParameterField.createValueField("release"))
                                                    .cluster(ParameterField.createValueField("cluster"))
                                                    .build();

    K8sGcpInfrastructureOutcome k8sGcpInfrastructureOutcome =
        K8sGcpInfrastructureOutcome.builder()
            .connectorRef("connectorId")
            .namespace("namespace")
            .releaseName("release")
            .cluster("cluster")
            .environment(environment)
            .infrastructureKey("54874007d7082ff0ab54cd51865954f5e78c5c88")
            .infrastructureKeyShort("548740")
            .build();

    k8sGcpInfrastructureOutcome.setTags(tags);

    InfrastructureOutcome infrastructureOutcome = infrastructureMapper.toOutcome(
        k8SGcpInfrastructure, null, environment, serviceOutcome, "accountId", "projId", "orgId", tags);
    assertThat(infrastructureOutcome).isEqualTo(k8sGcpInfrastructureOutcome);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sDirectReleaseName() {
    String serviceReleaseName = "K8sRelease";
    String infraReleaseName = "InfraK8sRelease";
    ServiceStepOutcome serviceStepOutcomeWithRelease =
        ServiceStepOutcome.builder().release(HarnessRelease.builder().name(serviceReleaseName).build()).build();
    ServiceStepOutcome serviceStepOutcomeWithoutRelease = ServiceStepOutcome.builder().build();

    K8SDirectInfrastructure directInfrastructure = K8SDirectInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connectorId"))
                                                       .namespace(ParameterField.createValueField("namespace"))
                                                       .releaseName(ParameterField.createValueField(infraReleaseName))
                                                       .build();

    K8sDirectInfrastructureOutcome infrastructureOutcome =
        (K8sDirectInfrastructureOutcome) infrastructureMapper.toOutcome(directInfrastructure, null, environment,
            serviceStepOutcomeWithRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(serviceReleaseName);

    infrastructureOutcome = (K8sDirectInfrastructureOutcome) infrastructureMapper.toOutcome(directInfrastructure, null,
        environment, serviceStepOutcomeWithoutRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(infraReleaseName);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sGcpReleaseName() {
    String serviceReleaseName = "K8sRelease";
    String infraReleaseName = "InfraK8sRelease";
    ServiceStepOutcome serviceStepOutcomeWithRelease =
        ServiceStepOutcome.builder().release(HarnessRelease.builder().name(serviceReleaseName).build()).build();
    ServiceStepOutcome serviceStepOutcomeWithoutRelease = ServiceStepOutcome.builder().build();

    K8sGcpInfrastructure infrastructure = K8sGcpInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .namespace(ParameterField.createValueField("namespace"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .releaseName(ParameterField.createValueField(infraReleaseName))
                                              .build();

    K8sGcpInfrastructureOutcome infrastructureOutcome =
        (K8sGcpInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null, environment,
            serviceStepOutcomeWithRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(serviceReleaseName);

    infrastructureOutcome = (K8sGcpInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null,
        environment, serviceStepOutcomeWithoutRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(infraReleaseName);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sAzureReleaseName() {
    String serviceReleaseName = "K8sRelease";
    String infraReleaseName = "InfraK8sRelease";
    ServiceStepOutcome serviceStepOutcomeWithRelease =
        ServiceStepOutcome.builder().release(HarnessRelease.builder().name(serviceReleaseName).build()).build();
    ServiceStepOutcome serviceStepOutcomeWithoutRelease = ServiceStepOutcome.builder().build();

    K8sAzureInfrastructure infrastructure = K8sAzureInfrastructure.builder()
                                                .connectorRef(ParameterField.createValueField("connectorId"))
                                                .namespace(ParameterField.createValueField("namespace"))
                                                .cluster(ParameterField.createValueField("cluster"))
                                                .releaseName(ParameterField.createValueField(infraReleaseName))
                                                .resourceGroup(ParameterField.createValueField("res-group"))
                                                .subscriptionId(ParameterField.createValueField("sub-id"))
                                                .build();

    K8sAzureInfrastructureOutcome infrastructureOutcome =
        (K8sAzureInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null, environment,
            serviceStepOutcomeWithRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(serviceReleaseName);

    infrastructureOutcome = (K8sAzureInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null,
        environment, serviceStepOutcomeWithoutRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(infraReleaseName);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sAwsReleaseName() {
    String serviceReleaseName = "K8sRelease";
    String infraReleaseName = "InfraK8sRelease";
    ServiceStepOutcome serviceStepOutcomeWithRelease =
        ServiceStepOutcome.builder().release(HarnessRelease.builder().name(serviceReleaseName).build()).build();
    ServiceStepOutcome serviceStepOutcomeWithoutRelease = ServiceStepOutcome.builder().build();

    K8sAwsInfrastructure infrastructure = K8sAwsInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("connectorId"))
                                              .namespace(ParameterField.createValueField("namespace"))
                                              .cluster(ParameterField.createValueField("cluster"))
                                              .releaseName(ParameterField.createValueField(infraReleaseName))
                                              .build();

    K8sAwsInfrastructureOutcome infrastructureOutcome =
        (K8sAwsInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null, environment,
            serviceStepOutcomeWithRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(serviceReleaseName);

    infrastructureOutcome = (K8sAwsInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null,
        environment, serviceStepOutcomeWithoutRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(infraReleaseName);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testK8sRancherReleaseName() {
    String serviceReleaseName = "K8sRelease";
    String infraReleaseName = "InfraK8sRelease";
    ServiceStepOutcome serviceStepOutcomeWithRelease =
        ServiceStepOutcome.builder().release(HarnessRelease.builder().name(serviceReleaseName).build()).build();
    ServiceStepOutcome serviceStepOutcomeWithoutRelease = ServiceStepOutcome.builder().build();

    K8sRancherInfrastructure infrastructure = K8sRancherInfrastructure.builder()
                                                  .connectorRef(ParameterField.createValueField("connectorId"))
                                                  .namespace(ParameterField.createValueField("namespace"))
                                                  .cluster(ParameterField.createValueField("cluster"))
                                                  .releaseName(ParameterField.createValueField(infraReleaseName))
                                                  .build();

    K8sRancherInfrastructureOutcome infrastructureOutcome =
        (K8sRancherInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null, environment,
            serviceStepOutcomeWithRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(serviceReleaseName);

    infrastructureOutcome = (K8sRancherInfrastructureOutcome) infrastructureMapper.toOutcome(infrastructure, null,
        environment, serviceStepOutcomeWithoutRelease, "accountId", "projId", "orgId", Collections.emptyMap());
    assertThat(infrastructureOutcome.getReleaseName()).isEqualTo(infraReleaseName);
  }
}
