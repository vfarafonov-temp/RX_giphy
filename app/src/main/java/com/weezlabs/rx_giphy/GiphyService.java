package com.weezlabs.rx_giphy;

import java.util.List;

import retrofit.Call;
import retrofit.http.GET;
import retrofit.http.Query;
import rx.Observable;

/**
 * Created by Admin on 17.10.2015.
 */
public interface GiphyService {

	@GET("gifs/search" + RetrofitProvider.API_KEY_QUERY)
	Call<BaseResponse<List<Gif>>> findGiphs(@Query("q") String searchQuery);

	@GET("gifs/search" + RetrofitProvider.API_KEY_QUERY)
	Observable<BaseResponse<List<Gif>>> findGifsRx(@Query("q") String searchQuery);
}
