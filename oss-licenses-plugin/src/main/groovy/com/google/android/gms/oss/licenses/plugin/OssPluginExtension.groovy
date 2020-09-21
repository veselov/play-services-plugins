package com.google.android.gms.oss.licenses.plugin

import org.gradle.api.Project
import org.gradle.api.provider.Property

class OssPluginExtension {

    private final Property<File> missingLicenses;

    OssPluginExtension(Project project) {
        missingLicenses = project.getObjects().property(File.class);
    }

    Property<File> getMissingLicenses() {
        return missingLicenses;
    }

}
