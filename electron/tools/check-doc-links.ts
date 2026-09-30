/* Every link and picture in the repository's documents points at something
   that is there, and every picture is used.
   (Taken from Hallym MIPS Simulator, electron/tools/check-doc-links.ts at
   v2.6.0, BSD 3-Clause, derived: ORIGIN.md; D-174.)

     node tools/check-doc-links.ts            the working tree (git's tracked files)
     node tools/check-doc-links.ts --online <ref>
                                              the published documents: the README and
                                              docs/usage, as GitHub serves them at <ref>,
                                              and every web link in them

   Offline:
   - broken: a Markdown link or picture ([..](x), ![..](x), <img src="x">) in
     a tracked .md file of this project whose file is not there, or whose
     #anchor is not a heading of that file (GitHub's anchors);
   - orphan: a picture (.png .jpg .jpeg .gif .svg .webp) tracked under docs/
     or electron/docs/ that no tracked .md file names (by its path or, in
     the same folder, its name: the screens README lists its files by name).
     A tool that writes a picture does not count: a picture is used when a
     document shows it or lists it.
   Upstream originals are left out: vendor/ (the Logisim 2.7.1 jar and the
   assembler's sources, kept byte for byte) and app/src/ (Logisim 2.7.1's
   own tree).

   Prints what it found and exits 1 if anything is broken or orphaned. */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

export const repo = path.join(import.meta.dirname, '..', '..');
const REPO = 'ars2323/hallym-circuit-studio';
export const UPSTREAM = /^(vendor|app\/src)\//;
export const PICTURE = /\.(png|jpe?g|gif|svg|webp)$/i;

export const trackedFiles = (): string[] =>
  execFileSync('git', ['-C', repo, 'ls-files', '-z'], { encoding: 'utf8' }).split('\0').filter(Boolean)
    .filter((f) => !UPSTREAM.test(f));

// GitHub's heading anchors: lower case, punctuation dropped, spaces to hyphens, repeats numbered.
export function anchors(md: string): Set<string> {
  const seen = new Map<string, number>();
  const out = new Set<string>();
  let fence = false;
  for (const line of md.split('\n')) {
    if (/^\s*```/.test(line)) fence = !fence;
    const m = !fence && /^#{1,6}\s+(.*?)\s*#*\s*$/.exec(line);
    if (!m) continue;
    const text = m[1].replace(/`/g, '').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/<[^>]+>/g, '');
    const base = text.toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').replace(/\s/g, '-');
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    out.add(n ? `${base}-${n}` : base);
  }
  return out;
}

// The links of a Markdown file (code blocks and inline code left out).
export function links(md: string): string[] {
  const text = md.replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
  const out: string[] = [];
  for (const m of text.matchAll(/!?\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g)) out.push(m[1]);
  for (const m of text.matchAll(/<img[^>]+src="([^"]+)"/g)) out.push(m[1]);
  for (const m of text.matchAll(/^\s*\[[^\]]+\]:\s*(\S+)/gm)) out.push(m[1]);
  return out;
}

const web = (t: string) => /^[a-z]+:/i.test(t);

// The documents' broken links: `read` gives a document's text, `exists` says whether a path is there.
export function brokenLinks(docs: readonly string[], read: (f: string) => string, exists: (f: string) => boolean):
    { count: number; broken: string[] } {
  const broken: string[] = [];
  const anchorCache = new Map<string, Set<string>>();
  const anchorsOf = (f: string) => {
    if (!anchorCache.has(f)) anchorCache.set(f, anchors(read(f)));
    return anchorCache.get(f)!;
  };
  let count = 0;
  for (const f of docs) {
    for (const target of links(read(f))) {
      if (web(target)) continue;
      count += 1;
      const [p, hash] = target.split('#');
      const resolved = p ? path.posix.normalize(path.posix.join(path.posix.dirname(f), decodeURIComponent(p))) : f;
      if (!exists(resolved)) { broken.push(`${f}: ${target} -- no such file`); continue; }
      if (hash && resolved.endsWith('.md') && !anchorsOf(resolved).has(decodeURIComponent(hash))) {
        broken.push(`${f}: ${target} -- no heading #${decodeURIComponent(hash)} in ${resolved}`);
      }
    }
  }
  return { count, broken };
}

// Pictures no document names: by path (from the root or from the document), or by name from a document in the
// picture's folder or from that folder's README.md.
export function orphanPictures(pictures: readonly string[], docs: readonly { f: string; text: string }[]): string[] {
  return pictures.filter((pic) => {
    const name = path.posix.basename(pic);
    const dir = path.posix.dirname(pic);
    return !docs.some(({ f, text }) => {
      if (text.includes(pic)) return true;
      const rel = path.posix.relative(path.posix.dirname(f), pic);
      if (text.includes(rel)) return true;
      return (path.posix.dirname(f) === dir || f === `${dir}/README.md`) && text.includes(name);
    });
  });
}

export const isDocPicture = (f: string) => PICTURE.test(f) && (f.startsWith('docs/') || f.startsWith('electron/docs/'));

function offline(): number {
  const tracked = trackedFiles();
  const mdFiles = tracked.filter((f) => f.endsWith('.md') && existsSync(path.join(repo, f)));
  const read = (f: string) => readFileSync(path.join(repo, f), 'utf8');
  const { count, broken } = brokenLinks(mdFiles, read, (f) => existsSync(path.join(repo, f)));
  const pictures = tracked.filter(isDocPicture);
  const orphans = orphanPictures(pictures, mdFiles.map((f) => ({ f, text: read(f) })));
  console.log(`links to files in ${mdFiles.length} documents: ${count}; broken: ${broken.length}`);
  for (const b of broken) console.log(`  BROKEN  ${b}`);
  console.log(`pictures under docs/ and electron/docs/: ${pictures.length}; orphans: ${orphans.length}`);
  for (const o of orphans) console.log(`  ORPHAN  ${o}`);
  return broken.length + orphans.length;
}

async function online(ref: string): Promise<number> {
  const docs = trackedFiles().filter((f) => f === 'README.md' || /^docs\/usage\/[^/]+\.md$/.test(f));
  const bad: string[] = [];
  const seen = new Map<string, number>();
  const get = async (url: string) => {
    if (!seen.has(url)) {
      let status = 0;
      for (let i = 0; i < 3 && (status === 0 || status >= 500 || status === 429); i += 1) {
        status = await fetch(url, { redirect: 'follow' }).then((r) => r.status, () => 0);
        if (status === 0 || status >= 500 || status === 429) await new Promise((r) => setTimeout(r, 2000));
      }
      seen.set(url, status);
    }
    return seen.get(url)!;
  };
  for (const f of docs) {
    for (const target of links(readFileSync(path.join(repo, f), 'utf8'))) {
      let url: string;
      if (web(target)) url = target;
      else {
        const [p] = target.split('#');
        const resolved = p ? path.posix.normalize(path.posix.join(path.posix.dirname(f), decodeURIComponent(p))) : f;
        const isDir = existsSync(path.join(repo, resolved)) && statSync(path.join(repo, resolved)).isDirectory();
        url = isDir ? `https://github.com/${REPO}/tree/${ref}/${encodeURI(resolved)}`
          : `https://raw.githubusercontent.com/${REPO}/${ref}/${encodeURI(resolved)}`;
      }
      const status = await get(url);
      if (status < 200 || status >= 400) bad.push(`${f}: ${target} -> ${url} (${status || 'no answer'})`);
    }
  }
  console.log(`links in ${docs.length} published documents, ${seen.size} distinct addresses at ${ref}; failing: ${bad.length}`);
  for (const b of bad) console.log(`  FAIL  ${b}`);
  return bad.length;
}

if (import.meta.main) {
  const i = process.argv.indexOf('--online');
  const failures = i >= 0 ? await online(process.argv[i + 1] ?? 'main') : offline();
  process.exit(failures ? 1 : 0);
}
