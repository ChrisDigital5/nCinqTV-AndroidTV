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

const NETWORK_PROVIDER_IDS = {
  213: 8,
  1024: 9,
  453: 15,
  2739: 337,
  2552: 350,
  49: 384,
  4330: 531,
  3353: 386,
};

const GENRES = {
  movie: [
    { id: 28, name: 'Action' }, { id: 12, name: 'Adventure' },
    { id: 16, name: 'Animation' }, { id: 35, name: 'Comedy' },
    { id: 80, name: 'Crime' }, { id: 99, name: 'Documentary' },
    { id: 18, name: 'Drama' }, { id: 14, name: 'Fantasy' },
    { id: 27, name: 'Horror' }, { id: 878, name: 'Sci-Fi' },
    { id: 53, name: 'Thriller' },
  ],
  tv: [
    { id: 10759, name: 'Action & Adventure' }, { id: 16, name: 'Animation' },
    { id: 35, name: 'Comedy' }, { id: 80, name: 'Crime' },
    { id: 99, name: 'Documentary' }, { id: 18, name: 'Drama' },
    { id: 10751, name: 'Family' }, { id: 9648, name: 'Mystery' },
    { id: 10765, name: 'Sci-Fi & Fantasy' },
  ],
};

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
    networks: [
      { id: 213, name: 'Netflix', logoUrl: imageUrl('/wwemzKWzjKYJFfCeiB57q3r4Bcm.png', 'w500') },
      { id: 1024, name: 'Prime Video', logoUrl: imageUrl('/w7HfLNm9CWwRmAMU58udl2L7We7.png', 'w500') },
      { id: 453, name: 'Hulu', logoUrl: imageUrl('/pqUTCleNUiTLAVlelGxUgWn1ELh.png', 'w500') },
      { id: 2739, name: 'Disney+', logoUrl: imageUrl('/1edZOYAfoyZyZ3rklNSiUpXX30Q.png', 'w500') },
      { id: 2552, name: 'Apple TV+', logoUrl: imageUrl('/bngHRFi794mnMq34gfVcm9nDxN1.png', 'w500') },
      { id: 49, name: 'HBO', logoUrl: imageUrl('/tuomPhY2UtuPTqqFnKMVHvSb724.png', 'w500') },
    ],
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
  const genre = url.searchParams.get('genre') || '';
  const network = url.searchParams.get('network') || '';
  const sortBy = url.searchParams.get('sort') || '';
  const year = url.searchParams.get('year') || '';
  const useDiscover = Boolean(genre || network || sortBy || year);
  const params = { page };
  if (useDiscover) {
    params.sort_by = sortBy || 'popularity.desc';
    if (genre) params.with_genres = genre;
    if (year) params[type === 'movie' ? 'primary_release_year' : 'first_air_date_year'] = year;
    if (network) {
      if (type === 'tv') params.with_networks = network;
      else if (NETWORK_PROVIDER_IDS[network]) {
        params.with_watch_providers = NETWORK_PROVIDER_IDS[network];
        params.watch_region = 'US';
      }
    }
  }
  const payload = await tmdb(useDiscover ? `/discover/${type}` : `/${type}/${category}`, env, params, fetchFn);
  return json({
    page: payload.page || page,
    totalPages: payload.total_pages || 1,
    items: (payload.results || []).map(item => normalizeMedia(item, type)),
    genres: GENRES[type],
  }, 200, { 'cache-control': 'public, max-age=300' });
}

async function search(url, env, fetchFn) {
  const query = (url.searchParams.get('q') || '').trim();
  if (query.length < 2) return json({ items: [] });
  const requestedType = url.searchParams.get('type');
  const type = requestedType === 'movie' || requestedType === 'tv' ? requestedType : null;
  const page = Math.max(1, Number(url.searchParams.get('page') || 1));
  const payload = await tmdb(type ? `/search/${type}` : '/search/multi', env, {
    query,
    include_adult: false,
    page,
  }, fetchFn);
  const items = (payload.results || [])
    .filter(item => type || item.media_type === 'movie' || item.media_type === 'tv')
    .map(item => normalizeMedia(item, type || item.media_type));
  return json({ page: payload.page || page, totalPages: payload.total_pages || 1, items });
}

async function details(type, id, env, fetchFn) {
  if (type !== 'movie' && type !== 'tv') return json({ error: 'Invalid media type' }, 400);
  const payload = await tmdb(`/${type}/${id}`, env, {
    append_to_response: type === 'tv'
      ? 'external_ids,credits,recommendations,content_ratings,videos'
      : 'external_ids,credits,recommendations,release_dates,videos',
  }, fetchFn);
  const media = normalizeMedia(payload, type);
  const trailer = (payload.videos?.results || []).find(video =>
    video.site === 'YouTube' && video.type === 'Trailer' && video.official
  ) || (payload.videos?.results || []).find(video => video.site === 'YouTube' && video.type === 'Trailer');
  const certification = type === 'tv'
    ? payload.content_ratings?.results?.find(item => item.iso_3166_1 === 'US')?.rating
    : payload.release_dates?.results?.find(item => item.iso_3166_1 === 'US')?.release_dates
      ?.find(item => item.certification)?.certification;
  return json({
    ...media,
    imdbId: payload.external_ids?.imdb_id || payload.imdb_id || null,
    trailerUrl: trailer?.key ? `https://www.youtube.com/watch?v=${trailer.key}` : null,
    runtimeMinutes: payload.runtime || payload.episode_run_time?.[0] || null,
    tagline: payload.tagline || '',
    status: payload.status || '',
    contentRating: certification || null,
    originalLanguage: payload.original_language || null,
    countries: (payload.production_countries || payload.origin_country || []).map(country => country.name || country),
    genres: (payload.genres || []).map(genre => genre.name),
    networks: (payload.networks || []).map(network => ({
      id: network.id,
      name: network.name,
      logoUrl: imageUrl(network.logo_path, 'w185'),
    })),
    creators: (payload.created_by || payload.credits?.crew?.filter(person => person.job === 'Director') || [])
      .slice(0, 4).map(person => person.name),
    cast: (payload.credits?.cast || []).slice(0, 10).map(person => ({
      id: person.id,
      name: person.name,
      character: person.character || '',
      profileUrl: imageUrl(person.profile_path, 'w185'),
    })),
    recommendations: (payload.recommendations?.results || []).slice(0, 16)
      .map(item => normalizeMedia(item, type)),
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
  const response = await fetchFn(`https://api.github.com/repos/${env.GITHUB_REPO}/releases/latest?androidUpdater=5`, {
    headers: {
      accept: 'application/vnd.github+json',
      'user-agent': 'ncinqtv-android-updater',
      'x-github-api-version': '2026-03-10',
    },
    cf: { cacheEverything: true, cacheTtl: 30 },
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
