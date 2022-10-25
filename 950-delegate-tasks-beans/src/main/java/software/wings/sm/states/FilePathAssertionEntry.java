/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.sm.states;

import io.harness.serializer.XmlUtils;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Created by sgurubelli on 8/31/17.
 */
public class FilePathAssertionEntry {
  @Attributes(title = "Artifact / File Path") String filePath;
  @Attributes(title = "Assertion") String assertion;
  @SchemaIgnore Status status;
  @SchemaIgnore String fileData;

  public FilePathAssertionEntry() {}

  public FilePathAssertionEntry(String filePath, String assertion, String fileData) {
    this.filePath = filePath;
    this.assertion = assertion;
    this.fileData = fileData;
  }

  public FilePathAssertionEntry(String filePath, String assertion, Status status) {
    this.filePath = filePath;
    this.assertion = assertion;
    this.status = status;
  }

  /**
   * Getter for property 'fileData'.
   *
   * @return Value for property 'fileData'.
   */
  public String getFileData() {
    return fileData;
  }

  /**
   * Setter for property 'fileData'.
   *
   * @param fileData Value to set for property 'fileData'.
   */
  public void setFileData(String fileData) {
    this.fileData = fileData;
  }

  /**
   * Getter for property 'filePath'.
   *
   * @return Value for property 'filePath'.
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Setter for property 'filePath'.
   *
   * @param filePath Value to set for property 'filePath'.
   */
  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  /**
   * Getter for property 'assertion'.
   *
   * @return Value for property 'assertion'.
   */
  public String getAssertion() {
    return assertion;
  }

  /**
   * Setter for property 'assertion'.
   *
   * @param assertion Value to set for property 'assertion'.
   */
  public void setAssertion(String assertion) {
    this.assertion = assertion;
  }

  /**
   * Getter for property 'status'.
   *
   * @return Value for property 'status'.
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Setter for property 'status'.
   *
   * @param status Value to set for property 'status'.
   */
  public void setStatus(Status status) {
    this.status = status;
  }

  /**
   * Xml format.
   *
   * @return true, if successful
   */
  public boolean xmlFormat() {
    try {
      document();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Xpath.
   *
   * @param path the path
   * @return the string
   */
  public String xpath(String path) {
    try {
      return XmlUtils.xpath(document(), path);
    } catch (Exception e) {
      return null;
    }
  }

  private Document document() throws ParserConfigurationException, SAXException, IOException {
    return XmlUtils.parse(fileData);
  }

  public enum Status { NOT_FOUND, SUCCESS, FAILED }
}
