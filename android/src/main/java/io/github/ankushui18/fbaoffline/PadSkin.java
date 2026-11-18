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
                    {R.drawable.legacy_classic_button_start, R.drawable.legacy_classic_button_start_p}}),
    CLASSIC_CIRCLE("Circle · Classic", "Original round joystick and classic buttons", CLASSIC,
            R.drawable.legacy_classic_analog, R.drawable.legacy_classic_analog_2),
    CHROME_CIRCLE("Circle · Chrome", "Original ring-and-ball joystick and metal buttons", CHROME,
            R.drawable.legacy_chrome_analog, R.drawable.legacy_chrome_analog_2);

    final String title, detail;
    final int[] directions;
    final int[][] buttons;
    final int circle, knob;
    final boolean classic;
    final int[] player, combos, combosPressed;
    PadSkin(String title, String detail, int[] directions, int[][] buttons) {
        this.title = title;
        this.detail = detail;
        this.directions = directions;
        this.buttons = buttons;
        circle = knob = 0;
        classic = title.equals("Classic");
        player = classic ? new int[] {R.drawable.legacy_classic_button_player, R.drawable.legacy_classic_button_player2, R.drawable.legacy_classic_button_player3, R.drawable.legacy_classic_button_player4}
                : new int[] {R.drawable.legacy_chrome_button_player, R.drawable.legacy_chrome_button_player2, R.drawable.legacy_chrome_button_player3, R.drawable.legacy_chrome_button_player4};
        combos = classic ? new int[] {R.drawable.legacy_classic_button_1, R.drawable.legacy_classic_button_2, R.drawable.legacy_classic_button_3, R.drawable.legacy_classic_button_4}
                : new int[] {R.drawable.legacy_chrome_button_1, R.drawable.legacy_chrome_button_2, R.drawable.legacy_chrome_button_3, R.drawable.legacy_chrome_button_4};
        combosPressed = classic ? new int[] {R.drawable.legacy_classic_button_1_p, R.drawable.legacy_classic_button_2_p, R.drawable.legacy_classic_button_3_p, R.drawable.legacy_classic_button_4_p}
                : new int[] {R.drawable.legacy_chrome_button_1_p, R.drawable.legacy_chrome_button_2_p, R.drawable.legacy_chrome_button_3_p, R.drawable.legacy_chrome_button_4_p};
    }

    PadSkin(String title, String detail, PadSkin base, int circle, int knob) {
        this.title = title;
        this.detail = detail;
        this.circle = circle;
        this.knob = knob;
        classic = base.classic;
        directions = base.directions;
        buttons = base.buttons;
        player = base.player;
        combos = base.combos;
        combosPressed = base.combosPressed;
    }

    static PadSkin stored(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException ignored) { return CHROME; }
    }
}
