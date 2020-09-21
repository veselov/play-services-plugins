/**
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.oss.licenses.plugin;

import static org.gradle.internal.impldep.org.testng.Assert.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LicensesTask} */
@SuppressWarnings("ReadWriteStringCanBeUsed")
@RunWith(JUnit4.class)
public class LicensesTaskTest {
  private static final Charset UTF_8 = StandardCharsets.UTF_8;
  private static final String BASE_DIR = "src/test/resources";
  private static final String LINE_BREAK = System.getProperty("line.separator");
  private Project project;
  private LicensesTask licensesTask;

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() throws IOException {
    File outputDir = temporaryFolder.newFolder();
    File outputLicenses = new File(outputDir, "testLicenses");

    project = ProjectBuilder.builder().withProjectDir(new File(BASE_DIR)).build();
    licensesTask = project.getTasks().create("generateLicenses", LicensesTask.class);

    licensesTask.outputDir = outputDir;
    licensesTask.licenses = outputLicenses;
  }

  private void createLicenseZip(String name) throws IOException {
    File zipFile = new File(name);
    ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFile));
    File input = new File(BASE_DIR + "/sampleLicenses");
    for (File file : input.listFiles()) {
      ZipEntry entry = new ZipEntry(file.getName());
      byte[] bytes = Files.readAllBytes(file.toPath());
      output.putNextEntry(entry);
      output.write(bytes, 0, bytes.length);
      output.closeEntry();
    }
    output.close();
  }

  @Test
  public void testInitOutputDir() {
    licensesTask.initOutputDir();

    assertTrue(licensesTask.outputDir.exists());
  }

  @Test
  public void testInitLicenseFile() throws IOException {
    licensesTask.initLicenseFile();

    assertTrue(licensesTask.licenses.exists());
    assertEquals((LicensesTask.getHEADER() + LINE_BREAK).length(), Files.size(licensesTask.licenses.toPath()));
  }

  @Test
  public void testIsGranularVersion_True() {
    String versionTrue = "14.6.0";
    assertTrue(licensesTask.isGranularVersion(versionTrue));
  }

  @Test
  public void testIsGranularVersion_False() {
    String versionFalse = "11.4.0";
    assertFalse(licensesTask.isGranularVersion(versionFalse));
  }

  @Test
  public void testAddLicensesFromPom() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    String version1 = "1";
    licensesTask.addLicensesFromPom(deps1, group1, name1, version1);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected = ",,\"groupA deps1 (groupA:deps1)\",1,x,N,,,\"MIT License\",http://www.opensource.org/licenses/mit-license.php,,,," + LINE_BREAK;
    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withoutDuplicate() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    String version1 = "1";
    licensesTask.addLicensesFromPom(deps1, group1, name1, version1);

    File deps2 = getResourceFile("dependencies/groupB/bcd/deps2.pom");
    String name2 = "deps2";
    String group2 = "groupB";
    String version2 = "1";
    licensesTask.addLicensesFromPom(deps2, group2, name2, version2);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected =
        ",,\"groupA deps1 (groupA:deps1)\",1,x,N,,,\"MIT License\",http://www.opensource.org/licenses/mit-license.php,,,,"
            + LINE_BREAK
            + ",,\"groupA deps1 (groupB:deps2)\",1,x,N,,,\"Apache License, version 2.0\",https://www.apache.org/licenses/LICENSE-2.0,,,,"
            + LINE_BREAK;

    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withMultiple() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    String version1 = "1";
    licensesTask.addLicensesFromPom(deps1, group1, name1, version1);

    File deps2 = getResourceFile("dependencies/groupE/deps5.pom");
    String name2 = "deps5";
    String group2 = "groupE";
    String version2 = "1";
    licensesTask.addLicensesFromPom(deps2, group2, name2, version2);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected =
        ",,\"groupA deps1 (groupA:deps1)\",1,x,N,,,\"MIT License\",http://www.opensource.org/licenses/mit-license.php,,,,"
            + LINE_BREAK
            + ",,\"groupE deps5 (groupE:deps5)\",1,5,N,,,\"MIT License\",http://www.opensource.org/licenses/mit-license.php,,,,"
            + LINE_BREAK
            + ",,\"groupE deps5 (groupE:deps5)\",1,5,N,,,\"Apache License, version 2.0\",https://www.apache.org/licenses/LICENSE-2.0,,,,"
            + LINE_BREAK;

    assertEquals(expected, content);
  }

  @Test
  public void testAddLicensesFromPom_withDuplicate() throws IOException {
    File deps1 = getResourceFile("dependencies/groupA/deps1.pom");
    String name1 = "deps1";
    String group1 = "groupA";
    String version1 = "1";
    licensesTask.addLicensesFromPom(deps1, group1, name1, version1);

    File deps2 = getResourceFile("dependencies/groupA/deps1.pom");
    String name2 = "deps1";
    String group2 = "groupA";
    String version2 = "1";
    licensesTask.addLicensesFromPom(deps2, group2, name2, version2);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected = ",,\"groupA deps1 (groupA:deps1)\",1,x,N,,,\"MIT License\",http://www.opensource.org/licenses/mit-license.php,,,," + LINE_BREAK;

    assertEquals(expected, content);
  }

  @Test
  public void testMissingLicense() throws IOException {
    File missingFile = getResourceFile("sample-missing-licenses.xml");
    licensesTask.loadMissingLicenses(missingFile);

    File deps2 = getResourceFile("no-license.pom");
    String name2 = "guava";
    String group2 = "guava";
    String version2 = "1";
    licensesTask.addLicensesFromPom(deps2, group2, name2, version2);

    File deps1 = getResourceFile("no-license2.pom");
    String name1 = "guava2";
    String group1 = "guava2";
    String version1 = "1";
    licensesTask.addLicensesFromPom(deps1, group1, name1, version1);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected = ",,\"no license (guava:guava)\",1,https://github.com/google/guava,N,,,\"Apache License, version 2.0\",http://www.apache.org/licenses/LICENSE-2.0.txt,,,," + LINE_BREAK;

    assertEquals(expected, content);
  }

  private File getResourceFile(String resourcePath) {
    return new File(getClass().getClassLoader().getResource(resourcePath).getFile());
  }

  @Test
  public void testGetBytesFromInputStream_throwException() throws IOException {
    InputStream inputStream = mock(InputStream.class);
    when(inputStream.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IOException());
    try {
      licensesTask.getBytesFromInputStream(inputStream, 1, 1);
      fail("This test should throw Exception.");
    } catch (RuntimeException e) {
      assertEquals("Failed to read license text.", e.getMessage());
    }
  }

  @Test
  public void testGetBytesFromInputStream_normalText() {
    String test = "test";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes(UTF_8));
    String content = new String(licensesTask.getBytesFromInputStream(inputStream, 1, 1), UTF_8);
    assertEquals("e", content);
  }

  @Test
  public void testGetBytesFromInputStream_specialCharacters() {
    String test = "Copyright © 1991-2017 Unicode";
    InputStream inputStream = new ByteArrayInputStream(test.getBytes(UTF_8));
    String content = new String(licensesTask.getBytesFromInputStream(inputStream, 4, 18), UTF_8);
    assertEquals("right © 1991-2017", content);
  }

  @Test
  @Ignore
  public void testAddGooglePlayServiceLicenses() throws IOException {
    File tempOutput = new File(licensesTask.outputDir, "dependencies/groupC");
    tempOutput.mkdirs();
    createLicenseZip(tempOutput.getPath() + "play-services-foo-license.aar");
    File artifact = new File(tempOutput.getPath() + "play-services-foo-license.aar");
    // licensesTask.addGooglePlayServiceLicenses(artifact);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected = "safeparcel,external,safeparcel" + LINE_BREAK + "\"JSR 305\",external,\"JSR 305\"" + LINE_BREAK;
    assertEquals(expected, content);
    assertThat(licensesTask.googleServiceLicenses.size(), is(2));
    assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"));
    assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"));
  }

  @Test
  @Ignore
  public void testAddGooglePlayServiceLicenses_withoutDuplicate() throws IOException {
    File groupC = new File(licensesTask.outputDir, "dependencies/groupC");
    groupC.mkdirs();
    createLicenseZip(groupC.getPath() + "/play-services-foo-license.aar");
    File artifactFoo = new File(groupC.getPath() + "/play-services-foo-license.aar");

    File groupD = new File(licensesTask.outputDir, "dependencies/groupD");
    groupD.mkdirs();
    createLicenseZip(groupD.getPath() + "/play-services-bar-license.aar");
    File artifactBar = new File(groupD.getPath() + "/play-services-bar-license.aar");

    // licensesTask.addGooglePlayServiceLicenses(artifactFoo);
    // licensesTask.addGooglePlayServiceLicenses(artifactBar);

    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    String expected = "safeparcel,external,safeparcel" + LINE_BREAK + "\"JSR 305\",external,\"JSR 305\"" + LINE_BREAK;
    assertEquals(expected, content);
    assertThat(licensesTask.googleServiceLicenses.size(), is(2));
    assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"));
    assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"));
  }

  @Test
  public void testAppendLicense() throws IOException {
    licensesTask.appendLicense("license1", "license1", "version1", "url", "https://www.apache.org/licenses/LICENSE-2.0");

    String expected = ",,license1,version1,url,N,,,\"Apache License, version 2.0\",https://www.apache.org/licenses/LICENSE-2.0,,,," + LINE_BREAK;
    String content = new String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8);
    assertEquals(expected, content);
  }

}
