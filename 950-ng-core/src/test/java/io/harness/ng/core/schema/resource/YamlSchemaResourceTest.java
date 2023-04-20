/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.schema.resource;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.schema.YamlBaseUrlService;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.Path;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class YamlSchemaResourceTest extends CategoryTest {
  @Mock YamlBaseUrlService yamlBaseUrlService;
  @InjectMocks @Spy YamlSchemaResource yamlSchemaResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn("http://harness.io/").when(yamlBaseUrlService).getBaseUrl();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetSchemaFile() throws IOException, NoSuchMethodException {
    final String testfile = "testSchema.json";
    InputStream testSchema = getClass().getClassLoader().getResourceAsStream(testfile);
    doReturn(testSchema).when(yamlSchemaResource).getResourceAsStream(testfile);
    ArgumentCaptor<File> fileArgumentCaptor = ArgumentCaptor.forClass(File.class);
    yamlSchemaResource.getSchemaFile(testfile);
    verify(yamlSchemaResource, times(1)).buildResponse(any(), fileArgumentCaptor.capture());
    final File file = fileArgumentCaptor.getValue();
    String updatedString = FileUtils.readFileToString(file, Charsets.UTF_8.name());
    InputStream testSchemaOutput = getClass().getClassLoader().getResourceAsStream("testSchemaOutput.json");
    File file1 = File.createTempFile("file1", ".json");
    yamlSchemaResource.copyInputStreamToFile(testSchemaOutput, file1);
    String expected = FileUtils.readFileToString(file1, Charsets.UTF_8.name());
    yamlSchemaResource.copyInputStreamToFile(testSchemaOutput, file1);
    assertThat(expected).isEqualTo(updatedString);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void assureGetSchemaFileMethodExists() throws NoSuchMethodException {
    assertThat(YamlSchemaResource.class.getMethod("getSchemaFile", String.class).getAnnotation(Path.class).value())
        .isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void assureClassHasPathAnnotation() {
    assertThat(YamlSchemaResource.class.getAnnotation(Path.class).value()).isNotNull();
  }
}
