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

test('maps semantic tags to Android version codes', () => {
  assert.equal(versionCodeFromTag('v1.0.0'), 10000);
  assert.equal(versionCodeFromTag('v1.4.7'), 10407);
  assert.equal(versionCodeFromTag('invalid'), 0);
});

test('update endpoint reports and routes to the current APK', async () => {
  const fakeFetch = async url => {
    assert.match(String(url), /releases\/latest$/);
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
