/**
 * generate-app-assets — rasterizes the sovereign monorepo's brand mark into
 * the resources/ master assets `@capacitor/assets` expects: a flat app icon,
 * Android adaptive-icon foreground/background layers, and light/dark splash
 * screens. Run `npx capacitor-assets generate` afterward to populate iOS's
 * AppIcon.appiconset and Splash.imageset, and Android's mipmap density
 * buckets and adaptive-icon drawables, from these sources.
 *
 * Source of truth: sovereign/runtime/public/icons/favicon.svg — a dark
 * rounded square with a white "S" letterform (see that repo's
 * scripts/generate-splash.ts, which this mirrors for the splash treatment,
 * including its dark-theme inversion rule: light square, dark letter, so the
 * mark reads against a dark surface instead of dissolving into it).
 *
 * Run: `node scripts/generate-app-assets.mjs`
 */
import { mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OUT_DIR = join(ROOT, 'resources');

/** Mark fills, from sovereign/runtime/public/icons/favicon.svg. */
const MARK_SQUARE = '#0a0a0a';
const MARK_LETTER = '#ffffff';
/** Surface colours — the light/dark `--sv-color-surface` tokens (also the
 * PWA manifest's theme_color/background_color for dark). */
const SURFACE_LIGHT = '#ffffff';
const SURFACE_DARK = '#09090b';
/** Mark occupies this fraction of the splash canvas's edge, matching
 * sovereign/scripts/generate-splash.ts's MARK_RATIO. */
const SPLASH_MARK_RATIO = 0.22;

/**
 * The "S" glyph path only, in its native 64x64 viewBox coordinate space —
 * copied from sovereign/runtime/public/icons/favicon.svg. Keeping this
 * inline (rather than reading across repos) means this script has no
 * dependency on the sovereign monorepo being checked out alongside this one;
 * re-sync by hand if the source mark ever changes.
 */
const GLYPH_PATH =
  'M32.83677685950413 54.5Q25.95661157024793 54.5 21.52479338842975 50.904958677685954Q17.092975206611566 47.3099173553719 16.163223140495866 41.35950413223141L23.973140495867767 39.438016528925615Q24.654958677685947 43.404958677685954 27.103305785123965 45.388429752066116Q29.551652892561982 47.37190082644628 33.146694214876035 47.37190082644628Q35.06818181818181 47.37190082644628 36.58677685950413 46.72107438016529Q38.10537190082644 46.070247933884296 39.00413223140495 44.7995867768595Q39.902892561983464 43.52892561983471 39.902892561983464 41.79338842975207Q39.902892561983464 40.18181818181818 38.97314049586777 39.00413223140495Q38.04338842975206 37.82644628099173 36.152892561983464 36.83471074380165Q34.26239669421487 35.84297520661157 31.411157024793386 34.789256198347104Q27.320247933884296 33.17768595041322 24.561983471074377 31.349173553719005Q21.80371900826446 29.520661157024794 20.440082644628095 27.103305785123965Q19.076446280991732 24.68595041322314 19.076446280991732 21.400826446280995Q19.076446280991732 17.929752066115704 20.780991735537185 15.264462809917354Q22.485537190082642 12.599173553719005 25.55371900826446 11.049586776859503Q28.62190082644628 9.5 32.71280991735537 9.5Q37.05165289256198 9.5 40.4297520661157 11.514462809917354Q43.80785123966942 13.528925619834709 45.853305785123965 17.49586776859504L39.46900826446281 21.400826446280995Q38.167355371900825 19.045454545454547 36.40082644628099 17.836776859504134Q34.63429752066115 16.62809917355372 32.46487603305785 16.62809917355372Q30.915289256198346 16.62809917355372 29.70661157024793 17.21694214876033Q28.497933884297517 17.805785123966942 27.785123966942145 18.828512396694215Q27.072314049586772 19.85123966942149 27.072314049586772 21.21487603305785Q27.072314049586772 22.640495867768596 27.909090909090907 23.75619834710744Q28.74586776859504 24.871900826446282 30.636363636363637 25.894628099173552Q32.52685950413223 26.917355371900825 35.56404958677686 28.03305785123967Q39.59297520661157 29.520661157024794 42.289256198347104 31.31818181818182Q44.98553719008264 33.11570247933884 46.41115702479338 35.502066115702476Q47.83677685950413 37.888429752066116 47.83677685950413 41.29752066115702Q47.83677685950413 45.14049586776859 45.946280991735534 48.146694214876035Q44.05578512396694 51.15289256198347 40.70867768595041 52.82644628099173Q37.361570247933884 54.5 32.83677685950413 54.5Z';

function fullMarkSvg(squareFill, glyphFill) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="14" fill="${squareFill}"/>
  <path d="${GLYPH_PATH}" fill="${glyphFill}"/>
</svg>`;
}

function glyphOnlySvg(glyphFill) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
  <path d="${GLYPH_PATH}" fill="${glyphFill}"/>
</svg>`;
}

async function writeIcon(outPath) {
  // Flat app-store icon: full mark, no transparency (flattening onto its own
  // square fill both satisfies "no alpha channel" and — since the flatten
  // colour matches the rect fill exactly — discards the rect's rounded
  // corners too, which is correct: App Store/Play submissions want a
  // full-bleed square, not pre-rounded corners; each OS applies its own mask.
  await sharp(Buffer.from(fullMarkSvg(MARK_SQUARE, MARK_LETTER)), { density: 384 })
    .resize(1024, 1024, { fit: 'contain' })
    .flatten({ background: MARK_SQUARE })
    .png()
    .toFile(outPath);
  console.log('wrote', outPath);
}

async function writeAdaptiveIconLayers(backgroundPath, foregroundPath) {
  await sharp({
    create: { width: 1024, height: 1024, channels: 4, background: MARK_SQUARE },
  })
    .png()
    .toFile(backgroundPath);
  console.log('wrote', backgroundPath);

  // Glyph only, transparent background, padded well inside Android's
  // adaptive-icon safe zone (~66% of canvas) so it survives circular/
  // squircle/rounded-square masking across launchers.
  const glyph = await sharp(Buffer.from(glyphOnlySvg(MARK_LETTER)), { density: 384 })
    .resize(620, 620, { fit: 'contain' })
    .png()
    .toBuffer();
  await sharp({
    create: { width: 1024, height: 1024, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  })
    .composite([{ input: glyph, gravity: 'center' }])
    .png()
    .toFile(foregroundPath);
  console.log('wrote', foregroundPath);
}

async function writeSplash(canvasSize, surfaceHex, squareFill, glyphFill, outPath) {
  const markSize = Math.round(canvasSize * SPLASH_MARK_RATIO);
  const mark = await sharp(Buffer.from(fullMarkSvg(squareFill, glyphFill)), { density: 384 })
    .resize(markSize, markSize)
    .png()
    .toBuffer();
  await sharp({
    create: { width: canvasSize, height: canvasSize, channels: 4, background: surfaceHex },
  })
    .composite([{ input: mark, gravity: 'center' }])
    .png()
    .toFile(outPath);
  console.log('wrote', outPath);
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true });

  await writeIcon(join(OUT_DIR, 'icon.png'));
  await writeAdaptiveIconLayers(
    join(OUT_DIR, 'icon-background.png'),
    join(OUT_DIR, 'icon-foreground.png'),
  );
  await writeSplash(2732, SURFACE_LIGHT, MARK_SQUARE, MARK_LETTER, join(OUT_DIR, 'splash.png'));
  // Dark theme: inverted mark (light square, near-black letter), matching
  // sovereign/scripts/generate-splash.ts's themedSvg() rationale.
  await writeSplash(2732, SURFACE_DARK, MARK_LETTER, MARK_SQUARE, join(OUT_DIR, 'splash-dark.png'));

  console.log(`\nDone. Master assets written to ${OUT_DIR}`);
  console.log('Next: npx capacitor-assets generate');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
