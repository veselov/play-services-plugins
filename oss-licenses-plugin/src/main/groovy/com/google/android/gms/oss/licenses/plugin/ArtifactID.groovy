package com.google.android.gms.oss.licenses.plugin

class ArtifactID {

    final String groupId
    final String artifactId

    ArtifactID(String groupId, String artifactId) {
        Objects.requireNonNull(this.groupId = groupId , "null groupId")
        Objects.requireNonNull(this.artifactId = artifactId, "null artifactId")
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        ArtifactID that = (ArtifactID) o

        if (artifactId != that.artifactId) return false
        if (groupId != that.groupId) return false

        return true
    }

    int hashCode() {
        int result
        result = groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        return result
    }
}
