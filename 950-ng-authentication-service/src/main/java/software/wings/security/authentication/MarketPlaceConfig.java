/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secret.ConfigSecret;

import com.google.inject.Singleton;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
@Singleton
public class MarketPlaceConfig {
  @ConfigSecret private String awsAccessKey;
  @ConfigSecret private String awsSecretKey;
  @ConfigSecret private String awsMarketPlaceProductCode; // will deprecate
  @ConfigSecret private String awsMarketPlaceCeProductCode; // will deprecate
  @ConfigSecret private String awsMarketPlaceFfProductCode;
  @ConfigSecret private String awsMarketPlaceCiProductCode;
  @ConfigSecret private String awsMarketPlaceSrmProductCode;
  @ConfigSecret private String awsMarketPlaceStoProductCode;
  @ConfigSecret private String awsMarketPlaceCdProductCode;
  @ConfigSecret private String awsMarketPlaceCcmProductCode;
  @ConfigSecret private String azureMarketplaceAccessKey;
  @ConfigSecret private String azureMarketplaceSecretKey;
}
