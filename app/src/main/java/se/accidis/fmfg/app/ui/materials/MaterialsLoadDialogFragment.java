package se.accidis.fmfg.app.ui.materials;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import se.accidis.fmfg.app.R;
import se.accidis.fmfg.app.model.Document;
import se.accidis.fmfg.app.model.DocumentRow;
import se.accidis.fmfg.app.model.Material;
import se.accidis.fmfg.app.services.DocumentsRepository;
import se.accidis.fmfg.app.utils.AndroidUtils;

/**
 * Fragment for creating/editing a document row (loading materials).
 */
public final class MaterialsLoadDialogFragment extends DialogFragment {
	public static final String ARG_ROW_ID = "rowId";
	private static final BigDecimal MG_PER_KG = new BigDecimal(1000000);

	private BigDecimal mAmount;
	private View mAmountHeading;
	private View mAmountLayout;
	private View mAmountRequiredMarker;
	private EditText mCustomNEMField;
	private View mCustomNEMHeading;
	private View mCustomNEMLayout;
	private View mCustomNEMRequiredMarker;
	private SwitchCompat mCustomNEMToggle;
	private RadioGroup mCustomNEMUnitGroup;
	private BigDecimal mCustomNEMkg;
	private boolean mCustomNEMMode;
	private boolean mCustomNEMPerPackage;
	private BigDecimal mDocumentTotalValue;
	private View mFmHeading;
	private View mFmRequiredMarker;
	private Spinner mFmSpinner;
	private CheckBox mMiljoCheckbox;
	private MaterialsLoadDialogListener mListener;
	private Material mMaterial;
	private BigDecimal mMultiplier;
	private View mNEMHeading;
	private TextView mNEMView;
	private int mSelectedFmIndex = -1;
	private int mNumberOfPackages;
	private EditText mNumberPkgsField;
	private DocumentsRepository mRepository;
	private UUID mEditingRowId;
	private EditText mTechnicalNameField;
	private View mTechnicalNameHeading;
	private TextView mTotalValueView;
	private AutoCompleteTextView mTypePkgsField;
	private TextView mValueView;
	private BigDecimal mWeightVolume;
	private boolean mWeightVolumeIsVolume;

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final Bundle args = getArguments();
		mMaterial = Material.fromBundle(args);
		mSelectedFmIndex = mMaterial.getSelectedFmIndex();
		AndroidUtils.assertIsTrue(!mMaterial.isCustom(), "Materials load dialog loaded with custom material.");

		mRepository = DocumentsRepository.getInstance(getContext());
		Document document = mRepository.getCurrentDocument();
		mEditingRowId = getRowId(args);
		DocumentRow row = document.getRowById(mEditingRowId);

		mDocumentTotalValue = document.getCalculatedTotalValue();
		boolean hasExistingRow = false;
		if (null != row) {
			hasExistingRow = true;
			mMaterial = row.getMaterial();
			mSelectedFmIndex = mMaterial.getSelectedFmIndex();
			mDocumentTotalValue = mDocumentTotalValue.subtract(row.getCalculatedValue());
		} else {
			mSelectedFmIndex = -1;
			mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
		}

		int multiplier = ValueHelper.getMultiplierByTpKat(mMaterial.getTpKat());
		mMultiplier = new BigDecimal(multiplier);

		@SuppressLint("InflateParams")
		View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_material_load, null);
		mAmountHeading = view.findViewById(R.id.material_load_amount_heading);
		mAmountLayout = view.findViewById(R.id.material_load_amount_layout);
		mAmountRequiredMarker = view.findViewById(R.id.material_load_amount_required);
		mCustomNEMField = (EditText) view.findViewById(R.id.material_load_custom_nem);
		mCustomNEMHeading = view.findViewById(R.id.material_load_custom_nem_heading);
		mCustomNEMLayout = view.findViewById(R.id.material_load_custom_nem_layout);
		mCustomNEMRequiredMarker = view.findViewById(R.id.material_load_custom_nem_required);
		mCustomNEMToggle = (SwitchCompat) view.findViewById(R.id.material_load_custom_nem_toggle);
		mCustomNEMUnitGroup = (RadioGroup) view.findViewById(R.id.material_load_custom_nem_unit);
		mFmHeading = view.findViewById(R.id.material_load_fm_heading);
		mFmRequiredMarker = view.findViewById(R.id.material_load_fm_required);
		mValueView = (TextView) view.findViewById(R.id.material_load_value);
		mTotalValueView = (TextView) view.findViewById(R.id.material_load_total_value);
		mNEMHeading = view.findViewById(R.id.material_load_nem_heading);
		mNEMView = (TextView) view.findViewById(R.id.material_load_nem);
		mTechnicalNameHeading = view.findViewById(R.id.material_load_technical_name_heading);
		mTechnicalNameField = (EditText) view.findViewById(R.id.material_load_technical_name);
		mCustomNEMMode = (null != row && null != row.getCustomNEMmg() && canToggleCustomNEMMode());
		initializeTechnicalNameInput(row);
		initializeFmSpinner(view);
		initializeCustomNEMInput(row);
		initializeCustomNEMUnit(row);
		initializeCustomNEMToggle();
		TextView multiplierView = (TextView) view.findViewById(R.id.material_load_multiplier);
		multiplierView.setText(String.valueOf(multiplier));

		Spinner weightVolumeUnitSpinner = (Spinner) view.findViewById(R.id.material_load_weight_volume_unit);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.unit_weight_volume, R.layout.spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		weightVolumeUnitSpinner.setAdapter(adapter);
		weightVolumeUnitSpinner.setOnItemSelectedListener(new WeightOrVolumeSelectedListener());
		if (null != row) {
			mWeightVolumeIsVolume = row.isVolume();
			weightVolumeUnitSpinner.setSelection(row.isVolume() ? 1 : 0);
		}

		EditText amountField = (EditText) view.findViewById(R.id.material_load_amount);
		amountField.addTextChangedListener(new AmountChangedListener());
		if (null != row) {
			amountField.setText(String.valueOf(row.getAmount()));
			mAmount = row.getAmount();
		}
		updateNemInputModeVisibility();

		mNumberPkgsField = (EditText) view.findViewById(R.id.material_load_number_pkgs);
		mNumberPkgsField.addTextChangedListener(new NumberOfPackagesChangedListener());
		mTypePkgsField = (AutoCompleteTextView) view.findViewById(R.id.material_load_type_pkgs);
		ArrayAdapter<String> typePkgsAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, getResources().getStringArray(R.array.material_pkg_types));
		mTypePkgsField.setThreshold(1);
		mTypePkgsField.setAdapter(typePkgsAdapter);

		EditText weightVolumeField = (EditText) view.findViewById(R.id.material_load_weight_volume);
		weightVolumeField.addTextChangedListener(new WeightVolumeChangedListener());
		if (null != row) {
			mNumberOfPackages = row.getNumberOfPackages();
			mNumberPkgsField.setText(String.valueOf(row.getNumberOfPackages()));
			mTypePkgsField.setText(row.getTypeOfPackages());
			weightVolumeField.setText(row.getWeightVolume().toString());
			if (null == mAmount) {
				mAmount = row.getAmount();
			}
		}

		mMiljoCheckbox = (CheckBox) view.findViewById(R.id.material_load_miljo);
		initializeMiljoCheckbox(row);

		calculate();

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setView(view)
			.setPositiveButton(R.string.generic_save, new SaveClickedListener())
			.setNegativeButton(R.string.generic_cancel, null);

		if (hasExistingRow) {
			// The button to remove the material is only visible if there is prior data
			builder.setNeutralButton(R.string.material_remove, new RemoveClickedListener());
		}

		return builder.create();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		super.onDismiss(dialog);
		if (null != mListener) {
			mListener.onDismiss();
		}
	}

	public void setDialogListener(MaterialsLoadDialogListener listener) {
		mListener = listener;
	}

	public static Bundle createArguments(Material material, DocumentRow row) {
		Bundle args = material.toBundle();
		if (null != row) {
			args.putString(ARG_ROW_ID, row.getId().toString());
		}
		return args;
	}

	private UUID getRowId(Bundle args) {
		if (null == args || !args.containsKey(ARG_ROW_ID)) {
			return null;
		}
		String idValue = args.getString(ARG_ROW_ID);
		if (null == idValue) {
			return null;
		}
		return UUID.fromString(idValue);
	}

	private void initializeFmSpinner(View view) {
		mFmSpinner = (Spinner) view.findViewById(R.id.material_load_fm_spinner);
		List<Material.FM> fmEntries = mMaterial.getFM();

		if (fmEntries.size() > 1) {
			if (!mCustomNEMMode && (mSelectedFmIndex < 0 || mSelectedFmIndex >= fmEntries.size())) {
				mSelectedFmIndex = 0;
				mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
			}
			ArrayAdapter<Material.FM> adapter = new ArrayAdapter<Material.FM>(getContext(), R.layout.spinner_two_line_item, fmEntries) {
				@NonNull
				@Override
				public View getView(int position, View convertView, @NonNull ViewGroup parent) {
					return createView(position, convertView, parent, R.layout.spinner_two_line_item);
				}

				@Override
				public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
					return createView(position, convertView, parent, R.layout.spinner_two_line_item);
				}

				private View createView(int position, View convertView, ViewGroup parent, int layoutResource) {
					View row = convertView;
					if (null == row) {
						LayoutInflater inflater = LayoutInflater.from(getContext());
						row = inflater.inflate(layoutResource, parent, false);
					}
					Material.FM entry = getItem(position);
					TextView line1 = (TextView) row.findViewById(R.id.fm_spinner_line1);
					TextView line2 = (TextView) row.findViewById(R.id.fm_spinner_line2);
					if (null != line1) {
						line1.setText(entry.getFbet() + " " + entry.getFben());
					}
					if (null != line2) {
						BigDecimal nemKg = new BigDecimal(entry.getNEMmgAsInt()).divide(MG_PER_KG, 6, BigDecimal.ROUND_FLOOR);
						line2.setText(getString(R.string.material_nem_kg_format, ValueHelper.formatValue(nemKg)));
					}
					return row;
				}
			};
			mFmSpinner.setAdapter(adapter);
			int initialSelection = (mSelectedFmIndex >= 0 && mSelectedFmIndex < fmEntries.size()) ? mSelectedFmIndex : 0;
			mFmSpinner.setSelection(initialSelection);
			mFmSpinner.setOnItemSelectedListener(new FmSelectedListener());
		} else if (1 == fmEntries.size()) {
			if (!mCustomNEMMode) {
				mSelectedFmIndex = 0;
				mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
			} else {
				mSelectedFmIndex = -1;
				mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
			}
		}
		updateNemInputModeVisibility();
	}

	private void initializeCustomNEMInput(DocumentRow row) {
		mCustomNEMField.addTextChangedListener(new CustomNEMChangedListener());
		if (null != row && null != row.getCustomNEMmg()) {
			mCustomNEMkg = row.getCustomNEMmg().divide(MG_PER_KG, 6, BigDecimal.ROUND_FLOOR);
			mCustomNEMField.setText(mCustomNEMkg.stripTrailingZeros().toPlainString());
		}
		updateNemInputModeVisibility();
	}

	private void initializeCustomNEMUnit(DocumentRow row) {
		mCustomNEMPerPackage = (null != row && row.isCustomNEMPerPackage());
		mCustomNEMUnitGroup.check(mCustomNEMPerPackage ? R.id.material_load_custom_nem_unit_package : R.id.material_load_custom_nem_unit_amount);
		mCustomNEMUnitGroup.setOnCheckedChangeListener(new CustomNEMUnitChangedListener());
		updateNemInputModeVisibility();
	}

	private void initializeCustomNEMToggle() {
		if (!canToggleCustomNEMMode()) {
			mCustomNEMToggle.setVisibility(View.GONE);
			return;
		}
		mCustomNEMToggle.setVisibility(View.VISIBLE);
		mCustomNEMToggle.setChecked(!mCustomNEMMode);
		mCustomNEMToggle.setOnCheckedChangeListener(new CustomNEMModeChangedListener());
	}

	private void initializeTechnicalNameInput(DocumentRow row) {
		DocumentRow technicalNameRow = (null != row ? row : new DocumentRow(mMaterial));
		boolean showTechnicalName = technicalNameRow.requiresTechnicalName();
		mTechnicalNameHeading.setVisibility(showTechnicalName ? View.VISIBLE : View.GONE);
		mTechnicalNameField.setVisibility(showTechnicalName ? View.VISIBLE : View.GONE);
		if (showTechnicalName && null != row) {
			mTechnicalNameField.setText(row.getTechnicalName());
		}
	}

	private void updateNemInputModeVisibility() {
		boolean showFm = mMaterial.getFM().size() > 1 && !mCustomNEMMode;
		boolean showNEM = isClass1();
		boolean showCustomNEM = showNEM && (canToggleCustomNEMMode() ? mCustomNEMMode : !mMaterial.hasPresetNEMValue());

		mFmHeading.setVisibility(showFm ? View.VISIBLE : View.GONE);
		mFmSpinner.setVisibility(showFm ? View.VISIBLE : View.GONE);
		mFmRequiredMarker.setVisibility(showFm && isClass1() ? View.VISIBLE : View.GONE);

		mCustomNEMHeading.setVisibility(showCustomNEM ? View.VISIBLE : View.GONE);
		mCustomNEMLayout.setVisibility(showCustomNEM ? View.VISIBLE : View.GONE);
		mCustomNEMUnitGroup.setVisibility(showCustomNEM ? View.VISIBLE : View.GONE);
		mCustomNEMRequiredMarker.setVisibility(showCustomNEM && isClass1() ? View.VISIBLE : View.GONE);

		mNEMHeading.setVisibility(showNEM ? View.VISIBLE : View.GONE);
		mNEMView.setVisibility(showNEM ? View.VISIBLE : View.GONE);

		boolean showAmount = usesAmountForNEM();
		mAmountHeading.setVisibility(showAmount ? View.VISIBLE : View.GONE);
		mAmountLayout.setVisibility(showAmount ? View.VISIBLE : View.GONE);
		mAmountRequiredMarker.setVisibility(showAmount && isClass1() ? View.VISIBLE : View.GONE);
	}

	private boolean usesAmountForNEM() {
		return isClass1() && !isCustomNEMPerPackageSelected();
	}

	private boolean isCustomNEMPerPackageSelected() {
		return isClass1() && mCustomNEMPerPackage && (mCustomNEMMode || !mMaterial.hasPresetNEMValue());
	}

	private boolean canToggleCustomNEMMode() {
		return !mMaterial.getFM().isEmpty();
	}

	private void initializeMiljoCheckbox(DocumentRow row) {
		if (null == mMiljoCheckbox) {
			return;
		}
		if (mMaterial.hasMiljoValue()) {
			mMiljoCheckbox.setVisibility(View.GONE);
			return;
		}
		mMiljoCheckbox.setVisibility(View.VISIBLE);
		boolean isChecked = (null != row && row.hasMiljoOverride());
		mMiljoCheckbox.setChecked(isChecked);
	}

	private void calculate() {
		if (null == mNEMView || null == mValueView || null == mTotalValueView) {
			return;
		}
		if (null == mAmount) {
			mAmount = BigDecimal.ZERO;
		}
		if (null == mWeightVolume) {
			mWeightVolume = BigDecimal.ZERO;
		}

		BigDecimal value;
		BigDecimal nemPerUnit = getActiveNEMPerUnitKg();
		if (null != nemPerUnit) {
			value = nemPerUnit.multiply(getActiveNEMMultiplier());
			mNEMView.setText(String.format(getString(R.string.unit_kg_format), ValueHelper.formatValue(value)));
		} else {
			value = mWeightVolume;
			if (null != mNEMView) {
				mNEMView.setText(getString(R.string.material_no_data));
			}
		}

		value = value.multiply(mMultiplier);
		mValueView.setText(String.format(getString(R.string.unit_points_format), ValueHelper.formatValue(value)));
		mTotalValueView.setText(String.format(getString(R.string.unit_points_format), ValueHelper.formatValue(value.add(mDocumentTotalValue))));
	}

	private BigDecimal getActiveNEMPerUnitKg() {
		if (!isClass1()) {
			return null;
		} else if (mCustomNEMMode && null != mCustomNEMkg) {
			return mCustomNEMkg;
		} else if (mMaterial.hasNEM()) {
			return mMaterial.getNEMkg();
		} else if (!mMaterial.hasPresetNEMValue() && null != mCustomNEMkg) {
			return mCustomNEMkg;
		}
		return null;
	}

	private BigDecimal getActiveNEMMultiplier() {
		if (usesCustomNEMValue() && mCustomNEMPerPackage) {
			return new BigDecimal(mNumberOfPackages);
		}
		return mAmount;
	}

	private boolean usesCustomNEMValue() {
		return isClass1() && null != mCustomNEMkg && (mCustomNEMMode || !mMaterial.hasPresetNEMValue());
	}

	private boolean isClass1() {
		return "1".equals(mMaterial.getKlass());
	}

	private int parsePackageCountOrZero(String str) {
		try {
			BigDecimal value = ValueHelper.parseValue(str);
			if (value.compareTo(BigDecimal.ZERO) < 0) {
				return 0;
			}
			return value.intValueExact();
		} catch (ArithmeticException ignored) {
			return 0;
		}
	}

	public interface MaterialsLoadDialogListener {
		void onDismiss();
	}

	private final class FmSelectedListener implements AdapterView.OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			if (mCustomNEMMode) {
				return;
			}
			int selectedFmIndex = position;
			if (selectedFmIndex != mSelectedFmIndex) {
				mSelectedFmIndex = selectedFmIndex;
				mMaterial = mMaterial.withSelectedFmIndex(selectedFmIndex);
				calculate();
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

	private final class AmountChangedListener implements TextWatcher {
		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			mAmount = ValueHelper.parseValue(s.toString());
			calculate();
		}
	}

	private final class NumberOfPackagesChangedListener implements TextWatcher {
		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			mNumberOfPackages = parsePackageCountOrZero(s.toString());
			calculate();
		}
	}

	private final class RemoveClickedListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			Document document = mRepository.getCurrentDocument();
			document.removeRowById(mEditingRowId);
			document.setHasUnsavedChanges(true);
			mRepository.commitCurrentDocument();
		}
	}

	private final class SaveClickedListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			Material rowMaterial = mCustomNEMMode ? mMaterial.withSelectedFmIndex(-1) : mMaterial;
			DocumentRow row = new DocumentRow(rowMaterial);

			int numberPkgs = parsePackageCountOrZero(mNumberPkgsField.getText().toString());
			row.setNumberOfPackages(numberPkgs);
			String typePkgs = mTypePkgsField.getText().toString().trim();
			row.setTypeOfPackages(typePkgs);
			row.setTechnicalName(mTechnicalNameField.getVisibility() == View.VISIBLE ? mTechnicalNameField.getText().toString() : "");
			row.setAmount(usesAmountForNEM() ? mAmount : BigDecimal.ZERO);
			BigDecimal customNEMmg = (isClass1() && (mCustomNEMMode || !mMaterial.hasPresetNEMValue())) ? convertKgToMg(mCustomNEMkg) : null;
			row.setCustomNEMmg(customNEMmg);
			row.setCustomNEMPerPackage(mCustomNEMPerPackage);
			row.setWeightVolume(mWeightVolume);
			row.setIsVolume(mWeightVolumeIsVolume);
			if (null != mMiljoCheckbox && mMiljoCheckbox.getVisibility() == View.VISIBLE) {
				row.setMiljoOverride(mMiljoCheckbox.isChecked());
			} else {
				row.setMiljoOverride(null);
			}

			Document document = mRepository.getCurrentDocument();
			if (null != mEditingRowId) {
				document.updateRow(mEditingRowId, row);
			} else {
				document.addRow(row);
			}
			document.setHasUnsavedChanges(true);
			mRepository.commitCurrentDocument();
		}

	}

	private final class WeightOrVolumeSelectedListener implements AdapterView.OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			mWeightVolumeIsVolume = (1 == position);
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

	private final class WeightVolumeChangedListener implements TextWatcher {
		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			mWeightVolume = ValueHelper.parseValue(s.toString());
			calculate();
		}
	}

	private final class CustomNEMChangedListener implements TextWatcher {
		@Override
		public void afterTextChanged(Editable s) {
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			BigDecimal parsed = ValueHelper.parseValue(s.toString());
			mCustomNEMkg = (parsed.compareTo(BigDecimal.ZERO) > 0) ? parsed : null;
			calculate();
		}
	}

	private final class CustomNEMUnitChangedListener implements RadioGroup.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId) {
			mCustomNEMPerPackage = (R.id.material_load_custom_nem_unit_package == checkedId);
			updateNemInputModeVisibility();
			calculate();
		}
	}

	private final class CustomNEMModeChangedListener implements CompoundButton.OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			mCustomNEMMode = !isChecked;
			if (mCustomNEMMode) {
				mSelectedFmIndex = -1;
				mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
			} else {
				List<Material.FM> fmEntries = mMaterial.getFM();
				if (!fmEntries.isEmpty() && (mSelectedFmIndex < 0 || mSelectedFmIndex >= fmEntries.size())) {
					mSelectedFmIndex = 0;
				}
				mMaterial = mMaterial.withSelectedFmIndex(mSelectedFmIndex);
				if (null != mFmSpinner && mSelectedFmIndex >= 0) {
					mFmSpinner.setSelection(mSelectedFmIndex);
				}
			}
			updateNemInputModeVisibility();
			calculate();
		}
	}

	private BigDecimal convertKgToMg(BigDecimal valueKg) {
		if (null == valueKg) {
			return null;
		}
		return valueKg.multiply(MG_PER_KG).setScale(0, RoundingMode.HALF_UP);
	}
}
