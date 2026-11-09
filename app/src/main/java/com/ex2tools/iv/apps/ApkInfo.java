package com.ex2tools.iv.apps;

public final class ApkInfo {

    public final String packageName;
    public final String label;
    public final String versionName;
    public final long versionCode;
    public final String signer;
    public final boolean installed;
    public final String installedVersionName;
    public final long installedVersionCode;
    public final String installedSigner;

    public ApkInfo(String packageName, String label, String versionName, long versionCode,
                   String signer, boolean installed, String installedVersionName,
                   long installedVersionCode, String installedSigner) {
        this.packageName = packageName;
        this.label = label;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.signer = signer;
        this.installed = installed;
        this.installedVersionName = installedVersionName;
        this.installedVersionCode = installedVersionCode;
        this.installedSigner = installedSigner;
    }

    public boolean isUpdate() {
        return installed && versionCode > installedVersionCode;
    }

    public boolean isDowngrade() {
        return installed && versionCode < installedVersionCode;
    }

    public boolean signatureMatches() {
        if (!installed) return true;
        if (signer == null || signer.equals("—")
                || installedSigner == null || installedSigner.isEmpty()
                || installedSigner.equals("—")) {
            return true;
        }
        return signer.equals(installedSigner);
    }
}
