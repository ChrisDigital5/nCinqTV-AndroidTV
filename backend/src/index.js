const TMDB_BASE_URL = 'https://api.themoviedb.org/3';
const TMDB_IMAGE_BASE_URL = 'https://image.tmdb.org/t/p';
const JSON_HEADERS = {
  'content-type': 'application/json; charset=utf-8',
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET, OPTIONS',
  'access-control-allow-headers': 'content-type',
};

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...JSON_HEADERS, ...extraHeaders },
  });
}

function imageUrl(path, size) {
  return path ? `${TMDB_IMAGE_BASE_URL}/${size}${path}` : null;
}

function yearFrom(value) {
  return typeof value === 'string' && value.length >= 4 ? Number(value.slice(0, 4)) : null;
}

function normalizeMedia(item, fallbackType) {
  const type = item.media_type === 'movie' || item.media_type === 'tv'
    ? item.media_type
    : fallbackType;
  return {
    id: item.id,
    type,
    title: item.title || item.name || 'Untitled',
    overview: item.overview || '',
    posterUrl: imageUrl(item.poster_path, 'w500'),
    backdropUrl: imageUrl(item.backdrop_path, 'w1280'),
    rating: Number(item.vote_average || 0),
    year: yearFrom(item.release_date || item.first_air_date),
  };
}

async function tmdb(path, env, searchParams = {}, fetchFn = fetch) {
  if (!env.TMDB_API_KEY) throw new Error('TMDB_API_KEY is not configured');
  const url = new URL(`${TMDB_BASE_URL}${path}`);
  url.searchParams.set('api_key', env.TMDB_API_KEY);
  url.searchParams.set('language', 'en-US');
  for (const [key, value] of Object.entries(searchParams)) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value));
    }
  }

  const response = await fetchFn(url, {
    headers: { accept: 'application/json' },
    cf: { cacheEverything: true, cacheTtl: 1800 },
  });
  if (!response.ok) throw new Error(`TMDB request failed with ${response.status}`);
  return response.json();
}

async function homeFeed(env, fetchFn) {
  const requests = [
    ['/trending/movie/week', 'movie', 'Trending Movies'],
    ['/trending/tv/week', 'tv', 'Trending Shows'],
    ['/movie/popular', 'movie', 'Popular Movies'],
    ['/tv/popular', 'tv', 'Popular Shows'],
    ['/movie/top_rated', 'movie', 'Top Rated Movies'],
    ['/tv/top_rated', 'tv', 'Top Rated Shows'],
  ];
  const payloads = await Promise.all(requests.map(([path]) => tmdb(path, env, {}, fetchFn)));
  const rows = payloads.map((payload, index) => ({
    title: requests[index][2],
    items: (payload.results || []).slice(0, 20).map(item => normalizeMedia(item, requests[index][1])),
  }));
  return {
    featured: rows[0]?.items.find(item => item.backdropUrl) || rows[0]?.items[0] || null,
    rows,
  };
}

async function catalog(type, url, env, fetchFn) {
  if (type !== 'movie' && type !== 'tv') return json({ error: 'Invalid media type' }, 400);
  const requestedCategory = url.searchParams.get('category') || 'popular';
  const allowedCategories = type === 'movie'
    ? new Set(['popular', 'top_rated', 'now_playing', 'upcoming'])
    : new Set(['popular', 'top_rated', 'airing_today', 'on_the_air']);
  const category = allowedCategories.has(requestedCategory) ? requestedCategory : 'popular';
  const page = Math.max(1, Number(url.searchParams.get('page') || 1));
  const payload = await tmdb(`/${type}/${category}`, env, { page }, fetchFn);
  return json({
    page: payload.page || page,
    totalPages: payload.total_pages || 1,
    items: (payload.results || []).map(item => normalizeMedia(item, type)),
  }, 200, { 'cache-control': 'public, max-age=300' });
}

async function search(url, env, fetchFn) {
  const query = (url.searchParams.get('q') || '').trim();
  if (query.length < 2) return json({ items: [] });
  const payload = await tmdb('/search/multi', env, {
    query,
    include_adult: false,
    page: Math.max(1, Number(url.searchParams.get('page') || 1)),
  }, fetchFn);
  const items = (payload.results || [])
    .filter(item => item.media_type === 'movie' || item.media_type === 'tv')
    .map(item => normalizeMedia(item, item.media_type));
  return json({ items });
}

async function details(type, id, env, fetchFn) {
  if (type !== 'movie' && type !== 'tv') return json({ error: 'Invalid media type' }, 400);
  const payload = await tmdb(`/${type}/${id}`, env, { append_to_response: 'external_ids' }, fetchFn);
  const media = normalizeMedia(payload, type);
  return json({
    ...media,
    imdbId: payload.external_ids?.imdb_id || payload.imdb_id || null,
    runtimeMinutes: payload.runtime || payload.episode_run_time?.[0] || null,
    genres: (payload.genres || []).map(genre => genre.name),
    seasonCount: payload.number_of_seasons || 0,
    seasons: (payload.seasons || [])
      .filter(season => season.season_number > 0)
      .map(season => ({
        number: season.season_number,
        name: season.name || `Season ${season.season_number}`,
        episodeCount: season.episode_count || 0,
        airDate: season.air_date || null,
        posterUrl: imageUrl(season.poster_path, 'w500'),
      })),
  }, 200, { 'cache-control': 'public, max-age=900' });
}

async function season(showId, seasonNumber, env, fetchFn) {
  const payload = await tmdb(`/tv/${showId}/season/${seasonNumber}`, env, {}, fetchFn);
  return json({
    showId: Number(showId),
    seasonNumber: Number(seasonNumber),
    name: payload.name || `Season ${seasonNumber}`,
    episodes: (payload.episodes || []).map(episode => ({
      number: episode.episode_number,
      name: episode.name || `Episode ${episode.episode_number}`,
      overview: episode.overview || '',
      runtimeMinutes: episode.runtime || null,
      airDate: episode.air_date || null,
      stillUrl: imageUrl(episode.still_path, 'w780'),
    })),
  }, 200, { 'cache-control': 'public, max-age=900' });
}

function versionCodeFromTag(tagName) {
  const match = String(tagName || '').match(/v?(\d+)\.(\d+)\.(\d+)/i);
  if (!match) return 0;
  return Number(match[1]) * 10000 + Number(match[2]) * 100 + Number(match[3]);
}

async function latestRelease(env, fetchFn) {
  const response = await fetchFn(`https://api.github.com/repos/${env.GITHUB_REPO}/releases/latest`, {
    headers: {
      accept: 'application/vnd.github+json',
      'user-agent': 'ncinqtv-android-updater',
      'x-github-api-version': '2026-03-10',
    },
    cf: { cacheEverything: true, cacheTtl: 300 },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`GitHub release request failed with ${response.status}`);
  return response.json();
}

function apkAsset(release) {
  return release?.assets?.find(asset => asset.name?.toLowerCase().endsWith('.apk')) || null;
}

async function updateInfo(requestUrl, env, fetchFn) {
  const currentCode = Number(requestUrl.searchParams.get('versionCode') || 0);
  const release = await latestRelease(env, fetchFn);
  const asset = apkAsset(release);
  if (!release || !asset) {
    return json({ available: false, currentVersionCode: currentCode });
  }
  const versionCode = versionCodeFromTag(release.tag_name);
  return json({
    available: versionCode > currentCode,
    versionCode,
    versionName: String(release.tag_name).replace(/^v/i, ''),
    releaseNotes: release.body || '',
    publishedAt: release.published_at || null,
    downloadUrl: new URL('/dl', requestUrl).toString(),
  }, 200, { 'cache-control': 'no-store' });
}

export async function handleRequest(request, env, fetchFn = fetch) {
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: JSON_HEADERS });

  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const isDownloadHead = request.method === 'HEAD' && path === '/dl';
  if (request.method !== 'GET' && !isDownloadHead) return json({ error: 'Method not allowed' }, 405);

  try {
    if (path === '/dl') {
      const release = await latestRelease(env, fetchFn);
      const asset = apkAsset(release);
      if (!asset) return json({ error: 'The Android TV APK has not been published yet.' }, 404);
      return Response.redirect(asset.browser_download_url, 302);
    }
    if (path === '/api/android/home') return json(await homeFeed(env, fetchFn), 200, { 'cache-control': 'public, max-age=300' });
    if (path === '/api/android/search') return await search(url, env, fetchFn);
    if (path === '/api/android/update') return await updateInfo(url, env, fetchFn);

    const catalogMatch = path.match(/^\/api\/android\/catalog\/(movie|tv)$/);
    if (catalogMatch) return await catalog(catalogMatch[1], url, env, fetchFn);

    const detailsMatch = path.match(/^\/api\/android\/details\/(movie|tv)\/(\d+)$/);
    if (detailsMatch) return await details(detailsMatch[1], detailsMatch[2], env, fetchFn);

    const seasonMatch = path.match(/^\/api\/android\/season\/(\d+)\/(\d+)$/);
    if (seasonMatch) return await season(seasonMatch[1], seasonMatch[2], env, fetchFn);

    return json({ error: 'Not found' }, 404);
  } catch (error) {
    console.error('[Android API]', error);
    return json({ error: error instanceof Error ? error.message : 'Unexpected server error' }, 502);
  }
}

export { normalizeMedia, versionCodeFromTag };

export default {
  fetch(request, env) {
    return handleRequest(request, env);
  },
};
