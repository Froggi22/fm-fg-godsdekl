package se.accidis.fmfg.app.ui.materials;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import se.accidis.fmfg.app.R;
import se.accidis.fmfg.app.model.Document;
import se.accidis.fmfg.app.model.DocumentRow;
import se.accidis.fmfg.app.model.Material;
import se.accidis.fmfg.app.services.AdrReferencesRepository;
import se.accidis.fmfg.app.services.DocumentsRepository;
import se.accidis.fmfg.app.services.LabelsRepository;
import se.accidis.fmfg.app.ui.MainActivity;

/**
 * Fragment showing information about a material.
 */
public final class MaterialsInfoFragment extends Fragment implements MainActivity.HasNavigationItem {
	private static final int TOOLTIP_MAX_LENGTH = 180;

	private Button mChangeButton;
	private Button mLoadButton;
	private Material mMaterial;
	private AdrReferencesRepository mReferencesRepository;
	private Button mRemoveButton;
	private DocumentsRepository mRepository;

	public static MaterialsInfoFragment createInstance(Material material) {
		Bundle bundle = material.toBundle();
		MaterialsInfoFragment fragment = new MaterialsInfoFragment();
		fragment.setArguments(bundle);
		return fragment;
	}

	@Override
	public int getItemId() {
		return R.id.nav_materials;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_materials_info, container, false);

		Bundle args = getArguments();
		mMaterial = Material.fromBundle(args);
		mRepository = DocumentsRepository.getInstance(getContext());
		mReferencesRepository = AdrReferencesRepository.getInstance(getContext());

		// Transportbenämning
		TextView tpbenView = (TextView) view.findViewById(R.id.material_tpben);
		tpbenView.setText(mMaterial.getTpben());

		// UN-nummer
		TextView unNrView = (TextView) view.findViewById(R.id.material_unnr);
		if (!TextUtils.isEmpty(mMaterial.getUNnr())) {
			unNrView.setText(String.format(getString(R.string.material_un_format), mMaterial.getUNnr()));
		} else {
			unNrView.setText(R.string.material_no_data);
		}

		// Etiketter
		TextView etiketterView = (TextView) view.findViewById(R.id.material_etiketter);
		if (!TextUtils.isEmpty(mMaterial.getEtiketterAsString())) {
			etiketterView.setText(mMaterial.getEtiketterAsString());
		} else {
			etiketterView.setText(R.string.material_no_data);
		}

		populateOptionalTextRow(view, R.id.material_sarbest_row, R.id.material_sarbest, joinValues(mMaterial.getSarbest()));
		populateOptionalTextRow(view, R.id.material_begrmgd_row, R.id.material_begrmgd, mMaterial.getBegrMgd());
		populateOptionalTextRow(view, R.id.material_redmgd_row, R.id.material_redmgd, mMaterial.getRedMgd());
		initializeInfoDialog(view, R.id.material_tunnelkod_info, R.string.material_tunnelkod, createReferenceText(AdrReferencesRepository.SECTION_TUNNELKOD, mMaterial.getTunnelkod()));
		initializeInfoDialog(view, R.id.material_sarbest_info, R.string.material_sarbest, createReferenceText(AdrReferencesRepository.SECTION_SARBEST, mMaterial.getSarbest()));
		initializeInfoDialog(view, R.id.material_begrmgd_info, R.string.material_begrmgd, createReferenceText(AdrReferencesRepository.SECTION_BEGRMGD, mMaterial.getBegrMgd()));
		initializeInfoDialog(view, R.id.material_redmgd_info, R.string.material_redmgd, createRedMgdReferenceText(mMaterial.getRedMgd()));

		// Tunnelrestriktionskod
		View tunnelKodRow = view.findViewById(R.id.material_tunnelkod_row);
		TextView tunnelKodHeading = (TextView) view.findViewById(R.id.material_tunnelkod_heading);
		TextView tunnelKodView = (TextView) view.findViewById(R.id.material_tunnelkod);
		if (!TextUtils.isEmpty(mMaterial.getTunnelkod())) {
			tunnelKodView.setText(mMaterial.getTunnelkod());
		} else {
			tunnelKodRow.setVisibility(View.GONE);
			tunnelKodHeading.setVisibility(View.GONE);
			tunnelKodView.setVisibility(View.GONE);
		}

		// Transportkategori
		TextView tpKatView = (TextView) view.findViewById(R.id.material_tpkat);
		if (0 != mMaterial.getTpKat()) {
			tpKatView.setText(String.valueOf(mMaterial.getTpKat()));
		} else {
			tpKatView.setText(R.string.material_no_data);
		}

		// Militära Benämningar
		TextView fbenHeading = (TextView) view.findViewById(R.id.material_fm_heading);
		LinearLayout fmListLayout = (LinearLayout) view.findViewById(R.id.material_fm_list);
		populateFmList(fbenHeading, fmListLayout);

		// Etiketter (bilder)
		LinearLayout labelsLayout = (LinearLayout) view.findViewById(R.id.material_layout_labels);
		if (!mMaterial.getEtiketter().isEmpty()) {
			populateLabelsView(labelsLayout);
		} else {
			labelsLayout.setVisibility(View.GONE);
		}

		mLoadButton = (Button) view.findViewById(R.id.material_button_load);
		mLoadButton.setOnClickListener(new LoadButtonClickListener());
		mChangeButton = (Button) view.findViewById(R.id.material_button_change);
		mChangeButton.setOnClickListener(new ChangeButtonClickListener());
		mRemoveButton = (Button) view.findViewById(R.id.material_button_remove);
		mRemoveButton.setOnClickListener(new RemoveButtonClickListener());
		refreshDocumentState();

		return view;
	}

	private void populateOptionalTextRow(View rootView, int rowId, int valueId, String value) {
		View row = rootView.findViewById(rowId);
		TextView valueView = (TextView) rootView.findViewById(valueId);
		if (!TextUtils.isEmpty(value)) {
			valueView.setText(value);
		} else {
			row.setVisibility(View.GONE);
			valueView.setVisibility(View.GONE);
		}
	}

	private String joinValues(List<String> values) {
		if (null == values || values.isEmpty()) {
			return null;
		}
		return TextUtils.join(", ", values);
	}

	private String createReferenceText(String section, String code) {
		if (TextUtils.isEmpty(code)) {
			return "";
		}

		String reference = mReferencesRepository.getReference(section, code);
		if (TextUtils.isEmpty(reference)) {
			reference = getString(R.string.material_reference_missing, code);
		}

		return code + ": " + reference;
	}

	private String createReferenceText(String section, List<String> codes) {
		if (null == codes || codes.isEmpty()) {
			return "";
		}

		List<String> references = new ArrayList<>();
		for (String code : codes) {
			if (!TextUtils.isEmpty(code)) {
				references.add(createReferenceText(section, code));
			}
		}

		return TextUtils.join("\n\n", references);
	}

	private String createRedMgdReferenceText(String code) {
		if (TextUtils.isEmpty(code)) {
			return "";
		}

		Object reference = mReferencesRepository.getReferenceValue(AdrReferencesRepository.SECTION_REDMGD, code);
		if (reference instanceof JSONArray) {
			JSONArray limits = (JSONArray) reference;
			if (2 == limits.length()) {
				return code + ": "
					+ "Högsta nettomängd per innerförpackning: " + limits.optString(0) + " g/ml. "
					+ "Högsta nettomängd per ytterförpackning: " + limits.optString(1) + " g/ml.";
			}
		} else if (null != reference && !TextUtils.isEmpty(String.valueOf(reference))) {
			return code + ": " + reference;
		}

		return code + ": " + getString(R.string.material_reference_missing, code);
	}

	private void initializeInfoDialog(View rootView, int infoId, final int titleId, final String message) {
		View infoView = rootView.findViewById(infoId);
		final String dialogMessage = (TextUtils.isEmpty(message) ? getString(R.string.material_no_data) : message);
		String tooltipMessage = createTooltipText(dialogMessage);
		final CharSequence styledDialogMessage = createBoldCodeText(dialogMessage);
		infoView.setContentDescription(tooltipMessage);
		infoView.setTooltipText(createBoldCodeText(tooltipMessage));
		infoView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				new AlertDialog.Builder(getActivity())
					.setTitle(titleId)
					.setMessage(styledDialogMessage)
					.setPositiveButton(R.string.generic_close, null)
					.show();
			}
		});
	}

	private String createTooltipText(String text) {
		if (TextUtils.isEmpty(text) || text.length() <= TOOLTIP_MAX_LENGTH) {
			return text;
		}

		int end = text.lastIndexOf(' ', TOOLTIP_MAX_LENGTH - 3);
		if (end < TOOLTIP_MAX_LENGTH / 2) {
			end = TOOLTIP_MAX_LENGTH - 3;
		}

		return text.substring(0, end).trim() + "...";
	}

	private CharSequence createBoldCodeText(String text) {
		if (TextUtils.isEmpty(text)) {
			return text;
		}

		SpannableStringBuilder builder = new SpannableStringBuilder(text);
		int start = 0;
		while (start < builder.length()) {
			int end = TextUtils.indexOf(builder, ':', start);
			if (end < 0) {
				break;
			}

			builder.setSpan(new StyleSpan(Typeface.BOLD), start, end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			int next = TextUtils.indexOf(builder, "\n\n", end + 1);
			if (next < 0) {
				break;
			}
			start = next + 2;
		}

		return builder;
	}

	private void populateLabelsView(LinearLayout layout) {
		Context context = getContext();
		Resources resources = getResources();
		int size = resources.getDimensionPixelSize(R.dimen.material_label_size);
		int margin = resources.getDimensionPixelSize(R.dimen.material_label_margin);

		List<Integer> labels = LabelsRepository.getLabelsByMaterial(mMaterial, false);

		for (Integer label : labels) {
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(size, size);
			layoutParams.setMargins(0, 0, margin, margin);

			ImageView imageView = new ImageView(context);
			imageView.setLayoutParams(layoutParams);
			imageView.setImageDrawable(ContextCompat.getDrawable(context, label));
			imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

			layout.addView(imageView);
		}
	}

	private void refreshDocumentState() {
		DocumentRow row = mRepository.getCurrentDocument().getRowByMaterial(mMaterial);
		if (null != row) {
			mLoadButton.setText(R.string.material_load_new);
			mChangeButton.setVisibility(View.VISIBLE);
			mRemoveButton.setVisibility(View.VISIBLE);
		} else {
			mLoadButton.setText(R.string.material_load);
			mChangeButton.setVisibility(View.GONE);
			mRemoveButton.setVisibility(View.GONE);
		}
	}

	private void populateFmList(TextView headingView, LinearLayout fmListLayout) {
		int rowSpacing = getResources().getDimensionPixelSize(R.dimen.material_fm_row_spacing);
		boolean hasData = false;

		for (Material.FM entry : mMaterial.getFM()) {
			CharSequence rowText = createFmRowText(entry);
			if (TextUtils.isEmpty(rowText)) {
				continue;
			}

			hasData = true;
			TextView rowView = new TextView(getContext());
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			params.setMargins(0, 0, 0, rowSpacing);
			rowView.setLayoutParams(params);
			TextViewCompat.setTextAppearance(rowView, R.style.DocumentRowPrimary);
			rowView.setText(rowText);
			fmListLayout.addView(rowView);
		}

		if (!hasData) {
			headingView.setVisibility(View.GONE);
			fmListLayout.setVisibility(View.GONE);
		}
	}

	private CharSequence createFmRowText(Material.FM entry) {
		List<String> labelParts = new ArrayList<>();
		if (!TextUtils.isEmpty(entry.getFbet())) {
			labelParts.add(entry.getFbet());
		}
		if (!TextUtils.isEmpty(entry.getFben())) {
			labelParts.add(entry.getFben());
		}

		String nemValue = formatNemKg(entry.getNEMmg());
		boolean hasNem = !TextUtils.isEmpty(nemValue);

		if (labelParts.isEmpty() && !hasNem) {
			return "";
		}

		SpannableStringBuilder builder = new SpannableStringBuilder();
		if (!labelParts.isEmpty()) {
			builder.append(TextUtils.join(" ", labelParts));
		}

		if (hasNem) {
			if (builder.length() > 0) {
				builder.append('\n');
			}
			int nemStart = builder.length();
			builder.append("NEM ");
			builder.append(nemValue);
			builder.append(" kg");
			builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.darkgray)), nemStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}

		return builder;
	}

	private String formatNemKg(Integer nemMg) {
		if (null == nemMg) {
			return null;
		}
		BigDecimal nemKg = new BigDecimal(nemMg).divide(new BigDecimal(1000000), 6, BigDecimal.ROUND_FLOOR);
		return ValueHelper.formatValue(nemKg);
	}

	private final class LoadButtonClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			MaterialsLoadDialogFragment dialog = new MaterialsLoadDialogFragment();
			dialog.setArguments(mMaterial.toBundle());
			dialog.setDialogListener(new MaterialsLoadDialogListener());
			dialog.show(getFragmentManager(), MaterialsLoadDialogFragment.class.getSimpleName());
		}
	}

	private final class ChangeButtonClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			DocumentRow row = mRepository.getCurrentDocument().getRowByMaterial(mMaterial);
			if (null == row) {
				return;
			}

			MaterialsLoadDialogFragment dialog = new MaterialsLoadDialogFragment();
			dialog.setArguments(MaterialsLoadDialogFragment.createArguments(row.getMaterial(), row));
			dialog.setDialogListener(new MaterialsLoadDialogListener());
			dialog.show(getFragmentManager(), MaterialsLoadDialogFragment.class.getSimpleName());
		}
	}

	private final class MaterialsLoadDialogListener implements MaterialsLoadDialogFragment.MaterialsLoadDialogListener {
		@Override
		public void onDismiss() {
			refreshDocumentState();
		}
	}

	private final class RemoveButtonClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			new AlertDialog.Builder(getActivity())
				.setTitle(R.string.material_remove_confirm_title)
				.setMessage(R.string.material_remove_confirm_message)
				.setPositiveButton(R.string.generic_yes, new ConfirmRemoveClickListener())
				.setNegativeButton(R.string.generic_no, null)
				.show();
		}
	}

	private final class ConfirmRemoveClickListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			Document document = mRepository.getCurrentDocument();
			document.removeRowsByMaterial(mMaterial);
			document.setHasUnsavedChanges(true);
			mRepository.commitCurrentDocument();
			refreshDocumentState();
		}
	}
}
