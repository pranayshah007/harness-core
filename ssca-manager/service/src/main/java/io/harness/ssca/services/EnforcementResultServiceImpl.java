/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.ssca.beans.AllowLicense;
import io.harness.ssca.beans.AllowList.AllowListItem;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.beans.Supplier;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.BadRequestException;
import org.eclipse.jgit.ignore.internal.Strings;

public class EnforcementResultServiceImpl implements EnforcementResultService {
  @Override
  public List<EnforcementResultEntity> getEnforcementResults(List<NormalizedSBOMComponentEntity> violatedComponents,
      String violationType, String violationDetails, ArtifactEntity artifact, String enforcementId) {
    List<EnforcementResultEntity> result = new ArrayList<>();
    for (NormalizedSBOMComponentEntity component : violatedComponents) {
      EnforcementResultEntity entity = EnforcementResultEntity.builder()
                                           .enforcementID(enforcementId)
                                           .artifactId(component.getArtifactId())
                                           .tag(artifact.getTag())
                                           .imageName(artifact.getName())
                                           .accountId(component.getAccountID())
                                           .orgIdentifier(component.getOrgIdentifier())
                                           .projectIdentifier(component.getProjectIdentifier())
                                           .orchestrationID(artifact.getOrchestrationId())
                                           .violationType(violationType)
                                           .violationDetails(violationDetails)
                                           .name(component.getPackageName())
                                           .supplier(component.getPackageSupplierName())
                                           .supplierType(component.getOriginatorType())
                                           .packageManager(component.getPackageManager())
                                           .license(component.getPackageLicense())
                                           .purl(component.getPurl())
                                           .version(component.getPackageVersion())
                                           .build();

      result.add(entity);
    }

    return result;
  }

  @Override
  public String getViolationDetails(
      NormalizedSBOMComponentEntity pkg, AllowListItem allowListItem, AllowListRuleType type) {
    if (type == AllowListRuleType.ALLOW_LICENSE_ITEM) {
      return getLicenseViolationDetails(pkg.getPackageName(), pkg.getPackageLicense(), allowListItem.getLicenses());
    } else if (type == AllowListRuleType.ALLOW_SUPPLIER_ITEM) {
      return getSupplierViolationDetails(
          pkg.getPackageName(), pkg.getPackageSupplierName(), allowListItem.getSuppliers());
    } else if (type == AllowListRuleType.ALLOW_PURL_ITEM) {
      return getPurlViolationDetails(pkg.getPackageName(), pkg.getPurl(), allowListItem.getPurls());
    } else {
      throw new IllegalArgumentException(String.format("Violation Details not Implemented for type: %s", type.name()));
    }
  }

  @Override
  public String getViolationDetails(DenyListItem denyListItem) {
    String violation = String.format("%s is denied:", denyListItem.getName());
    Field[] fields = denyListItem.getClass().getDeclaredFields();

    for (Field f : fields) {
      String fieldName = f.getName();
      String fieldValue;
      try {
        f.setAccessible(true);
        fieldValue = (String) f.get(denyListItem);
      } catch (IllegalAccessException e) {
        throw new BadRequestException("Error getting field value");
      }

      if (fieldValue != null) {
        violation += String.format(" %s is %s,", fieldName, fieldValue);
      }
    }

    return violation.substring(0, violation.length() - 1);
  }

  private String getSupplierViolationDetails(String packageName, String packageSupplier, List<Supplier> suppliers) {
    List<String> allowedSuppliers = new ArrayList<>();
    for (Supplier supplier : suppliers) {
      if (supplier.getName() == null) {
        allowedSuppliers.add(supplier.getSupplier());
      } else if (supplier.getName() == packageName) {
        if (supplier.getSupplier().contains("!")) {
          String deniedSupplier = Strings.split(supplier.getSupplier(), '!').get(1);
          return String.format("Supplier for %s should not be %s", packageName, deniedSupplier);
        }
        return String.format(
            "Supplier for %s needs to be: %s, but got: %s", packageName, supplier.getSupplier(), packageSupplier);
      }
    }

    return String.format("Supplier for %s needs to be: %s, but got: %s", packageName,
        String.join(" or ", allowedSuppliers), packageSupplier);
  }

  private String getLicenseViolationDetails(
      String packageName, List<String> packageLicense, List<AllowLicense> licenses) {
    List<String> allowedLicenses = new ArrayList<>();
    for (AllowLicense license : licenses) {
      if (license.getName() == null) {
        allowedLicenses.add(license.getLicense());
      } else if (license.getName() == packageName) {
        if (license.getLicense().contains("!")) {
          String deniedLicense = Strings.split(license.getLicense(), '!').get(1);
          return String.format("License for %s should not be %s", packageName, deniedLicense);
        }
        return String.format(
            "License for %s needs to be: %s, but got: %s", packageName, license.getLicense(), packageLicense);
      }
    }

    return String.format("License for %s needs to be: %s, but got: %s", packageName,
        String.join(" or ", allowedLicenses), packageLicense);
  }

  private String getPurlViolationDetails(String packageName, String packagePurl, List<String> purls) {
    for (String purl : purls) {
      if (purl.contains(packageName)) {
        return String.format("Purl for %s needs to be of the format %s, but got: %s", packageName, purl, packagePurl);
      }
    }
    return null;
  }
}
