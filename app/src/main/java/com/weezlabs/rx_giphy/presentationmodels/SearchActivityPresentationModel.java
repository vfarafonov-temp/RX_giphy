package com.weezlabs.rx_giphy.presentationmodels;


import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.jakewharton.rxbinding.support.v7.widget.RecyclerViewScrollStateChangeEvent;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.weezlabs.rx_giphy.Gif;
import com.weezlabs.rx_giphy.Network.BaseResponse;
import com.weezlabs.rx_giphy.Network.RetrofitProvider;
import com.weezlabs.rx_giphy.OperatorSupressError;

import org.robobinding.annotation.ItemPresentationModel;
import org.robobinding.annotation.PresentationModel;
import org.robobinding.itempresentationmodel.ListObservable;
import org.robobinding.itempresentationmodel.ViewTypeSelectionContext;
import org.robobinding.presentationmodel.HasPresentationModelChangeSupport;
import org.robobinding.presentationmodel.PresentationModelChangeSupport;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okio.BufferedSource;
import pl.droidsonroids.gif.GifDrawable;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * Presentation model for SearchActivity
 */
@PresentationModel
public class SearchActivityPresentationModel implements HasPresentationModelChangeSupport {
	private static final int MIN_SEARCH_LENGTH = 3;
	private static final int CODE_NO_MORE_ITEMS_TO_GET = -1;
	private final PresentationModelChangeSupport changeSupport_;
	private final ListObservable<Gif> gifs_;
	private final CompositeSubscription subscription_;
	private GifsItemPresentationModel gifsItemPresenatationModel_;
	private GifDrawable gifToPlay;
	private int offset_;
	private int progressBarVisibility_ = View.GONE;
	private ConnectableObservable<String> gifClickObservable_;
	private CharSequence lastQuery_;
	private Observable<CharSequence> loadNextPageObservable_ = Observable.empty();
	private CallbacksListener callBacksListener_;

	public SearchActivityPresentationModel(CompositeSubscription subscription, CallbacksListener listener) {
		callBacksListener_ = listener;
		subscription_ = subscription;

		changeSupport_ = new PresentationModelChangeSupport(this);

		gifs_ = new ListObservable<>(new ArrayList<Gif>());
		gifsItemPresenatationModel_ = new GifsItemPresentationModel();

		gifClickObservable_ = getGifItemClickObservable().publish();
		gifClickObservable_.connect();

		// Create subscription for items clicks
		subscription_.add(gifClickObservable_.subscribe(new Action1<String>() {
			@Override
			public void call(String s) {
				updateGifToPlay(null);
				changeProgressBarVisibility(View.VISIBLE);
			}
		}));
	}

	@Override
	public PresentationModelChangeSupport getPresentationModelChangeSupport() {
		return changeSupport_;
	}

	@ItemPresentationModel(value = GifsItemPresentationModel.class,
			viewTypeSelector = "selectViewType", factoryMethod = "createItemPresentationModel")
	public ListObservable<Gif> getGifs() {
		return gifs_;
	}

	public int selectViewType(ViewTypeSelectionContext<String> context) {
		return context.getPosition() % context.getViewTypeCount();
	}

	public GifsItemPresentationModel createItemPresentationModel() {
		return gifsItemPresenatationModel_;
	}

	public void clear() {
		gifs_.clear();
	}

	public int getItemsCount() {
		return gifs_.size();
	}

	public void addAll(List<Gif> data) {
		gifs_.addAll(data);
	}

	public Observable<String> getGifItemClickObservable() {
		if (gifsItemPresenatationModel_ != null) {
			return gifsItemPresenatationModel_.getClickObservable().flatMap(new Func1<Integer, Observable<String>>() {
				@Override
				public Observable<String> call(Integer integer) {
					return Observable.just(gifs_.get(integer).getUrl());
				}
			});
		} else {
			return null;
		}
	}

	public GifDrawable getGifToPlay() {
		return gifToPlay;
	}

	public void updateGifToPlay(GifDrawable gifToPlay) {
		this.gifToPlay = gifToPlay;
		// TODO: refactor all calls to refreshPresentationModel() with firing certain events
		changeSupport_.refreshPresentationModel();
	}

	/**
	 * Subscribes for search query changes
	 */
	public void subscribeToSearchQuery(ConnectableObservable<CharSequence> queryObservable) {
		// Subscribing to text changes in SearchView
		subscription_.add(queryObservable.subscribe(new Action1<CharSequence>() {
			@Override
			public void call(CharSequence charSequence) {
				updateGifToPlay(null);
				offset_ = 0;
				lastQuery_ = charSequence;
			}
		}));

		// Creating observable for matching search query
		Observable<CharSequence> searchObservable = queryObservable
				.filter(new Func1<CharSequence, Boolean>() {
					// Excepting only sequence with length more than MIN_SEARCH_LENGTH
					@Override
					public Boolean call(CharSequence charSequence) {
						return charSequence.length() > MIN_SEARCH_LENGTH - 1;
					}
				})
						// Waiting for 500 ms in case if typing not finished
				.debounce(500, TimeUnit.MILLISECONDS);

		// Subscribing to searchObservable to handle search process start
		subscription_.add(searchObservable.observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<CharSequence>() {
			@Override
			public void call(CharSequence charSequence) {
				changeProgressBarVisibility(View.VISIBLE);
			}
		}));

		// Create observable for getting list of gifs
		Observable<BaseResponse<List<Gif>>> gifsResponseObservable = searchObservable
				// Merging with RecyclerView scroll observable
				.mergeWith(loadNextPageObservable_)
				.flatMap(new Func1<CharSequence, Observable<BaseResponse<List<Gif>>>>() {
					// Downloading gif list
					@Override
					public Observable<BaseResponse<List<Gif>>> call(CharSequence charSequence) {
						return RetrofitProvider.getGiphyService().findGifsRx(charSequence.toString(), offset_)
								.subscribeOn(Schedulers.io())
								.observeOn(AndroidSchedulers.mainThread());
					}
				});

		// Subscribing to gifs list observable to update RecyclerView
		subscription_.add(gifsResponseObservable.subscribe(new Action1<BaseResponse<List<Gif>>>() {
			@Override
			public void call(BaseResponse<List<Gif>> listBaseResponse) {
				BaseResponse.PaginationInfo paginationInfo = listBaseResponse.getPaginationInfo();
				offset_ = paginationInfo.getOffset() + paginationInfo.getCount();
				if (offset_ >= paginationInfo.getTotalCount()) {
					offset_ = CODE_NO_MORE_ITEMS_TO_GET;
				}
				if (paginationInfo.getOffset() == 0) {
					clear();
				}
				int lastIndex = gifs_.size() - 1;
				addAll(listBaseResponse.getData());
				if (lastIndex >= 0) {
					if (callBacksListener_ != null && listBaseResponse.getData().size() > 0) {
						callBacksListener_.onNextPageLoaded(lastIndex);
					}
				} else {
					if (callBacksListener_ != null && listBaseResponse.getData().size() > 0) {
						callBacksListener_.onNewQueryLoaded();
					}
				}
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
												new Handler(Looper.getMainLooper()).post(new Runnable() {
													@Override
													public void run() {
														updateGifToPlay(null);
														changeProgressBarVisibility(View.GONE);
														if (callBacksListener_ != null) {
															callBacksListener_.onGifLoadingFailed(throwable);
														}
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
		subscription_.add(gifObservable.subscribe(new Subscriber<GifDrawable>() {
			@Override
			public void onCompleted() {
				// Do nothing
			}

			@Override
			public void onError(Throwable e) {
				if (callBacksListener_ != null) {
					callBacksListener_.onGifLoadingFailed(e);
				}
			}

			@Override
			public void onNext(GifDrawable gifDrawable) {
				changeProgressBarVisibility(View.GONE);
				updateGifToPlay(gifDrawable);
				gifDrawable.start();
			}
		}));
	}

	private void changeProgressBarVisibility(int visibility) {
		progressBarVisibility_ = visibility;
		changeSupport_.refreshPresentationModel();
	}

	public int getProgressBarVisibility() {
		return progressBarVisibility_;
	}

	public void subscribeToScrollObservable(Observable<RecyclerViewScrollStateChangeEvent> scrollStateObservable) {
		loadNextPageObservable_ = scrollStateObservable.flatMap(new Func1<RecyclerViewScrollStateChangeEvent, Observable<CharSequence>>() {
			@Override
			public Observable<CharSequence> call(final RecyclerViewScrollStateChangeEvent recyclerViewScrollStateChangeEvent) {
				return Observable.create(new Observable.OnSubscribe<CharSequence>() {
					@Override
					public void call(Subscriber<? super CharSequence> subscriber) {
						if (recyclerViewScrollStateChangeEvent.newState() == RecyclerView.SCROLL_STATE_IDLE
								&& offset_ != CODE_NO_MORE_ITEMS_TO_GET
								&& lastQuery_.length() > MIN_SEARCH_LENGTH - 1) {
							// Emit search query if scrolled to the end and have more items
							subscriber.onNext(lastQuery_);
						}
					}
				});
			}
		});
	}

	/**
	 * Interface for presentation model callbacks
	 */
	public interface CallbacksListener {
		void onNextPageLoaded(int lastIndexBeforeInserting);

		void onNewQueryLoaded();

		void onGifLoadingFailed(Throwable throwable);
	}
}
