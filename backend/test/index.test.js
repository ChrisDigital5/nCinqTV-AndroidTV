import assert from 'node:assert/strict';
import test from 'node:test';
import { handleRequest, normalizeMedia, versionCodeFromTag } from '../src/index.js';

const env = { TMDB_API_KEY: 'test-key', GITHUB_REPO: 'ChrisDigital5/nCinqTV-AndroidTV' };

test('normalizes movie and TV metadata for the client', () => {
  assert.deepEqual(normalizeMedia({
    id: 42,
    title: 'Movie',
    poster_path: '/poster.jpg',
    backdrop_path: '/backdrop.jpg',
    release_date: '2026-01-02',
    vote_average: 8.25,
  }, 'movie'), {
    id: 42,
    type: 'movie',
    title: 'Movie',
    overview: '',
    posterUrl: 'https://image.tmdb.org/t/p/w500/poster.jpg',
    backdropUrl: 'https://image.tmdb.org/t/p/w1280/backdrop.jpg',
    rating: 8.25,
    year: 2026,
  });
});

test('catalog forwards pagination and filters to TMDB discovery', async () => {
  let requestedUrl;
  const response = await handleRequest(
    new Request('https://tv.ncinq.app/api/android/catalog/tv?page=3&genre=18&network=213&sort=vote_average.desc'),
    env,
    async url => {
      requestedUrl = new URL(url);
      return Response.json({ page: 3, total_pages: 7, results: [] });
    },
  );
  assert.equal(response.status, 200);
  assert.equal(requestedUrl.pathname, '/3/discover/tv');
  assert.equal(requestedUrl.searchParams.get('page'), '3');
  assert.equal(requestedUrl.searchParams.get('with_genres'), '18');
  assert.equal(requestedUrl.searchParams.get('with_networks'), '213');
});

test('search preserves exact media type and pagination', async () => {
  const response = await handleRequest(
    new Request('https://tv.ncinq.app/api/android/search?q=avatar&type=tv&page=2'),
    env,
    async url => {
      assert.equal(new URL(url).pathname, '/3/search/tv');
      return Response.json({ page: 2, total_pages: 4, results: [{ id: 82452, name: 'Avatar: The Last Airbender' }] });
    },
  );
  const payload = await response.json();
  assert.equal(payload.items[0].type, 'tv');
  assert.equal(payload.items[0].id, 82452);
  assert.equal(payload.totalPages, 4);
});

test('maps semantic tags to Android version codes', () => {
  assert.equal(versionCodeFromTag('v1.0.0'), 10000);
  assert.equal(versionCodeFromTag('v1.4.7'), 10407);
  assert.equal(versionCodeFromTag('invalid'), 0);
});

test('update endpoint reports and routes to the current APK', async () => {
  const fakeFetch = async url => {
    assert.match(String(url), /releases\/latest\?androidUpdater=3$/);
    return Response.json({
      tag_name: 'v1.1.0',
      body: 'Faster playback',
      published_at: '2026-07-15T00:00:00Z',
      assets: [{ name: 'ncinq-tv-v1.1.0.apk', browser_download_url: 'https://github.test/app.apk' }],
    });
  };

  const response = await handleRequest(
    new Request('https://tv.ncinq.app/api/android/update?versionCode=10000'),
    env,
    fakeFetch,
  );
  const payload = await response.json();
  assert.equal(payload.available, true);
  assert.equal(payload.versionCode, 10100);
  assert.equal(payload.downloadUrl, 'https://tv.ncinq.app/dl');
});

test('/dl redirects to the latest public release asset', async () => {
  const response = await handleRequest(
    new Request('https://tv.ncinq.app/dl'),
    env,
    async () => Response.json({
      tag_name: 'v1.0.0',
      assets: [{ name: 'ncinq-tv-v1.0.0.apk', browser_download_url: 'https://github.test/app.apk' }],
    }),
  );
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), 'https://github.test/app.apk');
});

test('/dl supports HEAD checks', async () => {
  const response = await handleRequest(
    new Request('https://tv.ncinq.app/dl', { method: 'HEAD' }),
    env,
    async () => Response.json({
      tag_name: 'v1.0.1',
      assets: [{ name: 'ncinq-tv-v1.0.1.apk', browser_download_url: 'https://github.test/app.apk' }],
    }),
  );
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), 'https://github.test/app.apk');
});
