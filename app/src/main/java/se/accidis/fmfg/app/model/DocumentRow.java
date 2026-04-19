package se.accidis.fmfg.app.model;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.UUID;

import se.accidis.fmfg.app.R;
import se.accidis.fmfg.app.ui.materials.ValueHelper;

/**
 * Model object for rows in a transport declaration.
 */
public final class DocumentRow {
	private static final BigDecimal MG_PER_KG = new BigDecimal(1000000);

	private UUID mId;
	private Material mMaterial;
	private BigDecimal mMultiplier;
	private BigDecimal mAmount; // Antal enheter för beräkning av NEM
	private BigDecimal mCustomNEMmg; // Användardefinierad NEM i mg
	private String mCustomNEMUnit; // Enhet för användardefinierad NEM
	private Boolean mMiljoOverride; // Användardefinierad Miljo
	private boolean mIsVolume; // Huruvida kvantitet är angiven i liter
	private int mNumberOfPkgs; // Antal kolli
	private String mTypeOfPkgs; // Beskrivning av kolli
	private BigDecimal mWeightVolume; // Kvantitet farligt gods i liter / kg

	public DocumentRow(Material material) {
		mId = UUID.randomUUID();
		mAmount = BigDecimal.ZERO;
		mCustomNEMmg = null;
		mCustomNEMUnit = Keys.CUSTOM_NEM_UNIT_AMOUNT;
		mMiljoOverride = null;
		mMaterial = material;
		mMultiplier = new BigDecimal(ValueHelper.getMultiplierByTpKat(mMaterial.getTpKat()));
		mTypeOfPkgs = "";
		mWeightVolume = BigDecimal.ZERO;
	}

	public static DocumentRow fromJson(JSONObject json) throws JSONException {
		Material material = Material.fromJSON(json);
		DocumentRow row = new DocumentRow(material);
		String idValue = json.optString(Keys.ID, null);
		if (!TextUtils.isEmpty(idValue)) {
			row.mId = UUID.fromString(idValue);
		}
		row.mAmount = new BigDecimal(json.getString(Keys.AMOUNT));
		row.mIsVolume = json.getBoolean(Keys.IS_VOLUME);
		row.mNumberOfPkgs = json.getInt(Keys.NUMBER_OF_PKGS);
		row.mTypeOfPkgs = json.optString(Keys.TYPE_OF_PKGS, "");
		row.mWeightVolume = new BigDecimal(json.getString(Keys.WEIGHT_VOLUME));
		if (json.has(Keys.MILJO_OVERRIDE)) {
			row.mMiljoOverride = (json.isNull(Keys.MILJO_OVERRIDE) ? null : json.optBoolean(Keys.MILJO_OVERRIDE));
		}
		if (json.has(Keys.CUSTOM_NEM_MG)) {
			String customNem = json.optString(Keys.CUSTOM_NEM_MG, null);
			if (!TextUtils.isEmpty(customNem)) {
				try {
					BigDecimal parsed = new BigDecimal(customNem);
					row.mCustomNEMmg = (parsed.compareTo(BigDecimal.ZERO) > 0 ? parsed : null);
				} catch (NumberFormatException ignored) {
					row.mCustomNEMmg = null;
				}
			}
		}
		row.mCustomNEMUnit = sanitizeCustomNEMUnit(json.optString(Keys.CUSTOM_NEM_UNIT, Keys.CUSTOM_NEM_UNIT_AMOUNT));
		return row;
	}

	public void copyTo(DocumentRow other) {
		other.setMaterial(mMaterial);
		other.mAmount = mAmount;
		other.mCustomNEMmg = mCustomNEMmg;
		other.mCustomNEMUnit = mCustomNEMUnit;
		other.mMiljoOverride = mMiljoOverride;
		other.mIsVolume = mIsVolume;
		other.mNumberOfPkgs = mNumberOfPkgs;
		other.mTypeOfPkgs = mTypeOfPkgs;
		other.mWeightVolume = mWeightVolume;
	}

	public BigDecimal getAmount() {
		return mAmount;
	}

	public void setAmount(BigDecimal amount) {
		mAmount = (null != amount ? amount : BigDecimal.ZERO);
	}

	public BigDecimal getCalculatedValue() {
		BigDecimal value;
		if (hasNEM()) {
			value = getNEMkg();
		} else {
			value = mWeightVolume;
		}
		return value.multiply(mMultiplier);
	}

	public UUID getId() {
		return mId;
	}

	public Material getMaterial() {
		return mMaterial;
	}

	public void setMaterial(Material material) {
		mMaterial = material;
		mMultiplier = new BigDecimal(ValueHelper.getMultiplierByTpKat(mMaterial.getTpKat()));
		if (mMaterial.hasPresetNEMValue() || mMaterial.hasNEM()) {
			mCustomNEMmg = null;
			mCustomNEMUnit = Keys.CUSTOM_NEM_UNIT_AMOUNT;
		}
	}

	public BigDecimal getNEMkg() {
		BigDecimal nemPerUnit = getNemPerUnitKg();
		if (null == nemPerUnit) {
			return BigDecimal.ZERO;
		}
		return nemPerUnit.multiply(getNEMMultiplier());
	}

	public BigDecimal getCustomNEMmg() {
		return mCustomNEMmg;
	}

	public void setCustomNEMmg(BigDecimal customNEMmg) {
		if (null != customNEMmg && customNEMmg.compareTo(BigDecimal.ZERO) > 0) {
			mCustomNEMmg = customNEMmg;
		} else {
			mCustomNEMmg = null;
			mCustomNEMUnit = Keys.CUSTOM_NEM_UNIT_AMOUNT;
		}
	}

	public boolean isCustomNEMPerPackage() {
		return Keys.CUSTOM_NEM_UNIT_PACKAGE.equals(mCustomNEMUnit);
	}

	public void setCustomNEMPerPackage(boolean customNEMPerPackage) {
		mCustomNEMUnit = customNEMPerPackage ? Keys.CUSTOM_NEM_UNIT_PACKAGE : Keys.CUSTOM_NEM_UNIT_AMOUNT;
	}

	public int getNumberOfPackages() {
		return mNumberOfPkgs;
	}

	public void setNumberOfPackages(int numPackages) {
		mNumberOfPkgs = numPackages;
	}

	public String getPackagesText(Context context) {
		if (0 == mNumberOfPkgs && TextUtils.isEmpty(mTypeOfPkgs)) {
			return "";
		} else if (TextUtils.isEmpty(mTypeOfPkgs)) {
			return String.valueOf(mNumberOfPkgs) + ' ' + context.getString(1 == mNumberOfPkgs ? R.string.document_unspecified_package : R.string.document_unspecified_packages);
		} else {
			return String.valueOf(mNumberOfPkgs) + ' ' + mTypeOfPkgs.trim();
		}
	}

	public String getTypeOfPackages() {
		return mTypeOfPkgs;
	}

	public void setTypeOfPackages(String typeOfPackages) {
		mTypeOfPkgs = typeOfPackages;
	}

	public BigDecimal getWeightVolume() {
		return mWeightVolume;
	}

	public void setWeightVolume(BigDecimal weightVolume) {
		mWeightVolume = (null != weightVolume ? weightVolume : BigDecimal.ZERO);
	}

	public String getWeightVolumeText(Context context) {
		if (isFreeText()) {
			return "";
		} else {
			return String.format(context.getString(mIsVolume ? R.string.unit_liter_format : R.string.unit_kg_format), ValueHelper.formatValue(mWeightVolume));
		}
	}

	public boolean hasNEM() {
		return mMaterial.hasNEM() || null != mCustomNEMmg;
	}

	public boolean hasMiljoOverride() {
		return null != mMiljoOverride;
	}

	public boolean isMiljoFarligt() {
		if (null != mMiljoOverride) {
			return mMiljoOverride;
		}
		return mMaterial.getMiljo();
	}

	public void setMiljoOverride(Boolean value) {
		if (null != value && value) {
			mMiljoOverride = true;
		} else {
			mMiljoOverride = null;
		}
	}

	public boolean isFreeText() {
		return mMaterial.isCustom();
	}

	public boolean isVolume() {
		return mIsVolume;
	}

	public void setIsVolume(boolean isVolume) {
		mIsVolume = isVolume;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = mMaterial.toJson();
		json.put(Keys.ID, mId.toString());
		json.put(Keys.AMOUNT, mAmount.toString());
		json.put(Keys.IS_VOLUME, mIsVolume);
		json.put(Keys.NUMBER_OF_PKGS, mNumberOfPkgs);
		json.put(Keys.TYPE_OF_PKGS, mTypeOfPkgs);
		json.put(Keys.WEIGHT_VOLUME, mWeightVolume.toString());
		if (null != mCustomNEMmg) {
			json.put(Keys.CUSTOM_NEM_MG, mCustomNEMmg.toPlainString());
			json.put(Keys.CUSTOM_NEM_UNIT, mCustomNEMUnit);
		}
		if (null != mMiljoOverride) {
			json.put(Keys.MILJO_OVERRIDE, mMiljoOverride);
		}
		return json;
	}

	private BigDecimal getNemPerUnitKg() {
		if (mMaterial.hasNEM()) {
			return mMaterial.getNEMkg();
		} else if (null != mCustomNEMmg) {
			return mCustomNEMmg.divide(MG_PER_KG, 6, BigDecimal.ROUND_FLOOR);
		}
		return null;
	}

	private BigDecimal getNEMMultiplier() {
		if (!mMaterial.hasNEM() && null != mCustomNEMmg && isCustomNEMPerPackage()) {
			return new BigDecimal(mNumberOfPkgs);
		}
		return (null != mAmount) ? mAmount : BigDecimal.ZERO;
	}

	private static String sanitizeCustomNEMUnit(String value) {
		return Keys.CUSTOM_NEM_UNIT_PACKAGE.equals(value) ? Keys.CUSTOM_NEM_UNIT_PACKAGE : Keys.CUSTOM_NEM_UNIT_AMOUNT;
	}

	public static class Keys {
		public static final String AMOUNT = "Amount";
		public static final String CUSTOM_NEM_MG = "CustomNEMmg";
		public static final String CUSTOM_NEM_UNIT = "CustomNEMUnit";
		public static final String CUSTOM_NEM_UNIT_AMOUNT = "amount";
		public static final String CUSTOM_NEM_UNIT_PACKAGE = "package";
		public static final String ID = "RowId";
		public static final String IS_VOLUME = "IsVolume";
		public static final String NUMBER_OF_PKGS = "NumberOfPkgs";
		public static final String TYPE_OF_PKGS = "TypeOfPkgs";
		public static final String WEIGHT_VOLUME = "WeightVolume";
		public static final String MILJO_OVERRIDE = "MiljoOverride";

		private Keys() {
		}
	}
}
