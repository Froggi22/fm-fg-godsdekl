package se.accidis.fmfg.app.ui.materials;

import android.text.TextUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Simple helper class for calculating and formatting values.
 */
public final class ValueHelper {
	private static final DecimalFormat mValueFormat = new DecimalFormat();

	private ValueHelper() {
	}

	public static String formatValue(BigDecimal bigDecimal) {
		return mValueFormat.format(bigDecimal);
	}

	public static int getMultiplierByTpKat(int tpKat) {
		switch (tpKat) {
			case 1:
				return 50;
			case 2:
				return 3;
			case 3:
				return 1;
			default:
				return 0;
		}
	}

	public static void initializeLocale(Locale locale) {
		Locale.setDefault(locale);
		mValueFormat.setMaximumFractionDigits(5);
		mValueFormat.setMinimumFractionDigits(0);
		mValueFormat.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(locale));
	}

	public static BigDecimal parseValue(String text) {
		if (TextUtils.isEmpty(text)) {
			return BigDecimal.ZERO;
		}

		try {
			return new ExpressionParser(text).parse();
		} catch (IllegalArgumentException | ArithmeticException ignored) {
			return BigDecimal.ZERO;
		}
	}

	private static final class ExpressionParser {
		private static final int DIVISION_SCALE = 10;
		private final String mText;
		private int mPosition;

		ExpressionParser(String text) {
			// BigDecimal constructor accepts only period as separator.
			mText = text.replace(',', '.');
		}

		BigDecimal parse() {
			BigDecimal value = parseExpression();
			skipWhitespace();
			if (mPosition != mText.length()) {
				throw new IllegalArgumentException();
			}
			return value;
		}

		private BigDecimal parseExpression() {
			BigDecimal value = parseTerm();
			while (true) {
				skipWhitespace();
				if (consume('+')) {
					value = value.add(parseTerm());
				} else if (consume('-')) {
					value = value.subtract(parseTerm());
				} else {
					return value;
				}
			}
		}

		private BigDecimal parseTerm() {
			BigDecimal value = parseFactor();
			while (true) {
				skipWhitespace();
				if (consume('*')) {
					value = value.multiply(parseFactor());
				} else if (consume('/')) {
					BigDecimal divisor = parseFactor();
					if (BigDecimal.ZERO.compareTo(divisor) == 0) {
						throw new ArithmeticException();
					}
					value = value.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
				} else {
					return value;
				}
			}
		}

		private BigDecimal parseFactor() {
			skipWhitespace();
			int sign = 1;
			if (consume('+')) {
				sign = 1;
			} else if (consume('-')) {
				sign = -1;
			}

			BigDecimal value = parseNumber();
			return sign < 0 ? value.negate() : value;
		}

		private BigDecimal parseNumber() {
			skipWhitespace();
			int start = mPosition;
			boolean hasDigit = false;
			boolean hasDecimalSeparator = false;
			while (mPosition < mText.length()) {
				char ch = mText.charAt(mPosition);
				if (Character.isDigit(ch)) {
					hasDigit = true;
					mPosition++;
				} else if ('.' == ch && !hasDecimalSeparator) {
					hasDecimalSeparator = true;
					mPosition++;
				} else {
					break;
				}
			}

			if (!hasDigit) {
				throw new IllegalArgumentException();
			}
			return new BigDecimal(mText.substring(start, mPosition));
		}

		private boolean consume(char expected) {
			if (mPosition < mText.length() && expected == mText.charAt(mPosition)) {
				mPosition++;
				return true;
			}
			return false;
		}

		private void skipWhitespace() {
			while (mPosition < mText.length() && Character.isWhitespace(mText.charAt(mPosition))) {
				mPosition++;
			}
		}
	}
}
