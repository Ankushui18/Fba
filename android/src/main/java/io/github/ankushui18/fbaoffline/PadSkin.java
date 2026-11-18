package io.github.ankushui18.fbaoffline;

/** Original FBA4droid skin frames: D-pad, A–F, then Coin and Start. */
enum PadSkin {
    CHROME("Chrome", "Metal buttons · English Coin / Start",
            new int[] {R.drawable.legacy_chrome_button_dpad, R.drawable.legacy_chrome_button_dpad_up,
                    R.drawable.legacy_chrome_button_dpad_down, R.drawable.legacy_chrome_button_dpad_left,
                    R.drawable.legacy_chrome_button_dpad_right},
            new int[][] {{R.drawable.legacy_chrome_button_x, R.drawable.legacy_chrome_button_x_p},
                    {R.drawable.legacy_chrome_button_a, R.drawable.legacy_chrome_button_a_p},
                    {R.drawable.legacy_chrome_button_y, R.drawable.legacy_chrome_button_y_p},
                    {R.drawable.legacy_chrome_button_b, R.drawable.legacy_chrome_button_b_p},
                    {R.drawable.legacy_chrome_button_l1, R.drawable.legacy_chrome_button_l1_p},
                    {R.drawable.legacy_chrome_button_r1, R.drawable.legacy_chrome_button_r1_p},
                    {R.drawable.legacy_chrome_button_coin, R.drawable.legacy_chrome_button_coin_p},
                    {R.drawable.legacy_chrome_button_start, R.drawable.legacy_chrome_button_start_p}}),
    CLASSIC("Classic", "White D-pad · red, gold and blue buttons",
            new int[] {R.drawable.legacy_classic_button_dpad, R.drawable.legacy_classic_button_dpad_up,
                    R.drawable.legacy_classic_button_dpad_down, R.drawable.legacy_classic_button_dpad_left,
                    R.drawable.legacy_classic_button_dpad_right},
            new int[][] {{R.drawable.legacy_classic_button_x, R.drawable.legacy_classic_button_x_p},
                    {R.drawable.legacy_classic_button_a, R.drawable.legacy_classic_button_a_p},
                    {R.drawable.legacy_classic_button_y, R.drawable.legacy_classic_button_y_p},
                    {R.drawable.legacy_classic_button_b, R.drawable.legacy_classic_button_b_p},
                    {R.drawable.legacy_classic_button_l1, R.drawable.legacy_classic_button_l1_p},
                    {R.drawable.legacy_classic_button_r1, R.drawable.legacy_classic_button_r1_p},
                    {R.drawable.legacy_classic_button_coin, R.drawable.legacy_classic_button_coin_p},
                    {R.drawable.legacy_classic_button_start, R.drawable.legacy_classic_button_start_p}});

    final String title, detail;
    final int[] directions;
    final int[][] buttons;
    PadSkin(String title, String detail, int[] directions, int[][] buttons) {
        this.title = title;
        this.detail = detail;
        this.directions = directions;
        this.buttons = buttons;
    }
}
