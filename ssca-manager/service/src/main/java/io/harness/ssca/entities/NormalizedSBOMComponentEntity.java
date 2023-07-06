/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.entities;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@OwnedBy(SSCA)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "NormalizedSBOMEntityKeys")
@StoreIn(DbAliases.SSCA)
@Entity(value = "sbomComponent", noClassnameStored = true)
@Document("sbomComponent")
@TypeAlias("sbomComponent")
@HarnessEntity(exportable = true)
public class NormalizedSBOMComponentEntity implements PersistentEntity {
  @Field("orchestrationid") String orchestrationId;
  @Field("sbomversion") String sbomVersion;

  @Field("artifacturl") String artifactURL;
  @Field("artifactid") String artifactId;
  @Field("artifactname") String artifactName;
  List<String> tags;
  @Field("createdon") Instant createdOn;

  @Field("toolversion") String toolVersion;
  @Field("toolname") String toolName;
  @Field("toolvendor") String toolVendor;

  @Field("packageid") String packageID;
  @Field("packagename") String packageName;
  @Field("packagedescription") String packageDescription;
  @Field("packagelicense") List<String> packageLicense;
  @Field("packagesourceinfo") String packageSourceInfo;
  @Field("packageversion") String packageVersion;
  @Field("packagesuppliername") String packageSupplierName;
  @Field("packageoriginatorname") String packageOriginatorName;
  @Field("originatortype") String originatorType;
  @Field("packagetype") String packageType;
  @Field("packagecpe") String packageCPE;
  @Field("packageproperties") String packageProperties;
  String purl;
  @Field("packagemanager") String packageManager; // this will be parsed from the purl
  @Field("packagenamespace") String packageNamespace; // this will be parsed from the purl

  @Field("majorversion") int majorVersion;
  @Field("minorversion") int minorVersion;
  @Field("patchversion") int patchVersion;

  @Field("pipelineidentifier") String pipelineIdentifier;
  @Field("projectidentifier") String projectIdentifier;
  @Field("orgidentifier") String orgIdentifier;
  @Field("sequenceid") String sequenceID;
  @Field("accountid") String accountID;
}
