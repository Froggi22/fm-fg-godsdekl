package se.accidis.fmfg.app.ui.documents;

import java.util.ArrayList;
import java.util.List;

import se.accidis.fmfg.app.model.Document;
import se.accidis.fmfg.app.model.DocumentRow;
import se.accidis.fmfg.app.model.Material;

/**
 * Simple helper class for validating if a document is in compliance with co-loading rules.
 */
public final class ColoadingHelper {
    private static final String LABEL_14S = "1.4S";
    private static final String LABEL_1_PREFIX = "1";

    private ColoadingHelper() {
    }

    public static boolean isViolationOfColoadingRules(Document document) {
        /*
         * Kollin som innehåller explosiva ämnen eller föremål i klass 1, märkta med etikett nr 1, 1.4, 1.5, 1.6
         * utom etikett 1.4 med samhanteringsgrupp S får inte lastas på samma fordon som kollin som innehåller farligt
         * gods från övriga klasser.
         */
        boolean containsRestrictedClass1 = false, containsNonClass1 = false;
        for (DocumentRow row : document.getRows()) {
            boolean rowContainsClass1 = containsClass1(row);
            containsRestrictedClass1 = (containsRestrictedClass1 || (rowContainsClass1 && !isClass14S(row)));
            containsNonClass1 = (containsNonClass1 || !rowContainsClass1);
            if (containsRestrictedClass1 && containsNonClass1) {
                return true;
            }
        }

        /*
         * Kollin som är försedda med etiketterna 1, 1.4, 1.5 och 1.6 och som tillhör olika samhanteringsgrupper i klass 1,
         * får endast samlastas på ett fordon om det tillåts enligt bilden nedan.
         */
        if (containsRestrictedClass1) {
            List<Character> cohandlingGroups = getCohandlingGroups(document.getRows());
            for (Character group : cohandlingGroups) {
                if (!areAllowedCohandlingGroups(group, cohandlingGroups)) {
                    return true;
                }
            }
        }

        /*
         * Vi kontrollerar inte reglerna för ämnen med etikett 1+4.1 eller 1+5.2 eftersom det inte finns några sådana
         * i materiallistan.
         */

        return false;
    }

    private static boolean areAllowedCohandlingGroups(Character group, List<Character> cohandlingGroups) {
        for (Character otherGroup : cohandlingGroups) {
            if (!areAllowedCohandlingGroups(group, otherGroup)) {
                return false;
            }
        }
        return true;
    }

    private static boolean areAllowedCohandlingGroups(Character group, Character other) {
        if (group.equals(other)) {
            return true;
        }

        switch (group) {
            case 'A':
                return false;
            case 'B':
                return ('D' == other || 'S' == other);
            case 'C':
                return ('D' == other || 'E' == other || 'G' == other || 'N' == other || 'S' == other);
            case 'D':
                return ('B' == other || 'C' == other || 'E' == other || 'G' == other || 'N' == other || 'S' == other);
            case 'E':
                return ('C' == other || 'D' == other || 'G' == other || 'N' == other || 'S' == other);
            case 'F':
                return ('S' == other);
            case 'G':
                return ('C' == other || 'D' == other || 'E' == other || 'S' == other);
            case 'H':
                return ('S' == other);
            case 'J':
                return ('S' == other);
            case 'L':
                return false;
            case 'N':
                return ('C' == other || 'D' == other || 'E' == other || 'S' == other);
            case 'S':
                return ('A' != other && 'L' != other);
            default:
                return true;
        }
    }

    private static boolean containsClass1(DocumentRow row) {
        Material material = row.getMaterial();
        String klassKod = material.getKlassKod();
        if (null != klassKod && klassKod.startsWith(LABEL_1_PREFIX)) {
            return true;
        }

        List<String> etiketter = material.getDisplayEtiketter();
        for (String kod : etiketter) {
            if (kod.startsWith(LABEL_1_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClass14S(DocumentRow row) {
        Material material = row.getMaterial();
        if (LABEL_14S.equals(material.getKlassKod())) {
            return true;
        }

        return material.getDisplayEtiketter().contains(LABEL_14S);
    }

    private static List<Character> getCohandlingGroups(List<DocumentRow> rows) {
        List<Character> result = new ArrayList<>();
        for (DocumentRow row : rows) {
            addCohandlingGroup(result, row.getMaterial().getKlassKod());
            for (String kod : row.getMaterial().getDisplayEtiketter()) {
                addCohandlingGroup(result, kod);
            }
        }
        return result;
    }

    private static void addCohandlingGroup(List<Character> result, String kod) {
        if (null == kod || !kod.startsWith(LABEL_1_PREFIX)) {
            return;
        }

        char cohandlingGroup = kod.charAt(kod.length() - 1);
        if (Character.isLetter(cohandlingGroup) && !result.contains(cohandlingGroup)) {
            result.add(cohandlingGroup);
        }
    }
}
