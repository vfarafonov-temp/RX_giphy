package com.weezlabs.rx_giphy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.jakewharton.rxbinding.support.v7.widget.RxSearchView;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okio.BufferedSource;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class SearchActivity extends AppCompatActivity {

	private GifImageView gifView_;
	private Subscription subscription_;

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

		if (subscription_ == null) {
			MenuItem searchItem = menu.findItem(R.id.action_search);
			SearchView searchView = (SearchView) searchItem.getActionView();

			// Creating ibservable to return GifDrawable from search query
			Observable<GifDrawable> gifObservable = RxSearchView.queryTextChanges(searchView)
					.filter(new Func1<CharSequence, Boolean>() {
						@Override
						public Boolean call(CharSequence charSequence) {
							return charSequence.length() > 2;
						}
					})
					.debounce(500, TimeUnit.MILLISECONDS)
					.flatMap(new Func1<CharSequence, Observable<BaseResponse<List<Gif>>>>() {
						@Override
						public Observable<BaseResponse<List<Gif>>> call(CharSequence charSequence) {
							return RetrofitProvider.getGiphyService().findGifsRx(charSequence.toString())
									.subscribeOn(Schedulers.io())
									.observeOn(AndroidSchedulers.mainThread());
						}
					})
					.flatMap(new Func1<BaseResponse<List<Gif>>, Observable<GifDrawable>>() {
						@Override
						public Observable<GifDrawable> call(final BaseResponse<List<Gif>> listBaseResponse) {
							return Observable.create(new Observable.OnSubscribe<GifDrawable>() {
								@Override
								public void call(Subscriber<? super GifDrawable> subscriber) {
									if (listBaseResponse.getData().size() == 0) {
										subscriber.onError(new IOException("Nothing found"));
									}
									try {
										OkHttpClient okHttpClient = new OkHttpClient();
										okHttpClient.setConnectTimeout(10000, TimeUnit.MILLISECONDS);
										com.squareup.okhttp.Response response = okHttpClient.newCall(new Request.Builder().url(listBaseResponse.getData().get(0).getUrl()).build()).execute();
										if (!response.isSuccessful()) {
											subscriber.onError(new IOException("Request failed: " + response.code()));
										}
										BufferedSource source = response.body().source();
										GifDrawable gifDrawable = new GifDrawable(source.readByteArray());
										subscriber.onNext(gifDrawable);
										subscriber.onCompleted();
									} catch (IOException e) {
										e.printStackTrace();
										subscriber.onError(new IOException("Fail requesting: " + (e instanceof SocketTimeoutException ? "check your connection" : e.getMessage())));
									}
								}
							}).subscribeOn(Schedulers.io());
						}
					})
					.subscribeOn(AndroidSchedulers.mainThread())
					.observeOn(AndroidSchedulers.mainThread());

			// Subscribe to search observable
			subscription_ = gifObservable.subscribe(new Observer<GifDrawable>() {
				@Override
				public void onCompleted() {
				}

				@Override
				public void onError(Throwable e) {
					Toast.makeText(getBaseContext(), "Error getting gif: " + e.getMessage(), Toast.LENGTH_LONG).show();
				}

				@Override
				public void onNext(GifDrawable gifDrawable) {
					gifView_.setImageDrawable(gifDrawable);
					gifDrawable.start();
				}
			});
		}
		return true;
	}

	@Override
	protected void onDestroy() {
		if (subscription_ != null) {
			subscription_.unsubscribe();
		}
		super.onDestroy();
	}
}
