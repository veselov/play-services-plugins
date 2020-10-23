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

package com.google.android.gms.oss.licenses.plugin

import groovy.json.JsonSlurper
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.slf4j.LoggerFactory

/**
 * Task to find available licenses from the artifacts stored in the json
 * file generated by DependencyTask, and then generate the third_party_licenses
 * and third_party_license_metadata file.
 */
class LicensesTask extends DefaultTask {
    private static final String UTF_8 = "UTF-8"
    private static final byte[] LINE_SEPARATOR = System
            .getProperty("line.separator").getBytes(UTF_8)
    static final String HEADER = 'ID,"Vendor of the SW Component","Name of the SW Component","Version of the SW Component","Source of the SW Component","Is the SW Component modified(Y/N)","Author(s) of the Modification(s)","Copyright Information","Name & Version of the SW-License","Link to SW Component License","Text to be displayed","Additional License Conditions","Integration for the SW Component","Superordinate SW Component"'
    private static final int GRANULAR_BASE_VERSION = 14
    private static final String GOOGLE_PLAY_SERVICES_GROUP =
        "com.google.android.gms"
    // private static final String LICENSE_ARTIFACT_SUFFIX = "-license"
    private static final String FIREBASE_GROUP = "com.google.firebase"
    private static final String FAIL_READING_LICENSES_ERROR =
        "Failed to read license text."

    private static final logger = LoggerFactory.getLogger(LicensesTask.class)

    protected Set<String> googleServiceLicenses = []
    protected Set<String> addedLicenses = []

    @InputFile
    public File dependenciesJson

    @InputFile
    public Property<File> missingLicenses

    public Map<ArtifactID, GPathResult> missingLicenseInfo = new HashMap<>()

    @OutputDirectory
    public File outputDir

    @OutputFile
    public File licenses

    @TaskAction
    void action() {
        initOutputDir()
        initLicenseFile()

        def allDependencies = new JsonSlurper().parse(dependenciesJson)
        for (entry in allDependencies) {
            String group = entry.group
            String name = entry.name
            // String fileLocation = entry.fileLocation
            String version = entry.version
            // File artifactLocation = new File(fileLocation)

            if (isGoogleServices(group)) {
                /*
                // Add license info for google-play-services itself
                if (!name.endsWith(LICENSE_ARTIFACT_SUFFIX)) {
                    addLicensesFromPom(group, name, version)
                }
                // Add transitive licenses info for google-play-services. For
                // post-granular versions, this is located in the artifact
                // itself, whereas for pre-granular versions, this information
                // is located at the complementary license artifact as a runtime
                // dependency.
                if (isGranularVersion(version)) {
                    addGooglePlayServiceLicenses(artifactLocation)
                } else if (name.endsWith(LICENSE_ARTIFACT_SUFFIX)) {
                    addGooglePlayServiceLicenses(artifactLocation)
                }
                 */
                throw new IllegalArgumentException("Google services dependencies not supported")
            } else {
                addLicensesFromPom(group, name, version)
            }
        }

    }

    protected void initOutputDir() {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }

    protected void initLicenseFile() {
        if (licenses == null) {
            logger.error("License file is undefined")
        }
        licenses.newWriter().withWriter {w ->
            w << HEADER
        }
        licenses.append(LINE_SEPARATOR)

        if (missingLicenses != null && missingLicenses.isPresent()) {
            loadMissingLicenses(missingLicenses.get());
        }

    }

    protected static boolean isGoogleServices(String group) {
        return (GOOGLE_PLAY_SERVICES_GROUP.equalsIgnoreCase(group)
                || FIREBASE_GROUP.equalsIgnoreCase(group))
    }

    protected static boolean isGranularVersion (String version) {
        String[] versions = version.split("\\.")
        return (versions.length > 0
                && Integer.valueOf(versions[0]) >= GRANULAR_BASE_VERSION)
    }

    /*
    protected void addGooglePlayServiceLicenses(File artifactFile) {
        ZipFile licensesZip = new ZipFile(artifactFile)
        JsonSlurper jsonSlurper = new JsonSlurper()

        ZipEntry jsonFile = licensesZip.getEntry("third_party_licenses.json")
        ZipEntry txtFile = licensesZip.getEntry("third_party_licenses.txt")

        if (!jsonFile || !txtFile) {
            return
        }

        Object licensesObj = jsonSlurper.parse(licensesZip.getInputStream(
            jsonFile))
        if (licensesObj == null) {
            return
        }

        for (entry in licensesObj) {
            String key = entry.key
            int startValue = entry.value.start
            int lengthValue = entry.value.length

            if (!googleServiceLicenses.contains(key)) {
                googleServiceLicenses.add(key)
                byte[] content = getBytesFromInputStream(
                    licensesZip.getInputStream(txtFile),
                    startValue,
                    lengthValue)
                appendLicense(key, key, "external", content)
            }
        }
    }

     */

    protected static byte[] getBytesFromInputStream(
        InputStream stream,
        long offset,
        int length) {
        try {
            byte[] buffer = new byte[1024]
            ByteArrayOutputStream textArray = new ByteArrayOutputStream()

            stream.skip(offset)
            int bytesRemaining = length > 0 ? length : Integer.MAX_VALUE
            int bytes = 0

            while (bytesRemaining > 0
                && (bytes =
                stream.read(
                    buffer,
                    0,
                    Math.min(bytesRemaining, buffer.length)))
                != -1) {
                textArray.write(buffer, 0, bytes)
                bytesRemaining -= bytes
            }
            stream.close()

            return textArray.toByteArray()
        } catch (Exception e) {
            throw new RuntimeException(FAIL_READING_LICENSES_ERROR, e)
        }
    }

    protected void addLicensesFromPom(String group, String name, String version) {
        def pomFile = resolvePomFileArtifact(group, name, version)
        addLicensesFromPom((File) pomFile, group, name, version)
    }

    protected void addLicensesFromPom(File pomFile, String group, String name, String version) {
        if (pomFile == null || !pomFile.exists()) {
            logger.error("POM file $pomFile for $group:$name does not exist.")
            return
        }

        def rootNode = new XmlSlurper().parse(pomFile)

        String product = neStr(rootNode.getProperty("name"));
        if (product == null) {
            product = "${group}:${name}"
        } else {
            if (product != "${group}:${name}") {
                product = "${product} (${group}:${name})"
            }
        }

        def url = neStr(rootNode.scm.url)

        if (url == null) {
            url = neStr(rootNode.url);
        }

        if (LicenseResolver.manualLicense(this, rootNode, group, name, version, url, product)) {
            return
        }

        if (rootNode.licenses.size() == 0) {
            throw new IllegalArgumentException("No license information in "+pomFile)
        }

        def manualUrl = LicenseResolver.manualURL(this, group, name);

        if (manualUrl != null) {
            url = manualUrl
        }

        dumpLicenses(group, name, url, rootNode, product, version)

    }

    void dumpLicenses(String group, String artifact, String url, GPathResult rootNode, String product, String version) {

        if (url == null) {
            url = neStr(rootNode.url)
        }

        if (url == null) {
            throw new IllegalArgumentException("Can not find URL for $group:$artifact POM")
        }

        if (rootNode.licenses.license.size() > 1) {
            rootNode.licenses.license.each { node ->
                String nodeName = node.name
                String nodeUrl = node.url
                appendLicense("${product} ${nodeName}", "${product}", version, url, nodeUrl)
            }
        } else {
            String nodeUrl = rootNode.licenses.license.url
            appendLicense(product, product, version, url, nodeUrl)
        }

    }

    private File resolvePomFileArtifact(String group, String name, String version) {
        def moduleComponentIdentifier =
                createModuleComponentIdentifier(group, name, version)
        logger.info("Resolving POM file for $moduleComponentIdentifier licenses.")
        def components = getProject().getDependencies()
                .createArtifactResolutionQuery()
                .forComponents(moduleComponentIdentifier)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute()
        if (components.resolvedComponents.isEmpty()) {
            logger.warn("$moduleComponentIdentifier has no POM file.")
            return null
        }

        def artifacts = components.resolvedComponents[0].getArtifacts(MavenPomArtifact.class)
        if (artifacts.isEmpty()) {
            logger.error("$moduleComponentIdentifier empty POM artifact list.")
            return null
        }
        if (!(artifacts[0] instanceof ResolvedArtifactResult)) {
            logger.error("$moduleComponentIdentifier unexpected type ${artifacts[0].class}")
            return null
        }
        return ((ResolvedArtifactResult) artifacts[0]).getFile()
    }

    protected void appendLicense(String id, String product, String version, String url, String licenseUrl) {

        if (addedLicenses.contains(id+licenseUrl)) { return }
        addedLicenses.add(id+licenseUrl)

        // ID, vendor
        licenses.append(',,')
        // name of the SW component
        licenses.append(formatCsvString(product))
        licenses.append(',')
        // version of the SW Component
        licenses.append(formatCsvString(version))
        licenses.append(',')
        // source of the SW Component
        licenses.append(formatCsvString(url))
        licenses.append(',')
        // is the SW Component modified
        // modification authors
        // copyright information
        licenses.append('N,,,')
        // name and version of the license
        licenses.append(formatCsvString(LicenseResolver.resolveLicenseNameVersion(licenseUrl)))
        licenses.append(',')
        // license URL
        licenses.append(formatCsvString(licenseUrl))
        licenses.append(',')
        // text to be displayed
        // additional license conditions
        // integration of the sw components
        // superordinate
        licenses.append(',,,')
        licenses.append(LINE_SEPARATOR)

    }

    private static ModuleComponentIdentifier createModuleComponentIdentifier(String group, String name, String version) {
        return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)
    }

    static String formatCsvString(String s) {
        StringBuilder sb = new StringBuilder()
        boolean useQ = false
        int l = s.length()
        for (int i=0; i<l; i++) {
            char c = s.charAt(i)
            if (useQ) {
                if (c == (char)'"') {
                    // this is how we quote double quotes.
                    sb.append('"')
                }
                // everything else goes.
                sb.append(c)
            } else {

                if (c == (char)'"' || c == (char)',' || Character.isWhitespace(c)) {
                    // we need to turn on using quotes.
                    useQ = true
                    // starting quote
                    sb.append('"')
                    // all that had accumulated
                    sb.append(s, 0, i)
                    // escape if encountered double-quote
                    if (c == (char)'"') { sb.append('"') }
                    // add current character.
                    sb.append(c)
                }

            }
        }

        if (useQ) {
            sb.append('"')
            return sb.toString()
        }

        return s

    }

    void loadMissingLicenses(File file) {
        def root = new XmlSlurper().parse(file)
        root.library.each { library ->
            missingLicenseInfo.put(new ArtifactID(neStr(library.groupId), neStr(library.artifactId)), library)
        }
    }

    static neStr(Object o) {
        if (o == null) { return null }
        def s = o.toString().trim()
        if ("" == s) { return null }
        return s;
    }

}
