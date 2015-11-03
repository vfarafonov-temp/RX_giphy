package com.weezlabs.rx_giphy;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * Operator which skips calling subscriber's onError with call to {@link Action1}
 */
public class OperatorSupressError<T> implements Observable.Operator<T, T> {
	final Action1<Throwable> onError;

	/**
	 * Creating OperatorSupressError
	 *
	 * @param onError Action which will be called instead of calling subscriber's onError
	 */
	public OperatorSupressError(Action1<Throwable> onError) {
		this.onError = onError;
	}

	@Override
	public Subscriber<? super T> call(final Subscriber<? super T> subscriber) {
		return new Subscriber<T>(subscriber) {
			@Override
			public void onCompleted() {
				subscriber.onCompleted();
			}

			@Override
			public void onError(Throwable e) {
				onError.call(e);
			}

			@Override
			public void onNext(T t) {
				subscriber.onNext(t);
			}
		};
	}
}
