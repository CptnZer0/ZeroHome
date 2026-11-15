package dev.roel.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Order of the app row: the user's saved order first, new apps after, hidden apps left out. */
final class AppOrder {
    private AppOrder() {}

    static List<String> arrange(List<String> saved, List<String> installed, Collection<String> hidden) {
        List<String> out = new ArrayList<>();
        for (String p : saved) {
            if (installed.contains(p) && !hidden.contains(p) && !out.contains(p)) out.add(p);
        }
        for (String p : installed) {
            if (!hidden.contains(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    /** Moves pkg one step left (delta -1) or right (+1). Stays put at the ends. */
    static List<String> move(List<String> order, String pkg, int delta) {
        List<String> out = new ArrayList<>(order);
        int i = out.indexOf(pkg);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= out.size()) return out;
        out.set(i, out.get(j));
        out.set(j, pkg);
        return out;
    }

    /** Puts pkg first in the recent list (no duplicates) and keeps at most max entries. */
    static List<String> pushRecent(List<String> recent, String pkg, int max) {
        List<String> out = new ArrayList<>(recent);
        out.remove(pkg);
        out.add(0, pkg);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
