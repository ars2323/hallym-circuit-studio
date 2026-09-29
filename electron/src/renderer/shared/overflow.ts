/* The » rule (logic only): a bar too narrow for all it holds -- the title
   bar's toolbar, the status bar -- first gives way in steps that hide
   nothing (key hints, names, spacing: shared/titlebar.ts), then moves whole
   items, one at a time, into a "»" menu at its end (v1 X-02, D-106: the
   toolbar; Z-20's known limit, the status bar, the same way).  Nothing is
   ever just hidden: every item is on the bar or in its » menu, and comes
   back as the bar widens.

   Which item goes first: the one that matters least (the lowest `keep`),
   and among equals the one furthest right -- so the bar's left part, where
   the eye starts, stays as it is.  The menu lists them in the bar's order. */

export interface Unit {
  keep: number;             // higher: stays on the bar longer
}

// The items in the order they leave the bar (indices into `units`).
export function overflowOrder(units: readonly Unit[]): number[] {
  return units.map((u, i) => ({ keep: u.keep, i })).sort((a, b) => a.keep - b.keep || b.i - a.i).map((x) => x.i);
}

// How many items must leave, the fewest that make `fits` true (`fits(k)`: the bar holds the rest once the first k
// of the order have left, with the » button shown when k > 0).  All of them if nothing else works.
export function overflowCount(total: number, fits: (k: number) => boolean): number {
  for (let k = 0; k < total; k += 1) if (fits(k)) return k;
  return total;
}

// Which items are on the » menu when `count` have left, in the bar's order.
export function leftBar(units: readonly Unit[], count: number): number[] {
  return overflowOrder(units).slice(0, count).sort((a, b) => a - b);
}
