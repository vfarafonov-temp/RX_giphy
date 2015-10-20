package com.weezlabs.rx_giphy;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.jakewharton.rxbinding.support.v7.widget.RxSearchView;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.weezlabs.rx_giphy.Network.BaseResponse;
import com.weezlabs.rx_giphy.Network.RetrofitProvider;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okio.BufferedSource;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class SearchActivity extends AppCompatActivity {

	private GifImageView gifView_;
	private CompositeSubscription subscription_;
	private ProgressBar networkProgressBar_;
	private List<Gif> gifsList_ = new ArrayList<>();
	private GifsListAdapter gifsListAdapter_;
	private Observable<String> gifClickObservable_;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		gifView_ = (GifImageView) findViewById(R.id.gif_view);
		networkProgressBar_ = (ProgressBar) findViewById(R.id.pb_network);
		subscription_ = new CompositeSubscription();

		RecyclerView gifsList = (RecyclerView) findViewById(R.id.rv_search_results);
		gifsList.setLayoutManager(new LinearLayoutManager(this));
		gifsListAdapter_ = new GifsListAdapter(gifsList_);
		gifsList.setAdapter(gifsListAdapter_);

		// Get clicks observable
		ConnectableObservable<Integer> clickObservable = gifsListAdapter_.getClickObservable().publish();
		clickObservable.connect();

		// Subscribe to handle clicks event
		subscription_.add(clickObservable.subscribe(new Action1<Integer>() {
			@Override
			public void call(Integer integer) {
				gifView_.setImageDrawable(null);
				networkProgressBar_.setVisibility(View.VISIBLE);
			}
		}));

		// Prepare observable which emits gif's url after click
		gifClickObservable_ = clickObservable.flatMap(new Func1<Integer, Observable<String>>() {
			@Override
			public Observable<String> call(final Integer integer) {
				return Observable.create(new Observable.OnSubscribe<String>() {
					@Override
					public void call(Subscriber<? super String> subscriber) {
						subscriber.onNext(gifsList_.get(integer).getUrl());
					}
				});
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_search_activity, menu);

		MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView = (SearchView) searchItem.getActionView();
		createSearchSubscriptions(searchView);

		return true;
	}

	private void createSearchSubscriptions(SearchView searchView) {
		// Creating observable from SearchView
		Observable<CharSequence> searchObservable = RxSearchView.queryTextChanges(searchView)
				.filter(new Func1<CharSequence, Boolean>() {
					// Excepting only sequence with length more than 2
					@Override
					public Boolean call(CharSequence charSequence) {
						return charSequence.length() > 2;
					}
				})
						// Waiting for 500 ms in case if typing not finished
				.debounce(500, TimeUnit.MILLISECONDS);

		// Creating connectable observable so multiple subscribers can subscribe. (Making observable hot)
		ConnectableObservable<CharSequence> connectableSearchObservable = searchObservable.publish();
		connectableSearchObservable.connect();

		// Subscribing to searchObservable to handle search process start
		subscription_.add(connectableSearchObservable.observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<CharSequence>() {
			@Override
			public void call(CharSequence charSequence) {
				networkProgressBar_.setVisibility(View.VISIBLE);
			}
		}));

		// Create observable for getting list of gifs
		Observable<BaseResponse<List<Gif>>> gifsResponseObservable = connectableSearchObservable
				.flatMap(new Func1<CharSequence, Observable<BaseResponse<List<Gif>>>>() {
					// Downloading gif list
					@Override
					public Observable<BaseResponse<List<Gif>>> call(CharSequence charSequence) {
						return RetrofitProvider.getGiphyService().findGifsRx(charSequence.toString())
								.subscribeOn(Schedulers.io())
								.observeOn(AndroidSchedulers.mainThread());
					}
				});

		// Subscribing to gifs list observable to update RecyclerView
		subscription_.add(gifsResponseObservable.subscribe(new Action1<BaseResponse<List<Gif>>>() {
			@Override
			public void call(BaseResponse<List<Gif>> listBaseResponse) {
				gifsList_.clear();
				gifsList_.addAll(listBaseResponse.getData());
				gifsListAdapter_.notifyDataSetChanged();
			}
		}));

		// Creating observable to return GifDrawable from search query
		Observable<GifDrawable> gifObservable =
				gifsResponseObservable
						.flatMap(new Func1<BaseResponse<List<Gif>>, Observable<String>>() {
							// Get url
							@Override
							public Observable<String> call(final BaseResponse<List<Gif>> listBaseResponse) {
								// Get first item url
								return Observable.create(new Observable.OnSubscribe<String>() {
									@Override
									public void call(Subscriber<? super String> subscriber) {
										if (listBaseResponse.getData().size() == 0) {
											// Gif not found
											subscriber.onNext(null);
										} else {
											subscriber.onNext(listBaseResponse.getData().get(0).getUrl());
										}
									}
								});
							}
						})
								// Merging with observable from clicking on list
						.mergeWith(gifClickObservable_)
						.flatMap(new Func1<String, Observable<GifDrawable>>() {
							// Gets GifDrawable from url
							@Override
							public Observable<GifDrawable> call(final String url) {
								return Observable.create(new Observable.OnSubscribe<GifDrawable>() {
									@Override
									public void call(Subscriber<? super GifDrawable> subscriber) {
										if (url == null) {
											subscriber.onError(new IOException("Nothing found"));
											return;
										}
										try {
											// Downloading gif
											OkHttpClient okHttpClient = new OkHttpClient();
											okHttpClient.setConnectTimeout(10000, TimeUnit.MILLISECONDS);
											Response response = okHttpClient.newCall(new Request.Builder().url(url).build()).execute();
											if (!response.isSuccessful()) {
												subscriber.onError(new IOException("Request failed: " + response.code()));
												return;
											}
											BufferedSource source = response.body().source();
											GifDrawable gifDrawable = new GifDrawable(source.readByteArray());
											subscriber.onNext(gifDrawable);
											subscriber.onCompleted();
										} catch (IOException e) {
											e.printStackTrace();
											subscriber.onError(new IOException(
													// SocketTimeoutException does not have message so need to add it manually
													"Fail requesting: " + (e instanceof SocketTimeoutException ? "check your connection" : e.getMessage())
											));
										}
									}
								})
										.lift(new OperatorSupressError<GifDrawable>(new Action1<Throwable>() {
											@Override
											public void call(final Throwable throwable) {
												// Displaying error toast. Actually can handle different errors differently
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														gifView_.setImageDrawable(null);
														networkProgressBar_.setVisibility(View.GONE);
														Toast.makeText(getBaseContext(), "Error getting gif: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
													}
												});
											}
										}))
										.subscribeOn(Schedulers.io());
							}
						})
						.subscribeOn(AndroidSchedulers.mainThread())
						.observeOn(AndroidSchedulers.mainThread());

		// Subscribe to gifObservable
		subscription_.add(gifObservable.subscribe(new Action1<GifDrawable>() {
			@Override
			public void call(GifDrawable gifDrawable) {
				networkProgressBar_.setVisibility(View.GONE);
				gifView_.setImageDrawable(gifDrawable);
				gifDrawable.start();
			}
		}));
	}

	@Override
	protected void onDestroy() {
		if (subscription_ != null) {
			subscription_.unsubscribe();
		}
		super.onDestroy();
	}
}
