package com.lagu.songfy.data.remote

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    data object Loading : NetworkResult<Nothing>()
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(apiCall())
    } catch (e: retrofit2.HttpException) {
        NetworkResult.Error(
            message = e.response()?.errorBody()?.string() ?: e.message(),
            code = e.code()
        )
    } catch (e: java.io.IOException) {
        NetworkResult.Error(message = "Tidak ada koneksi internet")
    } catch (e: Exception) {
        NetworkResult.Error(message = e.localizedMessage ?: "Terjadi kesalahan")
    }
}