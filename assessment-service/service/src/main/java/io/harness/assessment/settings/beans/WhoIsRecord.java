/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhoIsRecord {
  @JsonProperty("WhoisRecord") private Record whoIsRecord;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Record {
    @JsonProperty("createdDate") private String createdDate;

    @JsonProperty("updatedDate") private String updatedDate;

    @JsonProperty("expiresDate") private String expiresDate;

    @JsonProperty("registrant") private Registrant registrant;

    @JsonProperty("administrativeContact") private Contact administrativeContact;

    @JsonProperty("technicalContact") private Contact technicalContact;

    @JsonProperty("domainName") private String domainName;

    @JsonProperty("nameServers") private NameServers nameServers;

    @JsonProperty("status") private String status;

    @JsonProperty("parseCode") private int parseCode;

    @JsonProperty("header") private String header;

    @JsonProperty("strippedText") private String strippedText;

    @JsonProperty("footer") private String footer;

    @JsonProperty("audit") private Audit audit;

    @JsonProperty("registrarName") private String registrarName;

    @JsonProperty("registrarIANAID") private String registrarIANAID;

    @JsonProperty("createdDateNormalized") private String createdDateNormalized;

    @JsonProperty("updatedDateNormalized") private String updatedDateNormalized;

    @JsonProperty("expiresDateNormalized") private String expiresDateNormalized;

    @JsonProperty("registryData") private RegistryData registryData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Registrant {
      private String name;
      private String organization;
      private String street1;
      private String city;
      private String state;
      private String postalCode;
      private String country;
      private String countryCode;
      private String email;
      private String telephone;
      private String fax;
      private String rawText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
      private String name;
      private String organization;
      private String street1;
      private String city;
      private String state;
      private String postalCode;
      private String country;
      private String countryCode;
      private String email;
      private String telephone;
      private String fax;
      private String rawText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NameServers {
      private String rawText;
      private String[] hostNames;
      private String[] ips;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Audit {
      private String createdDate;
      private String updatedDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegistryData {
      private String createdDate;
      private String updatedDate;
      private String expiresDate;
      private String domainName;
      private NameServers nameServers;
    }
  }
}
