package com.weezlabs.rx_giphy.Network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.weezlabs.rx_giphy.Gif;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;

/**
 * Created by Admin on 17.10.2015.
 */
public class RetrofitProvider {
	private static final String WEB_BASE = "http://api.giphy.com/v1/";
	public static final String API_KEY_QUERY = "?api_key=dc6zaTOxFJmzC";

	private static volatile Retrofit retrofit_;

	public static Retrofit getInstance() {
		if (retrofit_ == null) {
			synchronized (RetrofitProvider.class) {
				if (retrofit_ == null) {
					Gson gson = new GsonBuilder()
							.registerTypeAdapter(Gif.class, new Gif.GifDeserializer())
							.create();

					retrofit_ = new Retrofit.Builder()
							.baseUrl(WEB_BASE)
							.addConverterFactory(GsonConverterFactory.create(gson))
							.addCallAdapterFactory(RxJavaCallAdapterFactory.create())
							.build();
				}
			}
		}
		return retrofit_;
	}

	public static GiphyService getGiphyService() {
		return getInstance().create(GiphyService.class);
	}
}
