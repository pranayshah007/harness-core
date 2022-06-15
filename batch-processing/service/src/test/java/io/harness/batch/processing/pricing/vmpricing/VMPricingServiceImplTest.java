/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.vmpricing;

import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.pricing.client.CloudInfoPricingClient;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ProductDetailsResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
public class VMPricingServiceImplTest extends CategoryTest {
  @InjectMocks private VMPricingServiceImpl vmPricingService;
  @Mock private CloudInfoPricingClient banzaiPricingClient;
  private static final String REGION = "us-east-1";
  private static final String COMPUTE_SERVICE = "compute";
  private static final String DEFAULT_INSTANCE_FAMILY = "c4.8xlarge";
  private static final double DEFAULT_INSTANCE_CPU = 36;
  private static final double DEFAULT_INSTANCE_MEMORY = 60;
  private static final double DEFAULT_INSTANCE_PRICE = 1.60;
  private static final double MAX_RELATIVE_ERROR_PCT = 5;

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetComputeVMPricingInfo() throws IOException {
    Call<ProductDetailsResponse> pricingInfoCall = mock(Call.class);
    when(pricingInfoCall.execute()).thenReturn(createPricingResponse());
    when(banzaiPricingClient.getPricingInfo(CloudProvider.AWS.getCloudProviderName(), COMPUTE_SERVICE, REGION))
        .thenReturn(pricingInfoCall);
    ProductDetails computeVMPricingInfo =
        vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS);
    assertThat(computeVMPricingInfo).isNotNull();
    assertThat(computeVMPricingInfo.getCpusPerVm()).isEqualTo(DEFAULT_INSTANCE_CPU);
    assertThat(computeVMPricingInfo.getMemPerVm()).isEqualTo(DEFAULT_INSTANCE_MEMORY);
    assertThat(computeVMPricingInfo.getOnDemandPrice()).isEqualTo(DEFAULT_INSTANCE_PRICE);
    assertThat(computeVMPricingInfo.getType()).isEqualTo(DEFAULT_INSTANCE_FAMILY);
    ProductDetails computeVMPricingInfoCached =
        vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS);
    assertThat(computeVMPricingInfoCached).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetComputeVMPricingInfoAzure() throws IOException {
    Call<ProductDetailsResponse> pricingInfoCall = mock(Call.class);
    when(pricingInfoCall.execute()).thenReturn(createPricingResponse());
    when(banzaiPricingClient.getPricingInfo(CloudProvider.AZURE.getCloudProviderName(), COMPUTE_SERVICE, "uksouth"))
        .thenReturn(pricingInfoCall);
    ProductDetails computeVMPricingInfo =
        vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, "germanywestcentral", CloudProvider.AZURE);
    assertThat(computeVMPricingInfo).isNotNull();
    assertThat(computeVMPricingInfo.getCpusPerVm()).isEqualTo(DEFAULT_INSTANCE_CPU);
    assertThat(computeVMPricingInfo.getMemPerVm()).isEqualTo(DEFAULT_INSTANCE_MEMORY);
    assertThat(computeVMPricingInfo.getOnDemandPrice()).isEqualTo(DEFAULT_INSTANCE_PRICE);
    assertThat(computeVMPricingInfo.getType()).isEqualTo(DEFAULT_INSTANCE_FAMILY);
    ProductDetails computeVMPricingInfoCached =
        vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, "germanywestcentral", CloudProvider.AZURE);
    assertThat(computeVMPricingInfoCached).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetHardCodedComputeVMPricingInfo() throws IOException {
    ProductDetails computeVMPricingInfo =
        vmPricingService.getComputeVMPricingInfo("n2-standard-16", REGION, CloudProvider.GCP);
    assertThat(computeVMPricingInfo).isNotNull();
    assertThat(computeVMPricingInfo.getCpusPerVm()).isEqualTo(16.0);
    assertThat(computeVMPricingInfo.getMemPerVm()).isEqualTo(64.0);
    assertThat(computeVMPricingInfo.getOnDemandPrice()).isEqualTo(0.7769);
    assertThat(computeVMPricingInfo.getType()).isEqualTo("n2-standard-16");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetCustomComputeVMPricingInfo() throws IOException {
    Call<ProductDetailsResponse> pricingInfoCall = mock(Call.class);
    when(pricingInfoCall.execute()).thenReturn(createPricingResponse());
    when(banzaiPricingClient.getPricingInfo(CloudProvider.GCP.getCloudProviderName(), COMPUTE_SERVICE, REGION))
        .thenReturn(pricingInfoCall);

    ProductDetails computeVMPricingInfo =
        vmPricingService.getComputeVMPricingInfo("e2-custom-12-32768", REGION, CloudProvider.GCP);

    assertThat(computeVMPricingInfo).isNotNull();
    assertThat(computeVMPricingInfo.getCpusPerVm()).isEqualTo(12.0D);
    assertThat(computeVMPricingInfo.getMemPerVm()).isEqualTo(32.0D);
    assertThat(computeVMPricingInfo.getOnDemandPrice()).isCloseTo(0.372824, withinPercentage(MAX_RELATIVE_ERROR_PCT));
    assertThat(computeVMPricingInfo.getSpotPrice().get(0).getPrice())
        .isCloseTo(0.111844, withinPercentage(MAX_RELATIVE_ERROR_PCT));
    assertThat(computeVMPricingInfo.getType()).isEqualTo("e2-custom-12-32768");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testShouldReturnNullVMPricingInfo() throws IOException {
    Call<ProductDetailsResponse> pricingInfoCall = mock(Call.class);
    when(pricingInfoCall.execute()).thenThrow(IOException.class);
    when(banzaiPricingClient.getPricingInfo(CloudProvider.AWS.getCloudProviderName(), COMPUTE_SERVICE, REGION))
        .thenReturn(pricingInfoCall);
    ProductDetails computeVMPricingInfo =
        vmPricingService.getComputeVMPricingInfo(DEFAULT_INSTANCE_FAMILY, REGION, CloudProvider.AWS);
    assertThat(computeVMPricingInfo).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetFargatePricingInfo() {
    EcsFargatePricingInfo fargatePricingInfo = vmPricingService.getFargatePricingInfo(REGION);
    assertThat(fargatePricingInfo).isNotNull();
    assertThat(fargatePricingInfo.getCpuPrice()).isGreaterThan(0d);
    assertThat(fargatePricingInfo.getMemoryPrice()).isGreaterThan(0d);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testFetchInstancePriceShouldIgnoreCase() throws IOException {
    Call<ProductDetailsResponse> pricingInfoCall = mock(Call.class);
    when(pricingInfoCall.execute())
        .thenReturn(
            createPricingResponse(ProductDetails.builder().type("Standard_D16s_v3").onDemandPrice(10D).build()));
    when(banzaiPricingClient.getPricingInfo(
             eq(CloudProvider.AZURE.getCloudProviderName()), eq(COMPUTE_SERVICE), eq(REGION)))
        .thenReturn(pricingInfoCall);

    ProductDetails pricingInfo =
        vmPricingService.getComputeVMPricingInfo("standard_d16s_v3", REGION, CloudProvider.AZURE);

    assertThat(pricingInfo).isNotNull();
    assertThat(pricingInfo.getType()).isEqualTo("Standard_D16s_v3");
    assertThat(pricingInfo.getOnDemandPrice()).isEqualTo(10D);
  }

  private static Response createPricingResponse() {
    ProductDetails productDetails = ProductDetails.builder()
                                        .cpusPerVm(DEFAULT_INSTANCE_CPU)
                                        .memPerVm(DEFAULT_INSTANCE_MEMORY)
                                        .onDemandPrice(DEFAULT_INSTANCE_PRICE)
                                        .type(DEFAULT_INSTANCE_FAMILY)
                                        .build();

    return createPricingResponse(productDetails);
  }

  private static Response createPricingResponse(ProductDetails productDetails) {
    ProductDetailsResponse pricingResponse =
        ProductDetailsResponse.builder().products(Arrays.asList(productDetails)).build();
    return Response.success(pricingResponse);
  }
}
