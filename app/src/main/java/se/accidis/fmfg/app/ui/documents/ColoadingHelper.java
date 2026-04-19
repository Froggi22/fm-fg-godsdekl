package se.accidis.fmfg.app.ui.documents;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import se.accidis.fmfg.app.R;
import se.accidis.fmfg.app.model.Document;
import se.accidis.fmfg.app.model.DocumentRow;
import se.accidis.fmfg.app.model.Material;

/**
 * Simple helper class for validating if a document is in compliance with co-loading rules.
 */
public final class ColoadingHelper {
    private static final String CLASS_1 = "1";
    private static final String LABEL_14S = "1.4S";
    private static final String LABEL_1_PREFIX = "1";
    private static final Character[] COHANDLING_GROUPS = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'L', 'N', 'S'};
    private static final String ALLOWED = "X";
    private static final String DISALLOWED = "";
    private static final String COMMENT_SEPARATOR = "\\|";

    /*
     * Rader och kolumner följer COHANDLING_GROUPS: A, B, C, D, E, F, G, H, J, L, N, S.
     * X = tillåtet, tom sträng = otillåtet, siffror = villkor/kommentar som ska varna.
     */
    private static final String[][] COLOADING_RULES = {
            {"2", DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED},
            {DISALLOWED, ALLOWED, DISALLOWED, "1", DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED},
            {DISALLOWED, DISALLOWED, ALLOWED, ALLOWED, ALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, "3|4", ALLOWED},
            {DISALLOWED, "1", ALLOWED, ALLOWED, ALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, "3|4", ALLOWED},
            {DISALLOWED, DISALLOWED, ALLOWED, ALLOWED, ALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, "3|4", ALLOWED},
            {DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED},
            {DISALLOWED, DISALLOWED, ALLOWED, ALLOWED, ALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED},
            {DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED},
            {DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, ALLOWED, DISALLOWED, DISALLOWED, ALLOWED},
            {DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, "5", DISALLOWED, DISALLOWED},
            {DISALLOWED, DISALLOWED, "3|4", "3|4", "3|4", DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, DISALLOWED, "3", ALLOWED},
            {DISALLOWED, ALLOWED, ALLOWED, ALLOWED, ALLOWED, ALLOWED, ALLOWED, ALLOWED, ALLOWED, DISALLOWED, ALLOWED, ALLOWED}
    };

    private ColoadingHelper() {
    }

    public static boolean isViolationOfColoadingRules(Document document) {
        return isClass1LoadedWithOtherClasses(document) || hasColoadingGroupConflict(document);
    }

    public static boolean isClass1LoadedWithOtherClasses(Document document) {
        boolean containsRestrictedClass1 = false, containsNonClass1 = false;
        for (DocumentRow row : document.getRows()) {
            boolean rowContainsClass1 = containsClass1(row);
            containsRestrictedClass1 = (containsRestrictedClass1 || (rowContainsClass1 && !isClass14S(row)));
            containsNonClass1 = (containsNonClass1 || !rowContainsClass1);
            if (containsRestrictedClass1 && containsNonClass1) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasColoadingGroupConflict(Document document) {
        return getColoadingResult(document).requiresWarning();
    }

    public static String getColoadingWarningText(Document document, Context context) {
        ColoadingResult result = getColoadingResult(document);
        if (result.getCommentNumbers().isEmpty()) {
            return result.requiresWarning() ? context.getString(R.string.document_warning_coloading_comment_disallowed) : "";
        }

        StringBuilder builder = new StringBuilder();
        for (Integer commentNumber : result.getCommentNumbers()) {
            if (0 != builder.length()) {
                builder.append('\n');
            }
            switch (commentNumber) {
                case 1:
                    builder.append(context.getString(R.string.document_warning_coloading_comment_1));
                    break;
                case 2:
                    builder.append(context.getString(R.string.document_warning_coloading_comment_2));
                    break;
                case 3:
                    builder.append(context.getString(R.string.document_warning_coloading_comment_3));
                    break;
                case 4:
                    builder.append(context.getString(R.string.document_warning_coloading_comment_4));
                    break;
                case 5:
                    builder.append(context.getString(R.string.document_warning_coloading_comment_5));
                    break;
                default:
                    break;
            }
        }
        return builder.toString();
    }

    private static ColoadingResult getColoadingResult(Document document) {
        boolean containsRestrictedClass1 = false;
        for (DocumentRow row : document.getRows()) {
            boolean rowContainsClass1 = containsClass1(row);
            containsRestrictedClass1 = (containsRestrictedClass1 || (rowContainsClass1 && !isClass14S(row)));
        }

        if (containsRestrictedClass1) {
            List<Character> cohandlingGroups = getCohandlingGroups(document.getRows());
            Set<Integer> warningCommentNumbers = getWarningCommentNumbers(cohandlingGroups);
            if (!warningCommentNumbers.isEmpty()) {
                return ColoadingResult.warning(warningCommentNumbers);
            }

            if (hasDisallowedCohandlingGroupCombination(cohandlingGroups)) {
                return ColoadingResult.warning();
            }
        }

        return ColoadingResult.allowed();
    }

    private static boolean hasDisallowedCohandlingGroupCombination(List<Character> cohandlingGroups) {
        for (int i = 0; i < cohandlingGroups.size(); i++) {
            for (int j = i + 1; j < cohandlingGroups.size(); j++) {
                if (getColoadingRule(cohandlingGroups.get(i), cohandlingGroups.get(j)).requiresWarning()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<Integer> getWarningCommentNumbers(List<Character> cohandlingGroups) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int i = 0; i < cohandlingGroups.size(); i++) {
            for (int j = i + 1; j < cohandlingGroups.size(); j++) {
                Character group = cohandlingGroups.get(i);
                Character otherGroup = cohandlingGroups.get(j);
                ColoadingRule rule = getColoadingRule(group, otherGroup);
                result.addAll(rule.getCommentNumbers());
                if (rule.requiresWarning()
                        && rule.getCommentNumbers().isEmpty()
                        && (Character.valueOf('L').equals(group) || Character.valueOf('L').equals(otherGroup))) {
                    result.add(5);
                }
            }
        }
        return result;
    }

    private static ColoadingRule getColoadingRule(Character group, Character otherGroup) {
        int row = getCohandlingGroupIndex(group);
        int column = getCohandlingGroupIndex(otherGroup);
        if (row < 0 || column < 0) {
            return ColoadingRule.warning();
        }

        return ColoadingRule.fromCell(COLOADING_RULES[row][column]);
    }

    private static int getCohandlingGroupIndex(Character group) {
        for (int i = 0; i < COHANDLING_GROUPS.length; i++) {
            if (COHANDLING_GROUPS[i].equals(group)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsClass1(DocumentRow row) {
        Material material = row.getMaterial();
        if (CLASS_1.equals(material.getKlass())) {
            return true;
        }

        if (!TextUtils.isEmpty(material.getKlass())) {
            return false;
        }

        List<String> etiketter = material.getDisplayEtiketter();
        for (String kod : etiketter) {
            if (!TextUtils.isEmpty(kod) && kod.startsWith(LABEL_1_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClass14S(DocumentRow row) {
        Material material = row.getMaterial();
        if (CLASS_1.equals(material.getKlass()) && LABEL_14S.equals(material.getKlassKod())) {
            return true;
        }

        if (!TextUtils.isEmpty(material.getKlass())) {
            return false;
        }

        return material.getDisplayEtiketter().contains(LABEL_14S);
    }

    private static List<Character> getCohandlingGroups(List<DocumentRow> rows) {
        List<Character> result = new ArrayList<>();
        for (DocumentRow row : rows) {
            Material material = row.getMaterial();
            if (CLASS_1.equals(material.getKlass())) {
                addCohandlingGroup(result, material.getKlassKod());
            }
        }
        return result;
    }

    private static void addCohandlingGroup(List<Character> result, String kod) {
        if (TextUtils.isEmpty(kod) || !kod.startsWith(LABEL_1_PREFIX)) {
            return;
        }

        char cohandlingGroup = kod.charAt(kod.length() - 1);
        if (Character.isLetter(cohandlingGroup)) {
            result.add(cohandlingGroup);
        }
    }

    private static final class ColoadingResult {
        private final boolean mRequiresWarning;
        private final Set<Integer> mCommentNumbers;

        private ColoadingResult(boolean requiresWarning, Set<Integer> commentNumbers) {
            mRequiresWarning = requiresWarning;
            mCommentNumbers = commentNumbers;
        }

        static ColoadingResult allowed() {
            return new ColoadingResult(false, new LinkedHashSet<Integer>());
        }

        static ColoadingResult warning() {
            return new ColoadingResult(true, new LinkedHashSet<Integer>());
        }

        static ColoadingResult warning(Set<Integer> commentNumbers) {
            return new ColoadingResult(true, commentNumbers);
        }

        boolean requiresWarning() {
            return mRequiresWarning;
        }

        Set<Integer> getCommentNumbers() {
            return mCommentNumbers;
        }
    }

    private static final class ColoadingRule {
        private final boolean mRequiresWarning;
        private final Set<Integer> mCommentNumbers;

        private ColoadingRule(boolean requiresWarning, Set<Integer> commentNumbers) {
            mRequiresWarning = requiresWarning;
            mCommentNumbers = commentNumbers;
        }

        static ColoadingRule fromCell(String cell) {
            if (ALLOWED.equals(cell)) {
                return new ColoadingRule(false, new LinkedHashSet<Integer>());
            }
            if (TextUtils.isEmpty(cell)) {
                return warning();
            }

            Set<Integer> comments = new LinkedHashSet<>();
            String[] values = cell.split(COMMENT_SEPARATOR);
            for (String value : values) {
                try {
                    comments.add(Integer.parseInt(value.trim()));
                } catch (NumberFormatException ignored) {
                    return warning();
                }
            }
            return new ColoadingRule(true, comments);
        }

        static ColoadingRule warning() {
            return new ColoadingRule(true, new LinkedHashSet<Integer>());
        }

        boolean requiresWarning() {
            return mRequiresWarning;
        }

        Set<Integer> getCommentNumbers() {
            return mCommentNumbers;
        }
    }
}
