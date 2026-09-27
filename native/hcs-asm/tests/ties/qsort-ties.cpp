/*
 * hcs-asm tests — Copyright (c) 2026 AIAC Lab, Hallym University. BSD 3-Clause License, see LICENSE.
 *
 * qsort with a chosen order for equal keys (see qsort-ties.h). HCS_QSORT_TIES:
 * unset or 0 keeps table order, 1 reverses it, other values shuffle it.
 */
#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <vector>

static unsigned tie_rank(size_t i, unsigned seed) {
  if (seed == 1) return ~(unsigned)i;
  unsigned x = (unsigned)i * 2654435761u ^ seed * 40503u;  // any fixed shuffle per seed
  x ^= x >> 15;
  x *= 2246822519u;
  x ^= x >> 13;
  return x;
}

extern "C" void hcs_qsort_ties(void *base, size_t n, size_t size, int (*cmp)(const void *, const void *)) {
  const char *env = std::getenv("HCS_QSORT_TIES");
  unsigned seed = env ? (unsigned)std::strtoul(env, 0, 10) : 0;
  char *b = (char *)base;
  std::vector<size_t> idx(n);
  for (size_t i = 0; i < n; i += 1) idx[i] = i;
  std::stable_sort(idx.begin(), idx.end(), [&](size_t x, size_t y) {
    int c = cmp(b + x * size, b + y * size);
    if (c != 0) return c < 0;
    return seed != 0 && tie_rank(x, seed) < tie_rank(y, seed);
  });
  std::vector<char> sorted(n * size);
  for (size_t i = 0; i < n; i += 1) std::memcpy(&sorted[i * size], b + idx[i] * size, size);
  if (n > 0) std::memcpy(b, &sorted[0], n * size);
}
