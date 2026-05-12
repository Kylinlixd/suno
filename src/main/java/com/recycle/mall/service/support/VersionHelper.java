
package com.recycle.mall.service.support;

import java.util.List;

/**
 * 语义版本号比较辅助工具
 */
public final class VersionHelper {

    private VersionHelper() {}

    public static String normalizeVersion(String clientVersion) {
        if (clientVersion == null || clientVersion.isBlank()) {
            return "1.0.0";
        }
        return clientVersion.trim();
    }

    public static int compareVersion(String left, String right) {
        SemVer lv = parseVersion(left);
        SemVer rv = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            if (lv.core()[i] != rv.core()[i]) {
                return Integer.compare(lv.core()[i], rv.core()[i]);
            }
        }
        if (lv.preRelease().isEmpty() && rv.preRelease().isEmpty()) {
            return 0;
        }
        if (lv.preRelease().isEmpty()) {
            return 1;
        }
        if (rv.preRelease().isEmpty()) {
            return -1;
        }
        int max = Math.max(lv.preRelease().size(), rv.preRelease().size());
        for (int i = 0; i < max; i++) {
            if (i >= lv.preRelease().size()) {
                return -1;
            }
            if (i >= rv.preRelease().size()) {
                return 1;
            }
            String l = lv.preRelease().get(i);
            String r = rv.preRelease().get(i);
            boolean lNumeric = isNumericIdentifier(l);
            boolean rNumeric = isNumericIdentifier(r);
            if (lNumeric && rNumeric) {
                int cmp = Integer.compare(Integer.parseInt(l), Integer.parseInt(r));
                if (cmp != 0) {
                    return cmp;
                }
                continue;
            }
            if (lNumeric != rNumeric) {
                return lNumeric ? -1 : 1;
            }
            int cmp = l.compareTo(r);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    static SemVer parseVersion(String version) {
        String clean = version == null ? "0.0.0" : version.trim();
        String withoutBuild = clean.split("\+", 2)[0];
        String[] majorAndPre = withoutBuild.split("-", 2);
        String[] parts = majorAndPre[0].split("\.");
        int[] values = new int[] {0, 0, 0};
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                values[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                values[i] = 0;
            }
        }
        List<String> preRelease = List.of();
        if (majorAndPre.length > 1 && !majorAndPre[1].isBlank()) {
            preRelease = List.of(majorAndPre[1].split("\."));
        }
        return new SemVer(values, preRelease);
    }

    private static boolean isNumericIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    record SemVer(int[] core, List<String> preRelease) {}
}
