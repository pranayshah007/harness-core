/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.EcrConfig;
import software.wings.common.BuildDetailsComparatorAscending;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by brett on 7/15/17
 */
@OwnedBy(CDC)
@Singleton
public class EcrClassicServiceImpl implements EcrClassicService {
  @Inject private AwsHelperService awsHelperService;
  @Inject private EncryptionService encryptionService;

  @Override
  public List<BuildDetails> getBuilds(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String imageName, int maxNumberOfBuilds) {
    List<BuildDetails> buildDetails = new ArrayList<>();
    ListImagesResult listImagesResult;
    ListImagesRequest listImagesRequest = new ListImagesRequest().withRepositoryName(imageName);
    do {
      listImagesResult = awsHelperService.listEcrImages(ecrConfig, encryptionDetails, listImagesRequest);
      listImagesResult.getImageIds()
          .stream()
          .filter(imageIdentifier -> imageIdentifier != null && isNotEmpty(imageIdentifier.getImageTag()))
          .forEach(imageIdentifier
              -> buildDetails.add(BuildDetails.Builder.aBuildDetails()
                                      .withNumber(imageIdentifier.getImageTag())
                                      .withUiDisplayName("Tag# " + imageIdentifier.getImageTag())
                                      .build()));
      listImagesRequest.setNextToken(listImagesResult.getNextToken());
    } while (listImagesRequest.getNextToken() != null);
    // Sorting at build tag for docker artifacts.
    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  @Override
  public BuildDetails getLastSuccessfulBuild(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String imageName) {
    return null;
  }

  @Override
  public boolean verifyRepository(
      EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails, String repositroyName) {
    return listEcrRegistry(ecrConfig, encryptionDetails).contains(repositroyName);
  }

  @Override
  public boolean validateCredentials(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(ecrConfig, encryptionDetails, false);
    awsHelperService.validateAwsAccountCredential(ecrConfig.getAccessKey(), ecrConfig.getSecretKey());
    return true;
  }

  @Override
  public List<String> listEcrRegistry(EcrConfig ecrConfig, List<EncryptedDataDetail> encryptionDetails) {
    List<String> repoNames = new ArrayList<>();
    DescribeRepositoriesRequest describeRepositoriesRequest = new DescribeRepositoriesRequest();
    DescribeRepositoriesResult describeRepositoriesResult;
    do {
      describeRepositoriesResult =
          awsHelperService.listRepositories(ecrConfig, encryptionDetails, describeRepositoriesRequest);
      describeRepositoriesResult.getRepositories().forEach(repository -> repoNames.add(repository.getRepositoryName()));
      describeRepositoriesRequest.setNextToken(describeRepositoriesResult.getNextToken());
    } while (describeRepositoriesRequest.getNextToken() != null);

    return repoNames;
  }

  @Override
  public String getEcrImageUrl(EcrConfig ecrConfig, String imageName) {
    String registry = ecrConfig.getEcrUrl().substring(8);
    if (!registry.endsWith("/")) {
      registry += "/";
    }

    return registry + imageName;
  }
}
