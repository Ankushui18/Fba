package io.github.ankushui18.fbaoffline;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared styling for the library, controller picker and in-game menu. */
final class ArcadeUi {
    static final int PAGE = 0xfff5f7fb, WHITE = Color.WHITE, INK = 0xff132d48, MUTED = 0xff4e667d;
    static final int BLUE = 0xff146bc0, NAVY = 0xff104b89, LIGHT_BLUE = 0xffe3f0ff, BORDER = 0xffd4e3f1;
    static final int NIGHT = 0xff101e30, NIGHT_EDGE = 0xff334962, NIGHT_DIM = 0xffb0c9e0;
    private final Context context;

    ArcadeUi(Context context) { this.context = context; }

    int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    TextView text(CharSequence value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    TextView eyebrow(String value, int color) {
        TextView view = text(value, 11, color, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    static void singleLine(TextView view) {
        view.setSingleLine();
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    ImageView art(int image) {
        ImageView view = new ImageView(context);
        view.setImageResource(image);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    ImageButton icon(int image, String description, int fill, int tint, int radius, int padding) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(image);
        if (tint != 0) button.setColorFilter(tint);
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setBackground(tappable(fill, fill, radius, tint == WHITE || fill == BLUE));
        int p = dp(padding);
        button.setPadding(p, p, p, p);
        return button;
    }

    Button button(String title, int fill, int ink, int radius) {
        Button button = new Button(context);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(14);
        button.setTextColor(new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
                new int[] {fill == BLUE ? 0xffd9eaff : MUTED, ink}));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setBackground(tappable(fill, fill, radius, ink == WHITE));
        button.setStateListAnimator(null);
        return button;
    }

    GradientDrawable surface(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != fill) shape.setStroke(dp(1), stroke);
        return shape;
    }

    GradientDrawable gradient(int from, int to, int radius) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {from, to});
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    Drawable tappable(int fill, int stroke, int radius, boolean dark) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x35ffffff : 0x1c146bc0),
                surface(fill, stroke, radius), surface(WHITE, WHITE, radius));
    }

    void space(LinearLayout column, int height) {
        column.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height)));
    }

    /** Keep text and ROM rows readable on tablets while the color bands span the window. */
    FrameLayout widthLimit(int maxDp) {
        return new FrameLayout(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(maxDp));
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
    }
}
