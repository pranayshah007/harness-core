/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import io.harness.cvng.beans.CVNGTaskMetadataConstants;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
@Slf4j
public class CVNGTaskMetadataUtils {
  public static CVNGLogTag getCvngLogTag(String tagKey, String tagValue) {
    return CVNGLogTag.builder().key(tagKey).value(tagValue).type(CVNGLogTag.TagType.STRING).build();
  }

  public static List<CVNGLogTag> getDataCollectionMetadataTags(DataCollectionTaskDTO.DataCollectionTaskResult result) {
    List<CVNGLogTag> cvngLogTags = new ArrayList<>();
    Map<String, String> dataCollectionMetadata = result.getDataCollectionMetadata();
    if (result.getDataCollectionMetadata() != null) {
      CVNGLogTag cvngLogTagGroupId = CVNGTaskMetadataUtils.getCvngLogTag(
          CVNGTaskMetadataConstants.GROUP_ID, dataCollectionMetadata.get(CVNGTaskMetadataConstants.GROUP_ID));
      CVNGLogTag cvngLogTagQueryId = CVNGTaskMetadataUtils.getCvngLogTag(
          CVNGTaskMetadataConstants.QUERY_IDS, dataCollectionMetadata.get(CVNGTaskMetadataConstants.QUERY_IDS));
      CVNGLogTag cvngLogTagDelegate = CVNGTaskMetadataUtils.getCvngLogTag(
          CVNGTaskMetadataConstants.DELEGATE_ID, dataCollectionMetadata.get(CVNGTaskMetadataConstants.DELEGATE_ID));
      cvngLogTags.addAll(List.of(cvngLogTagDelegate, cvngLogTagGroupId, cvngLogTagQueryId));
    }
    return cvngLogTags;
  }

  public static List<CVNGLogTag> getCvngLogTagsForTask(String dataCollectionTaskUuid) {
    CVNGLogTag cvngLogTagTaskId =
        CVNGTaskMetadataUtils.getCvngLogTag(CVNGTaskMetadataConstants.TASK_ID, dataCollectionTaskUuid);
    return new ArrayList<>(List.of(cvngLogTagTaskId));
  }

  public static String humanReadableFormat(Duration duration) {
    return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();
  }

  public static List<CVNGLogTag> getTaskDurationTags(Duration duration) {
    String timeReadableFormat = CVNGTaskMetadataUtils.humanReadableFormat(duration);
    List<CVNGLogTag> cvngLogTags = new ArrayList<>();
    CVNGLogTag cvngLogTagDuration =
        CVNGTaskMetadataUtils.getCvngLogTag(CVNGTaskMetadataConstants.DURATION, timeReadableFormat);
    cvngLogTags.add(cvngLogTagDuration);
    return cvngLogTags;
  }

  public static Map<String, String> getDataCollectionInfoMetadata(
      CVConfig cvConfig, VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    Map<String, String> dataCollectionMetadata = new HashMap<>();
    try {
      String queryId = "";
      String groupId = "";
      if (cvConfig instanceof NextGenLogCVConfig) {
        queryId = ((NextGenLogCVConfig) cvConfig).getQueryName();
        groupId = ((NextGenLogCVConfig) cvConfig).getGroupName();
      } else if (cvConfig instanceof LogCVConfig) {
        queryId = ((LogCVConfig) cvConfig).getQueryName();
      } else if (cvConfig instanceof MetricCVConfig) {
        groupId = ((MetricCVConfig<AnalysisInfo>) cvConfig).maybeGetGroupName().orElse("DEFAULT_GROUP");
        queryId = ((MetricCVConfig<AnalysisInfo>) cvConfig)
                      .getMetricInfos()
                      .stream()
                      .filter(metricInfo -> metricInfo.getDeploymentVerification().isEnabled())
                      .map(AnalysisInfo::getMetricName)
                      .collect(Collectors.toList())
                      .toString();
      }
      if (StringUtils.isNotEmpty(queryId)) {
        dataCollectionMetadata.put(CVNGTaskMetadataConstants.QUERY_IDS, queryId);
      }
      if (StringUtils.isNotEmpty(groupId)) {
        dataCollectionMetadata.put(CVNGTaskMetadataConstants.GROUP_ID, groupId);
      }
      dataCollectionMetadata.put(CVNGTaskMetadataConstants.HEALTH_SOURCE_ID, cvConfig.getFullyQualifiedIdentifier());
      dataCollectionMetadata.put(
          CVNGTaskMetadataConstants.VERIFICATION_JOB_INSTANCE_ID, verificationJobInstance.getUuid());
      dataCollectionMetadata.put(CVNGTaskMetadataConstants.VERIFICATION_TASK_ID, verificationTaskId);
    } catch (Exception ignored) {
      log.error("Cannot parse metadata for CVConfig : {} , Verification Job Instance : {}", cvConfig.getUuid(),
          verificationJobInstance.getUuid());
    }
    return dataCollectionMetadata;
  }
}
