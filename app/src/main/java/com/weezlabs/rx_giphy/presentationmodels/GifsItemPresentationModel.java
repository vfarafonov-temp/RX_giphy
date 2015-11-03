package com.weezlabs.rx_giphy.presentationmodels;

import com.weezlabs.rx_giphy.Gif;

import org.robobinding.itempresentationmodel.ItemContext;
import org.robobinding.itempresentationmodel.ItemPresentationModel;

import rx.Observable;
import rx.Subscriber;

/**
 * Presentation model for item inside list of gifs
 */
public class GifsItemPresentationModel implements ItemPresentationModel<Gif> {
	private Gif value_;
	private int currentPosition_;
	private GifClicksListener gifClicksListener_;

	@Override
	public void updateData(Gif bean, ItemContext itemContext) {
		value_ = bean;
		currentPosition_ = itemContext.getPosition();
	}

	public String getValue() {
		if (value_ != null) {
			return currentPosition_ + ". " + value_.getId();
		} else {
			return "";
		}
	}

	public void onItemClicked() {
		if (gifClicksListener_ != null) {
			gifClicksListener_.onGifClicked(currentPosition_);
		}
	}

	public interface GifClicksListener {
		void onGifClicked(int position);
	}

	public void setGifClicksListener(GifClicksListener gifClicksListener) {
		gifClicksListener_ = gifClicksListener;
	}

	/**
	 * Returns observable which emit clicked item position on clicks
	 */
	public Observable<Integer> getClickObservable() {
		return Observable.create(new Observable.OnSubscribe<Integer>() {
			@Override
			public void call(final Subscriber<? super Integer> subscriber) {
				GifClicksListener listener = new GifClicksListener() {
					@Override
					public void onGifClicked(int position) {
						if (subscriber.isUnsubscribed()) {
							setGifClicksListener(null);
						} else {
							subscriber.onNext(position);
						}
					}
				};
				setGifClicksListener(listener);
			}
		});
	}
}
