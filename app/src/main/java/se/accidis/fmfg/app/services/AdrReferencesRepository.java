package se.accidis.fmfg.app.services;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import se.accidis.fmfg.app.utils.IOUtils;

/**
 * Repository for ADR reference texts used in material info help dialogs.
 */
public final class AdrReferencesRepository {
	private static final String ADR_REFERENCES_JSON_ASSET = "ADRReferences.json";
	private static final String TAG = AdrReferencesRepository.class.getSimpleName();

	public static final String SECTION_BEGRMGD = "BegrMgd";
	public static final String SECTION_FRPINSTR = "FrpInstr";
	public static final String SECTION_REDMGD = "RedMgd";
	public static final String SECTION_SARBEST = "Särbest";
	public static final String SECTION_TUNNELKOD = "Tunnelkod";

	private static AdrReferencesRepository mInstance;

	private final Context mContext;
	private JSONObject mReferences;

	private AdrReferencesRepository(Context context) {
		mContext = context.getApplicationContext();
	}

	public static AdrReferencesRepository getInstance(Context context) {
		return (null == mInstance ? (mInstance = new AdrReferencesRepository(context)) : mInstance);
	}

	public String getReference(String section, String code) {
		Object reference = getReferenceValue(section, code);
		if (null == reference) {
			return null;
		}

		return String.valueOf(reference);
	}

	public Object getReferenceValue(String section, String code) {
		if (TextUtils.isEmpty(section) || TextUtils.isEmpty(code)) {
			return null;
		}

		JSONObject references = getReferences();
		if (null == references) {
			return null;
		}

		JSONObject sectionReferences = references.optJSONObject(section);
		if (null == sectionReferences) {
			return null;
		}

		if (sectionReferences.has(code)) {
			return sectionReferences.opt(code);
		}

		if (SECTION_SARBEST.equals(section)) {
			String numericCode = getLeadingDigits(code);
			if (!TextUtils.isEmpty(numericCode) && !numericCode.equals(code) && sectionReferences.has(numericCode)) {
				return sectionReferences.opt(numericCode);
			}
		}

		return null;
	}

	private String getLeadingDigits(String value) {
		int length = value.length();
		int end = 0;
		while (end < length && Character.isDigit(value.charAt(end))) {
			end++;
		}

		return value.substring(0, end);
	}

	private JSONObject getReferences() {
		if (null != mReferences) {
			return mReferences;
		}

		try {
			String json = IOUtils.readToEnd(mContext.getAssets().open(ADR_REFERENCES_JSON_ASSET));
			mReferences = new JSONObject(json);
		} catch (IOException | JSONException ex) {
			Log.e(TAG, "Failed to load ADR references.", ex);
		}

		return mReferences;
	}
}
