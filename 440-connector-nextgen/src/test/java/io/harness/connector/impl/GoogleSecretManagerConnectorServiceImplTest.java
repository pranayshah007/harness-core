/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorsTestBase;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.PL)
public class GoogleSecretManagerConnectorServiceImplTest extends ConnectorsTestBase {
  @Inject @InjectMocks GoogleSecretManagerConnectorServiceImpl googleSecretManagerConnectorService;
  private final List<String> gcpRegions =
      Stream
          .of("us-east1", "us-east2", "us-east4", "us-west1", "us-west2", "us-west3", "us-west4", "us-central1",
              "us-central2", "us-central3", "us-central4", "eu-west1", "eu-west2", "eu-west3", "eu-west4", "eu-west6",
              "eu-north1", "eu-central1", "eu-central2", "eu-central3", "eu-central4", "eu-south1", "eu-south2",
              "eu-south3", "eu-south4", "asia-east1", "asia-east2", "asia-northeast1", "asia-southeast1",
              "asia-southeast2", "asia-south1", "asia-south2", "asia-south3", "asia-south4", "northamerica-northeast1",
              "southamerica-east1")
          .collect(Collectors.toList());
  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testGcpRegions() {
    List<String> gcpRegions = googleSecretManagerConnectorService.getGcpRegions();
    assertThat(gcpRegions).isEqualTo(gcpRegions);
  }
}
