/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDetailsAbstract;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.evaluators.CDExpressionEvaluator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.common.collect.Maps;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class InfrastructureProvisionerMapperTest extends CategoryTest {
  @Spy private CDExpressionEvaluator evaluator;

  @InjectMocks private InfrastructureProvisionerMapper infrastructureProvisionerMapper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testToOutcome() {
    HashMap<String, String> hostAttributes = populateHostAttributes();

    Infrastructure pdcInfrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                           .hostObjectArray(ParameterField.createValueField(hostObjectArray))
                                           .hostAttributes(ParameterField.createValueField(hostAttributes))
                                           .hostFilter(getAllHostFilter())
                                           .build();

    ((InfrastructureDetailsAbstract) pdcInfrastructure).setInfraName("infraName");
    ((InfrastructureDetailsAbstract) pdcInfrastructure).setInfraIdentifier("infraIdentifier");

    PdcInfrastructureOutcome pdcInfrastructureOutcome =
        (PdcInfrastructureOutcome) infrastructureProvisionerMapper.toOutcome(
            pdcInfrastructure, EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build());

    assertThat(pdcInfrastructureOutcome.getCredentialsRef()).isEqualTo("sshKeyRef");
    assertThat(pdcInfrastructureOutcome.getHosts())
        .containsExactlyInAnyOrder(
            "ec2-85-201-252-114.compute-1.amazonaws.com", "ec2-52-201-252-114.compute-1.amazonaws.com");
    assertThat(pdcInfrastructureOutcome.getInfrastructureKey()).isEqualTo("0ebad79c13bd2f86edbae354b72b4d2a410f3bab");
    assertThat(pdcInfrastructureOutcome.getHostFilter()).isEqualTo(getAllHostFilterDTO());
    assertThat(pdcInfrastructureOutcome.getInfraName()).isEqualTo("infraName");
    assertThat(pdcInfrastructureOutcome.getInfraIdentifier()).isEqualTo("infraIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testOutcomeWithInvalidJson() {
    HashMap<String, String> hostAttributes = populateHostAttributes();
    Infrastructure pdcInfrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
            .hostObjectArray(ParameterField.createValueField("Not valid JSON"))
            .hostAttributes(ParameterField.createValueField(hostAttributes))
            .hostFilter(HostFilter.builder().type(HostFilterType.HOST_ATTRIBUTES).build())
            .build();

    assertThatThrownBy(()
                           -> infrastructureProvisionerMapper.toOutcome(pdcInfrastructure,
                               EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build()))
        .hasMessage("Host object array JSON cannot be parsed")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateHostAttributesWithoutHostnameValue() {
    HashMap<String, String> hostAttributes = populateHostAttributesWithoutHostnameValue();
    Infrastructure pdcInfrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
            .hostObjectArray(ParameterField.createValueField("Not valid JSON"))
            .hostAttributes(ParameterField.createValueField(hostAttributes))
            .hostFilter(HostFilter.builder().type(HostFilterType.HOST_ATTRIBUTES).build())
            .build();

    assertThatThrownBy(()
                           -> infrastructureProvisionerMapper.toOutcome(pdcInfrastructure,
                               EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build()))
        .hasMessage("[hostname] property value cannot be null or empty")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateHostAttributesWithoutHostname() {
    HashMap<String, String> hostAttributes = populateHostAttributesWithoutHostname();
    Infrastructure pdcInfrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                           .hostObjectArray(ParameterField.createValueField(hostObjectArray))
                                           .hostAttributes(ParameterField.createValueField(hostAttributes))
                                           .hostFilter(HostFilter.builder().type(HostFilterType.ALL).build())
                                           .build();

    assertThatThrownBy(()
                           -> infrastructureProvisionerMapper.toOutcome(pdcInfrastructure,
                               EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build()))
        .hasMessage("[hostname] property is mandatory for all host objects")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testOutcomeWithEmptyJson() {
    HashMap<String, String> hostAttributes = populateHostAttributes();
    Infrastructure pdcInfrastructure =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("sshKeyRef"))
            .hostObjectArray(ParameterField.createValueField(""))
            .hostAttributes(ParameterField.createValueField(hostAttributes))
            .hostFilter(HostFilter.builder().type(HostFilterType.HOST_ATTRIBUTES).build())
            .build();

    assertThatThrownBy(()
                           -> infrastructureProvisionerMapper.toOutcome(pdcInfrastructure,
                               EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build()))
        .hasMessage("Host object array JSON cannot be null or empty")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testToOutcomeWithInvalidHostFilter() {
    HashMap<String, String> hostAttributes = populateHostAttributes();
    Infrastructure pdcInfrastructure = PdcInfrastructure.builder()
                                           .credentialsRef(ParameterField.createValueField("sshKeyRef"))
                                           .hostObjectArray(ParameterField.createValueField(hostObjectArray))
                                           .hostAttributes(ParameterField.createValueField(hostAttributes))
                                           .hostFilter(HostFilter.builder().type(HostFilterType.HOST_NAMES).build())
                                           .build();

    assertThatThrownBy(()
                           -> infrastructureProvisionerMapper.toOutcome(pdcInfrastructure,
                               EnvironmentOutcome.builder().build(), ServiceStepOutcome.builder().build()))
        .hasMessage("Unsupported host filter type found for dynamically provisioned infrastructure: HostNames")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @NotNull
  private HashMap<String, String> populateHostAttributes() {
    HashMap<String, String> hostAttributes = Maps.newHashMap();
    hostAttributes.put("hostname", "public_dns");
    hostAttributes.put("SubnetId", "public_ip");
    hostAttributes.put("hostnameType", "private_dns_name_options[0].hostname_type");
    hostAttributes.put("httpEnabled", "metadata_options.http_endpoint");
    return hostAttributes;
  }

  @NotNull
  private HashMap<String, String> populateHostAttributesWithoutHostname() {
    HashMap<String, String> hostAttributes = Maps.newHashMap();
    hostAttributes.put("SubnetId", "public_ip");
    return hostAttributes;
  }

  @NotNull
  private HashMap<String, String> populateHostAttributesWithoutHostnameValue() {
    HashMap<String, String> hostAttributes = Maps.newHashMap();
    hostAttributes.put("hostname", "");
    hostAttributes.put("SubnetId", "public_ip");
    return hostAttributes;
  }

  private HostFilter getAllHostFilter() {
    return HostFilter.builder().type(HostFilterType.ALL).build();
  }

  private HostFilterDTO getAllHostFilterDTO() {
    return HostFilterDTO.builder().type(HostFilterType.ALL).spec(AllHostsFilterDTO.builder().build()).build();
  }

  private final String hostObjectArray = "[\n"
      + "   {\n"
      + "      \"ami\":\"ami-41e0b93b\",\n"
      + "      \"arn\":\"arn:aws:ec2:us-east-1:806630305776:instance/i-0620d1be29fb2307b\",\n"
      + "      \"associate_public_ip_address\":true,\n"
      + "      \"availability_zone\":\"us-east-1d\",\n"
      + "      \"private_ip\":\"172.31.29.127\",\n"
      + "      \"public_dns\":\"ec2-85-201-252-114.compute-1.amazonaws.com\",\n"
      + "      \"public_ip\":\"52.201.252.115\",\n"
      + "      \"private_dns_name_options\":[\n"
      + "         {\n"
      + "            \"enable_resource_name_dns_a_record\":false,\n"
      + "            \"enable_resource_name_dns_aaaa_record\":false,\n"
      + "            \"hostname_type\":\"ip-name\"\n"
      + "         }\n"
      + "      ],\n"
      + "      \"metadata_options\":{\n"
      + "         \"http_endpoint\":\"enabled\",\n"
      + "         \"http_put_response_hop_limit\":1,\n"
      + "         \"http_tokens\":\"optional\",\n"
      + "         \"instance_metadata_tags\":\"disabled\"\n"
      + "      }\n"
      + "   },\n"
      + "   {\n"
      + "      \"ami\":\"ami-41e0b93b\",\n"
      + "      \"arn\":\"arn:aws:ec2:us-east-1:806630305776:instance/i-0a70c33e38f15637c\",\n"
      + "      \"associate_public_ip_address\":true,\n"
      + "      \"availability_zone\":\"us-east-1d\",\n"
      + "      \"private_ip\":\"172.31.29.126\",\n"
      + "      \"public_dns\":\"ec2-52-201-252-114.compute-1.amazonaws.com\",\n"
      + "      \"public_ip\":\"52.201.252.114\",\n"
      + "      \"private_dns_name_options\":[\n"
      + "         {\n"
      + "            \"enable_resource_name_dns_a_record\":false,\n"
      + "            \"enable_resource_name_dns_aaaa_record\":false,\n"
      + "            \"hostname_type\":\"ip-name\"\n"
      + "         }\n"
      + "      ]\n"
      + "   }\n"
      + "]";
}
