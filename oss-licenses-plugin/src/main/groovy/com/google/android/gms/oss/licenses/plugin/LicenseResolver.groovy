package com.google.android.gms.oss.licenses.plugin

import groovy.util.slurpersupport.GPathResult

class LicenseResolver {

    static String resolveLicenseNameVersion(String url) {

        switch (url) {
            case "https://www.apache.org/licenses/LICENSE-2.0":
            case "http://www.apache.org/licenses/LICENSE-2.0.txt":
                return "Apache License, version 2.0"
            case "http://www.bouncycastle.org/licence.html":
                return "The Legion of the Bouncy Castle License"
            case "https://opensource.org/licenses/mit-license.php":
            case "http://www.opensource.org/licenses/mit-license.php":
                return "MIT License"
            case "https://en.wikipedia.org/wiki/WTFPL":
                return "Public Domain"
        }

        throw new IllegalArgumentException("Unsupported license URL "+url)

    }


    static boolean manualLicense(LicensesTask task, GPathResult pomRoot, String group, String artifact, String version, String url, String product) {

        if (version.contains("empty-to-avoid-conflict")) {
            return true;
        }

        def missingInfo = task.missingLicenseInfo.get(new ArtifactID(group, artifact))
        if (missingInfo == null) { return false }

        if (missingInfo.skip.size() > 0) { return true }

        if (missingInfo.licenses.size() == 0) {
            // throw new IllegalArgumentException("No license information for ${group}:${artifact}")
            return false
        }

        def manualUrl = manualURL(task, group, artifact);

        if (manualUrl != null) {
            url = manualUrl
        }

        task.dumpLicenses(group, artifact, url, missingInfo, product, version)

        return true
    }

    static String manualURL(LicensesTask task, String group, String artifact) {

        def missingInfo = task.missingLicenseInfo.get(new ArtifactID(group, artifact))
        if (missingInfo == null) { return null }
        return LicensesTask.neStr(missingInfo.url)
    }
}
