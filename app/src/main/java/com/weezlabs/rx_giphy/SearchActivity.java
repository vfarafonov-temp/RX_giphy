package com.weezlabs.rx_giphy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okio.BufferedSource;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import retrofit.Call;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class SearchActivity extends AppCompatActivity {

	private GifImageView gifView_;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		gifView_ = (GifImageView) findViewById(R.id.gif_view);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_search_activity, menu);
		MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView = (SearchView) searchItem.getActionView();
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				GiphyService giphyService = RetrofitProvider.getGiphyService();
				Call<BaseResponse<List<Gif>>> result = giphyService.findGiphs(query);
				result.enqueue(new Callback<BaseResponse<List<Gif>>>() {
					@Override
					public void onResponse(Response<BaseResponse<List<Gif>>> response, Retrofit retrofit) {
						List<Gif> data = response.body() != null ? response.body().getData() : (new ArrayList<Gif>());
						if (data.size() > 0) {
							OkHttpClient client = new OkHttpClient();
							Request request = new Request.Builder()
									.url(data.get(0).getUrl())
									.build();
							client.newCall(request).enqueue(new com.squareup.okhttp.Callback() {
								@Override
								public void onFailure(Request request, IOException e) {
								}

								@Override
								public void onResponse(final com.squareup.okhttp.Response response) throws IOException {
									BufferedSource source = response.body().source();
									final GifDrawable gifDrawable = new GifDrawable(source.readByteArray());
									runOnUiThread(new Runnable() {
										@Override
										public void run() {
											gifView_.setImageDrawable(gifDrawable);
											gifDrawable.start();
										}
									});
								}
							});
						}
					}

					@Override
					public void onFailure(Throwable t) {
					}
				});
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
		return true;
	}
}
