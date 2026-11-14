package dev.roel.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AppOrderTest {
    @Test
    public void savedFirstThenNewHiddenAndUninstalledDropped() {
        List<String> saved = Arrays.asList("spotify", "gone", "youtube");
        List<String> installed = Arrays.asList("chrome", "youtube", "spotify", "tvbro");
        List<String> out = AppOrder.arrange(saved, installed, Collections.singletonList("chrome"));
        assertEquals(Arrays.asList("spotify", "youtube", "tvbro"), out);
    }

    @Test
    public void moveSwapsAndStopsAtEnds() {
        List<String> order = Arrays.asList("a", "b", "c");
        assertEquals(Arrays.asList("b", "a", "c"), AppOrder.move(order, "b", -1));
        assertEquals(order, AppOrder.move(order, "a", -1));
        assertEquals(order, AppOrder.move(order, "c", 1));
        assertEquals(order, AppOrder.move(order, "missing", 1));
    }
}
