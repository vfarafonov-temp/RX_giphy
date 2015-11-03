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
import android.widget.Toast;

import com.jakewharton.rxbinding.support.v7.widget.RecyclerViewScrollStateChangeEvent;
import com.jakewharton.rxbinding.support.v7.widget.RxRecyclerView;
import com.jakewharton.rxbinding.support.v7.widget.RxSearchView;
import com.weezlabs.rx_giphy.presentationmodels.SearchActivityPresentationModel;
import com.weezlabs.rx_giphy.viewbindings.GifImageViewBinding;

import org.robobinding.ViewBinder;
import org.robobinding.binder.BinderFactory;
import org.robobinding.binder.BinderFactoryBuilder;
import org.robobinding.customviewbinding.CustomViewBinding;
import org.robobinding.supportwidget.recyclerview.RecyclerViewBinding;

import pl.droidsonroids.gif.GifImageView;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subscriptions.CompositeSubscription;

public class SearchActivity extends AppCompatActivity {

	private CompositeSubscription subscription_;
	private RecyclerView gifsBoundRecyclerView_;
	private SearchActivityPresentationModel searchActivityPresentationModel_;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		subscription_ = new CompositeSubscription();

		// Create presentation model
		searchActivityPresentationModel_ = new SearchActivityPresentationModel(subscription_, new SearchActivityPresentationModel.CallbacksListener() {
			@Override
			public void onNextPageLoaded(int lastIndexBeforeInserting) {
				if (((LinearLayoutManager) gifsBoundRecyclerView_.getLayoutManager()).findLastCompletelyVisibleItemPosition() == lastIndexBeforeInserting) {
					gifsBoundRecyclerView_.scrollToPosition(lastIndexBeforeInserting + 1);
				}
			}

			@Override
			public void onNewQueryLoaded() {
				gifsBoundRecyclerView_.scrollToPosition(0);
			}

			@Override
			public void onGifLoadingFailed(final Throwable throwable) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getBaseContext(), "Error getting gif: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
					}
				});
			}
		});

		// Bind with RoboBinder
		BinderFactory binderFactory = new BinderFactoryBuilder()
				.add(CustomViewBinding.forView(RecyclerView.class, new RecyclerViewBinding()))
				.add(new GifImageViewBinding().extend(GifImageView.class))
				.build();

		ViewBinder viewBinder = binderFactory.createViewBinder(this);
		View view = viewBinder.inflateAndBind(R.layout.activity_search, searchActivityPresentationModel_);

		setContentView(view);

		gifsBoundRecyclerView_ = (RecyclerView) findViewById(R.id.rv_search_results);
		gifsBoundRecyclerView_.setLayoutManager(new LinearLayoutManager(this));

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		searchActivityPresentationModel_.subscribeToScrollObservable(
				RxRecyclerView.scrollStateChangeEvents(gifsBoundRecyclerView_)
						.filter(new Func1<RecyclerViewScrollStateChangeEvent, Boolean>() {
							// Filter emits only if we are at the end of list
							@Override
							public Boolean call(RecyclerViewScrollStateChangeEvent recyclerViewScrollStateChangeEvent) {
								return ((LinearLayoutManager) gifsBoundRecyclerView_.getLayoutManager()).findLastCompletelyVisibleItemPosition() == (gifsBoundRecyclerView_.getAdapter().getItemCount() - 1);
							}
						})
		);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_search_activity, menu);

		MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView_ = (SearchView) searchItem.getActionView();
		ConnectableObservable<CharSequence> queryObservable = RxSearchView.queryTextChanges(searchView_).publish();
		queryObservable.connect();
		searchActivityPresentationModel_.subscribeToSearchQuery(queryObservable);

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
