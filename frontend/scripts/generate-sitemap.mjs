#!/usr/bin/env node
/**
 * Writes public/sitemap.xml before every build, so Google can find every school and place page.
 *
 * It asks the live API which universities and places exist. If the API can't be reached
 * (for example it's asleep or down), it still writes a sitemap with the main pages and
 * never fails the build.
 *
 *   SITE_URL      public address of the site   (default https://campusrate.vercel.app)
 *   VITE_API_URL  backend address ending in /api (same variable the app uses)
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SITE_URL = (process.env.SITE_URL || process.env.VITE_SITE_URL || 'https://campusrate.vercel.app').replace(/\/$/, '');
const API_URL = (process.env.VITE_API_URL || '').replace(/\/$/, '');
const PLACE_TYPES = ['DORM', 'DINING', 'COURSE', 'JOB', 'CLUB'];
const STATIC_PAGES = ['/', '/rankings', '/compare', '/fit', '/worth-it'];

const outFile = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'sitemap.xml');

async function getJson(path, timeoutMs = 15000) {
  const res = await fetch(API_URL + path, { signal: AbortSignal.timeout(timeoutMs) });
  if (!res.ok) return { status: res.status, data: null };
  return { status: res.status, data: await res.json() };
}

/** Runs `fn` over `items` with at most `limit` requests in flight. */
async function pool(items, limit, fn) {
  const results = new Array(items.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: Math.min(limit, items.length) }, async () => {
      while (next < items.length) {
        const i = next++;
        results[i] = await fn(items[i]);
      }
    }),
  );
  return results;
}

/**
 * There's no "list all universities" endpoint, so look them up by id until we hit a long
 * run of ids that don't exist. Universities are seeded with ids 1, 2, 3, ...
 */
async function findUniversityIds() {
  const ids = [];
  let misses = 0;
  for (let start = 1; misses < 20 && start < 5000; start += 10) {
    const batch = Array.from({ length: 10 }, (_, k) => start + k);
    const found = await pool(batch, 10, async (id) => {
      try {
        return (await getJson(`/universities/${id}`)).status === 200 ? id : null;
      } catch {
        return null;
      }
    });
    for (const id of found) {
      if (id) {
        ids.push(id);
        misses = 0;
      } else {
        misses++;
      }
    }
  }
  return ids;
}

async function findPlaceIds(universityIds) {
  const jobs = universityIds.flatMap((u) => PLACE_TYPES.map((type) => ({ u, type })));
  const lists = await pool(jobs, 8, async ({ u, type }) => {
    try {
      const { data } = await getJson(`/universities/${u}/places?type=${type}`);
      return Array.isArray(data) ? data : [];
    } catch {
      return [];
    }
  });
  return [...new Set(lists.flat().filter((p) => !p.status || p.status === 'APPROVED').map((p) => p.id))];
}

function urlEntry(path, priority, changefreq) {
  return `  <url>\n    <loc>${SITE_URL}${path}</loc>\n    <changefreq>${changefreq}</changefreq>\n    <priority>${priority}</priority>\n  </url>`;
}

async function main() {
  let universityIds = [];
  let placeIds = [];

  if (!API_URL) {
    console.warn('[sitemap] VITE_API_URL is not set; writing main pages only.');
  } else {
    try {
      // Wake the backend first (free hosting may be asleep); allow up to 90 seconds.
      await getJson('/health', 90000);
      universityIds = await findUniversityIds();
      placeIds = await findPlaceIds(universityIds);
    } catch (e) {
      console.warn(`[sitemap] API not reachable (${e.message}); writing main pages only.`);
    }
  }

  const entries = [
    ...STATIC_PAGES.map((p) => urlEntry(p, p === '/' ? '1.0' : '0.6', 'weekly')),
    ...universityIds.map((id) => urlEntry(`/universities/${id}`, '0.9', 'daily')),
    ...placeIds.map((id) => urlEntry(`/places/${id}`, '0.7', 'weekly')),
  ];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries.join('\n')}\n</urlset>\n`;
  mkdirSync(dirname(outFile), { recursive: true });
  writeFileSync(outFile, xml);
  console.log(`[sitemap] ${entries.length} URLs (${universityIds.length} universities, ${placeIds.length} places) → public/sitemap.xml`);
}

main().catch((e) => {
  // Never block a deploy because of the sitemap.
  console.warn(`[sitemap] skipped: ${e.message}`);
});
