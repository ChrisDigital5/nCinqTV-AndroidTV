package app.ncinq.tv.data

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val API_BASE_URL = "https://tv.ncinq.app/"

interface NCinqApi {
    @GET("api/android/home")
    suspend fun home(): HomeFeed

    @GET("api/android/catalog/{type}")
    suspend fun catalog(
        @Path("type") type: String,
        @Query("category") category: String = "popular",
        @Query("page") page: Int = 1,
        @Query("genre") genre: Int? = null,
        @Query("network") network: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("year") year: Int? = null,
    ): CatalogPage

    @GET("api/android/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
    ): SearchResults

    @GET("api/android/details/{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
    ): MediaDetails

    @GET("api/android/season/{showId}/{season}")
    suspend fun season(
        @Path("showId") showId: Int,
        @Path("season") season: Int,
    ): SeasonDetails

    @POST("api/stream")
    suspend fun stream(@Body request: StreamRequest): StreamResponse

    @GET("api/android/update")
    suspend fun update(@Query("versionCode") versionCode: Int): UpdateInfo
}

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val versioned = if (request.method == "GET") {
                request.newBuilder().url(request.url.newBuilder().addQueryParameter("tvApi", "3").build()).build()
            } else request
            chain.proceed(versioned)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val service: NCinqApi = Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NCinqApi::class.java)
}

class CatalogRepository(private val api: NCinqApi = ApiClient.service) {
    private val vixsrcEpisodeResolver = VixsrcEpisodeResolver()
    suspend fun home() = api.home()
    suspend fun catalog(
        type: MediaType,
        category: String = "popular",
        page: Int = 1,
        genre: Int? = null,
        network: Int? = null,
        sort: String? = null,
        year: Int? = null,
    ) = api.catalog(type.wireName, category, page, genre, network, sort, year)
    suspend fun search(query: String, type: MediaType? = null, page: Int = 1) =
        api.search(query, type?.wireName, page)
    suspend fun details(type: MediaType, id: Int) = api.details(type.wireName, id)
    suspend fun season(showId: Int, season: Int) = api.season(showId, season)
    suspend fun update(versionCode: Int) = api.update(versionCode)

    suspend fun resolve(request: PlaybackRequest): StreamResult {
        if (request.requiresVerifiedVixsrcSource()) {
            vixsrcEpisodeResolver.resolve(request)?.let { return it }
        }
        val response = api.stream(request.toStreamRequest())
        return response.stream?.takeIf { response.success && it.url.isNotBlank() }
            ?: throw IllegalStateException(response.error ?: "No direct stream is available for this title.")
    }
}

object MediaProxy {
    fun streamUrl(stream: StreamResult): String {
        val builder = "${API_BASE_URL}api/cors-proxy".toHttpUrl().newBuilder()
            .addQueryParameter("url", stream.url)
        if (stream.headers.isNotEmpty()) {
            builder.addQueryParameter("headers", Gson().toJson(stream.headers))
        }
        stream.proxyToken?.takeIf { it.isNotBlank() }?.let {
            builder.addQueryParameter("token", it)
        }
        return builder.build().toString()
    }

    fun captionUrl(caption: Caption): String {
        val builder = "${API_BASE_URL}api/cors-proxy".toHttpUrl().newBuilder()
            .addQueryParameter("url", caption.url)
        if (caption.type.equals("srt", ignoreCase = true)) {
            builder.addQueryParameter("format", "vtt")
        }
        return builder.build().toString()
    }
}
