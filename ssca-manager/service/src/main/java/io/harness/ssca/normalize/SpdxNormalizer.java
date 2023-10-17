/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.normalize;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ssca.beans.SettingsDTO;
import io.harness.ssca.beans.SpdxDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMComponentEntityBuilder;
import io.harness.ssca.utils.SBOMUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;

@Slf4j
public class SpdxNormalizer implements Normalizer<SpdxDTO> {
  @Override
  public List<NormalizedSBOMComponentEntity> normaliseSBOM(SpdxDTO sbom, SettingsDTO settings) throws ParseException {
    List<NormalizedSBOMComponentEntity> sbomEntityList = new ArrayList<>();

    for (SpdxDTO.Package spdxPackage : sbom.getPackages()) {
      NormalizedSBOMComponentEntityBuilder normalizedSBOMEntityBuilder =
          NormalizedSBOMComponentEntity.builder()
              .sbomVersion(sbom.getSpdxVersion())
              .artifactUrl(settings.getArtifactURL())
              .artifactId(settings.getArtifactID())
              .artifactName(sbom.getName())
              .tags(Collections.singletonList(settings.getArtifactTag()))
              .createdOn(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                             .parse(sbom.getCreationInfo().getCreated())
                             .toInstant())
              .toolVersion(settings.getTool().getVersion())
              .toolName(settings.getTool().getName())
              .toolVendor(settings.getTool().getVendor())
              .packageId(spdxPackage.getSPDXID())
              .packageName(spdxPackage.getName())
              .packageDescription(spdxPackage.getDescription())
              .packageLicense(getPackageLicenses(spdxPackage.getLicenseConcluded(), spdxPackage.getLicenseDeclared()))
              .packageVersion(spdxPackage.getVersionInfo())
              .packageSourceInfo(spdxPackage.getSourceInfo())
              .orchestrationId(settings.getOrchestrationID())
              .pipelineIdentifier(settings.getPipelineIdentifier())
              .projectIdentifier(settings.getProjectIdentifier())
              .orgIdentifier(settings.getOrgIdentifier())
              .accountId(settings.getAccountID());

      if (spdxPackage.getOriginator() != null && spdxPackage.getOriginator().contains(":")) {
        String[] splitOriginator = Strings.split(spdxPackage.getOriginator(), ':');
        normalizedSBOMEntityBuilder.originatorType(splitOriginator[0].trim());
        normalizedSBOMEntityBuilder.packageOriginatorName(splitOriginator[1].trim());
      } else {
        normalizedSBOMEntityBuilder.packageOriginatorName(spdxPackage.getOriginator());
      }

      List<Integer> versionInfo = SBOMUtils.getVersionInfo(spdxPackage.getVersionInfo());
      normalizedSBOMEntityBuilder.majorVersion(versionInfo.get(0));
      normalizedSBOMEntityBuilder.minorVersion(versionInfo.get(1));
      normalizedSBOMEntityBuilder.patchVersion(versionInfo.get(2));

      try {
        List<String> packageManagerInfo = getPackageManagerSpdx(spdxPackage);
        normalizedSBOMEntityBuilder.purl(packageManagerInfo.get(0));
        normalizedSBOMEntityBuilder.packageNamespace(packageManagerInfo.get(1));
        normalizedSBOMEntityBuilder.packageManager(packageManagerInfo.get(2));
        if (packageManagerInfo.get(3) != null) {
          normalizedSBOMEntityBuilder.packageName(packageManagerInfo.get(3));
        }
      } catch (InvalidArgumentsException e) {
        log.error(String.format("Error Message: %s, Stacktrace: %s", e.getMessage(), e.getStackTrace()));
        continue;
      }

      sbomEntityList.add(normalizedSBOMEntityBuilder.build());
    }
    return sbomEntityList;
  }

  private List<String> getPackageLicenses(String licenseConcluded, String licenseDeclared) {
    String packageLicenseExpression = "NO_ASSERTION";
    if (EmptyPredicate.isNotEmpty(licenseConcluded) && !SBOMUtils.NO_ASSERTION_LIST.contains(licenseConcluded)) {
      packageLicenseExpression = licenseConcluded;
    } else if (EmptyPredicate.isNotEmpty(licenseDeclared)) {
      packageLicenseExpression = licenseDeclared;
    }
    List<String> licenses = SBOMUtils.processExpression(packageLicenseExpression);

    for (int i = 0; i < licenses.size(); i++) {
      if (licenses.get(i).contains(SBOMUtils.LICENSE_REF_DELIM)) {
        String newLicense = licenses.get(i).split(SBOMUtils.LICENSE_REF_DELIM)[1];
        licenses.set(i, newLicense);
      }
    }
    return licenses;
  }

  private List<String> getPackageManagerSpdx(SpdxDTO.Package spdxPackage) {
    String packagePurl = null;
    String packageNamespace = null;
    String packageManager = null;
    String packageName = null;
    for (SpdxDTO.Package.ExternalRefs externalRef : spdxPackage.getExternalRefs()) {
      if (externalRef.getReferenceCategory().equals(SBOMUtils.EXTERNAL_REF_CATEGORY_PURL)) {
        String purl = externalRef.getReferenceLocator();
        if (purl == null) {
          throw new InvalidArgumentsException("Invalid Purl");
        }
        packagePurl = purl;

        String[] splitPurl = Strings.split(purl, SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_PRIMARY);
        if (splitPurl.length != 2 && !splitPurl[0].equals("pkg")) {
          log.error(String.format("Invalid purl: %s", purl));
          throw new InvalidArgumentsException("Invalid Purl");
        }
        splitPurl = Strings.split(splitPurl[1], SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_SECONDAY);
        if (splitPurl.length < 2) {
          log.error(String.format("Invalid purl: %s", purl));
          throw new InvalidArgumentsException("Invalid Purl");
        }
        if (splitPurl.length > 2) {
          // if purl is of the format pkg:<package-manager>/<namespace>/<name>@<version>
          packageNamespace = splitPurl[1];
          // if the purl includes package of the format <name>@<version>
          if (splitPurl[splitPurl.length - 1].indexOf(SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_TERTIARY) > -1) {
            String[] splitPackage =
                Strings.split(splitPurl[splitPurl.length - 1], SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_TERTIARY);
            packageName = splitPackage[0];
          }
        }
        packageManager = splitPurl[0];
      }
    }
    return Arrays.asList(packagePurl, packageNamespace, packageManager, packageName);
  }
}
