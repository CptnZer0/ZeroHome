package dev.roel.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class GlowTest {
    @Test
    public void redLogoOnWhiteGivesRed() {
        int[] px = new int[100];
        Arrays.fill(px, 0xFFFFFFFF);
        for (int i = 0; i < 10; i++) px[i] = 0xFFFF0000;
        assertEquals(0xFFFF0000, Glow.pick(px));
    }

    @Test
    public void greenLogoOnBlackGivesGreen() {
        int[] px = new int[100];
        Arrays.fill(px, 0xFF000000);
        for (int i = 0; i < 10; i++) px[i] = 0xFF00FF00;
        assertEquals(0xFF00FF00, Glow.pick(px));
    }

    @Test
    public void greyOrClearBannerFallsBack() {
        int[] px = new int[100];
        Arrays.fill(px, 0xFF808080);
        assertEquals(Glow.FALLBACK, Glow.pick(px));
        Arrays.fill(px, 0x00FF0000);
        assertEquals(Glow.FALLBACK, Glow.pick(px));
    }
}
