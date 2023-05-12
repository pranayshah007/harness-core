/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ami;

import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.RUSHABH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class AmiServiceImplTest extends WingsBaseTest {
  @Mock private AwsHelperService awsHelperService;
  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGetFiltersWithNullEmptyValues() {
    AmiServiceImpl service = new AmiServiceImpl();
    List<Filter> filters = service.getFilters(null, null);
    assertThat(filters.size()).isEqualTo(2);
    assertThat(filters.get(0).getName()).isEqualTo("is-public");
    assertThat(filters.get(0).getValues().size()).isEqualTo(1);
    assertThat(filters.get(0).getValues().get(0)).isEqualTo("false");

    assertThat(filters.get(1).getName()).isEqualTo("state");
    assertThat(filters.get(1).getValues().size()).isEqualTo(1);
    assertThat(filters.get(1).getValues().get(0)).isEqualTo("available");

    filters = service.getFilters(new HashMap<>(), new HashMap<>());
    assertThat(filters.size()).isEqualTo(2);
    assertThat(filters.get(0).getName()).isEqualTo("is-public");
    assertThat(filters.get(0).getValues().size()).isEqualTo(1);
    assertThat(filters.get(0).getValues().get(0)).isEqualTo("false");

    assertThat(filters.get(1).getName()).isEqualTo("state");
    assertThat(filters.get(1).getValues().size()).isEqualTo(1);
    assertThat(filters.get(1).getValues().get(0)).isEqualTo("available");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldFetchAMIBuilds() {
    AmiServiceImpl amiServiceImpl = new AmiServiceImpl();
    on(amiServiceImpl).set("awsHelperService", awsHelperService);
    List<Image> images = asList(new Image()
                                    .withImageId("1")
                                    .withName("Image1")
                                    .withCreationDate("1")
                                    .withOwnerId("1")
                                    .withImageType("AMI")
                                    .withTags(new Tag().withKey("abc").withValue("efg")),
        new Image()
            .withImageId("2")
            .withName("Image2")
            .withCreationDate("2")
            .withOwnerId("2")
            .withImageType("AMI")
            .withTags(new Tag().withKey("abc1").withValue("efg1"),
                new Tag().withKey("k8s.io/cluster/test-cluster").withValue("owned")));

    when(awsHelperService.desribeEc2Images(any(), any(), any(), any()))
        .thenReturn(new DescribeImagesResult().withImages(images));
    AwsConfig awsConfig = AwsConfig.builder()
                              .accessKey("AKIAJLEKM45P4PO5QUFQ".toCharArray())
                              .secretKey("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".toCharArray())
                              .build();

    List<BuildDetails> buildDetails = amiServiceImpl.getBuilds(awsConfig, null, "US-east", null, null, 50);
    List<String> builds = buildDetails.stream().map(BuildDetails::getNumber).collect(Collectors.toList());
    assertThat(builds).isEqualTo(asList("Image1", "Image2"));
    assertThat(buildDetails.get(1).getMetadata()).hasSize(3);
    assertThat(buildDetails.get(1).getMetadata().containsKey("k8s.io/cluster/test-cluster")).isFalse();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGetFiltersWithTags() {
    AmiServiceImpl service = new AmiServiceImpl();
    Map<String, List<String>> tagMap = new HashMap<>();
    tagMap.put("tagkey", asList("tagValue1", "tagValue2"));
    List<Filter> filters = service.getFilters(tagMap, new HashMap<>());
    assertThat(filters.size()).isEqualTo(3);
    assertThat(filters.get(0).getName()).isEqualTo("is-public");
    assertThat(filters.get(0).getValues().size()).isEqualTo(1);
    assertThat(filters.get(0).getValues().get(0)).isEqualTo("false");

    assertThat(filters.get(1).getName()).isEqualTo("state");
    assertThat(filters.get(1).getValues().size()).isEqualTo(1);
    assertThat(filters.get(1).getValues().get(0)).isEqualTo("available");

    assertThat(filters.get(2).getName()).isEqualTo("tag:tagkey");
    assertThat(filters.get(2).getValues().size()).isEqualTo(2);
    assertThat(filters.get(2).getValues().get(0)).isEqualTo("tagValue1");
    assertThat(filters.get(2).getValues().get(1)).isEqualTo("tagValue2");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGetFiltersWithFilterMap() {
    AmiServiceImpl service = new AmiServiceImpl();
    Map<String, String> filterMap = new HashMap<>();
    filterMap.put("ami-key1", "value1");
    filterMap.put("ami-key2", "value2");
    filterMap.put("ami-key3", "value3");
    filterMap.put("ami-key4", "");
    filterMap.put("ami-key5", null);
    filterMap.put(null, null);
    filterMap.put(null, "value6");
    filterMap.put("", "value7");
    filterMap.put("wrongkey", "value8");

    List<Filter> filters = service.getFilters(new HashMap<>(), filterMap);

    assertThat(filters.size()).isEqualTo(5);
    assertThat(filters.get(0).getName()).isEqualTo("is-public");
    assertThat(filters.get(0).getValues().size()).isEqualTo(1);
    assertThat(filters.get(0).getValues().get(0)).isEqualTo("false");

    assertThat(filters.get(1).getName()).isEqualTo("state");
    assertThat(filters.get(1).getValues().size()).isEqualTo(1);
    assertThat(filters.get(1).getValues().get(0)).isEqualTo("available");

    assertThat(filters.get(2).getName()).isEqualTo("key1");
    assertThat(filters.get(2).getValues().size()).isEqualTo(1);
    assertThat(filters.get(2).getValues().get(0)).isEqualTo("value1");

    assertThat(filters.get(3).getName()).isEqualTo("key2");
    assertThat(filters.get(3).getValues().size()).isEqualTo(1);
    assertThat(filters.get(3).getValues().get(0)).isEqualTo("value2");

    assertThat(filters.get(4).getName()).isEqualTo("key3");
    assertThat(filters.get(4).getValues().size()).isEqualTo(1);
    assertThat(filters.get(4).getValues().get(0)).isEqualTo("value3");
  }
}
