/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.instanceutilization;

import static io.harness.rule.OwnerRule.ANMOL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.NGCommonEntityConstants;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.AzureVmUtilisationDTO;
import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
import io.harness.ccm.graphql.core.recommendation.AzureCpuUtilisationService;
import io.harness.ccm.graphql.core.recommendation.EC2InstanceUtilizationService;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UtilizationQueryTest extends CategoryTest {
  private UtilizationQuery utilizationQueryUnderTest;

  private ResolutionEnvironment env;
  private List<EC2InstanceUtilizationData> ec2InstanceUtilizationData;
  private List<AzureVmUtilisationDTO> azureVmUtilisationDTOS;

  private final String ACCOUNT_ID = "accountId";
  private final String INSTANCE_ID = "instanceId";
  private final String AZURE_VM_ID = "azureVmId";

  @Before
  public void setUp() throws Exception {
    utilizationQueryUnderTest = new UtilizationQuery();
    utilizationQueryUnderTest.graphQLUtils = mock(GraphQLUtils.class);
    utilizationQueryUnderTest.azureCpuUtilisationService = mock(AzureCpuUtilisationService.class);
    utilizationQueryUnderTest.ec2InstanceUtilizationService = mock(EC2InstanceUtilizationService.class);
    GraphQLContext graphQLContext =
        GraphQLContext.newContext().of(NGCommonEntityConstants.ACCOUNT_KEY, ACCOUNT_ID).build();
    DataFetchingEnvironment dataFetchingEnvironment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                                                          .context(graphQLContext)
                                                          .variables(ImmutableMap.of())
                                                          .build();
    env = new ResolutionEnvironment(null, dataFetchingEnvironment, null, null, null, null);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUtilData() {
    ec2InstanceUtilizationData = getEc2InstanceUtilizationData();
    when(utilizationQueryUnderTest.graphQLUtils.getAccountIdentifier(any(ResolutionEnvironment.class)))
        .thenReturn(ACCOUNT_ID);
    when(utilizationQueryUnderTest.ec2InstanceUtilizationService.getEC2InstanceUtilizationData(ACCOUNT_ID, INSTANCE_ID))
        .thenReturn(ec2InstanceUtilizationData);

    final List<EC2InstanceUtilizationData> result = utilizationQueryUnderTest.utilData(INSTANCE_ID, env);

    assertThat(result).isEqualTo(ec2InstanceUtilizationData);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUtilData_EC2InstanceUtilizationServiceReturnsNull() {
    when(utilizationQueryUnderTest.graphQLUtils.getAccountIdentifier(any(ResolutionEnvironment.class)))
        .thenReturn(ACCOUNT_ID);
    when(utilizationQueryUnderTest.ec2InstanceUtilizationService.getEC2InstanceUtilizationData(ACCOUNT_ID, INSTANCE_ID))
        .thenReturn(null);

    final List<EC2InstanceUtilizationData> result = utilizationQueryUnderTest.utilData(INSTANCE_ID, env);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testUtilData_EC2InstanceUtilizationServiceReturnsNoItems() {
    when(utilizationQueryUnderTest.graphQLUtils.getAccountIdentifier(any(ResolutionEnvironment.class)))
        .thenReturn(ACCOUNT_ID);
    when(utilizationQueryUnderTest.ec2InstanceUtilizationService.getEC2InstanceUtilizationData(ACCOUNT_ID, INSTANCE_ID))
        .thenReturn(Collections.emptyList());

    final List<EC2InstanceUtilizationData> result = utilizationQueryUnderTest.utilData(INSTANCE_ID, env);

    assertThat(result).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAzureVmUtilData() {
    azureVmUtilisationDTOS = getAzureVmUtilisationDTOS();
    when(utilizationQueryUnderTest.graphQLUtils.getAccountIdentifier(any(ResolutionEnvironment.class)))
        .thenReturn(ACCOUNT_ID);
    when(utilizationQueryUnderTest.azureCpuUtilisationService.getAzureVmCpuUtilisationData(AZURE_VM_ID, ACCOUNT_ID, 0))
        .thenReturn(azureVmUtilisationDTOS);

    final List<AzureVmUtilisationDTO> result = utilizationQueryUnderTest.azureVmUtilData(AZURE_VM_ID, 0, env);

    assertThat(result).isEqualTo(azureVmUtilisationDTOS);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testAzureVmUtilData_AzureCpuUtilisationServiceReturnsNoItems() {
    when(utilizationQueryUnderTest.graphQLUtils.getAccountIdentifier(any(ResolutionEnvironment.class)))
        .thenReturn(ACCOUNT_ID);
    when(utilizationQueryUnderTest.azureCpuUtilisationService.getAzureVmCpuUtilisationData(AZURE_VM_ID, ACCOUNT_ID, 0))
        .thenReturn(Collections.emptyList());

    final List<AzureVmUtilisationDTO> result = utilizationQueryUnderTest.azureVmUtilData(AZURE_VM_ID, 0, env);

    assertThat(result).isEqualTo(Collections.emptyList());
  }

  private List<EC2InstanceUtilizationData> getEc2InstanceUtilizationData() {
    return List.of(EC2InstanceUtilizationData.builder()
                       .avgcpu(9.7)
                       .avgmemory(18.4)
                       .maxcpu(18.7)
                       .maxmemory(24.56)
                       .starttime(1684454400000L)
                       .endtime(1684540800000L)
                       .build());
  }

  private List<AzureVmUtilisationDTO> getAzureVmUtilisationDTOS() {
    return List.of(AzureVmUtilisationDTO.builder()
                       .vmId(AZURE_VM_ID)
                       .averageCpu(9.7)
                       .maxCpu(18.7)
                       .startTime(1684454400000L)
                       .endTime(1684540800000L)
                       .build());
  }
}
