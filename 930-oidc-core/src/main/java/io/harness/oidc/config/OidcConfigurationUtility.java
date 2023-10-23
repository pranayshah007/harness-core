/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PL)
@Data
@Slf4j
public class OidcConfigurationUtility {
  public static final String GENERATE_AT_RUNTIME = "GENERATE_AT_RUNTIME";
  private io.harness.oidc.config.OidcConfigStructure.OidcTokenStructure gcpOidcTokenStructure;
  private ObjectMapper objectMapper;

  public OidcConfigurationUtility(String oidcConfigFile) {
    objectMapper = new ObjectMapper();
    try {
      File oidcIdTokenConfigFile = new File(oidcConfigFile);
      io.harness.oidc.config.OidcConfigStructure oidcConfigStructure =
          objectMapper.readValue(oidcIdTokenConfigFile, io.harness.oidc.config.OidcConfigStructure.class);
      this.gcpOidcTokenStructure = oidcConfigStructure.getGcpOidcToken();
    } catch (JsonProcessingException ex) {
      log.error("Json read error encountered while doing OidcIdTokenConfiguration : {}", ex);
    } catch (IOException ex) {
      log.error("File read error encountered while doing OidcIdTokenConfiguration : {}", ex);
    } catch (Exception ex) {
      log.error("Exception encountered while doing OidcIdTokenConfiguration : {}", ex);
    }
  }
}
