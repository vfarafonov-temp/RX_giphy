package com.weezlabs.rx_giphy;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jakewharton.rxbinding.view.RxView;

import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * Gifs adapter
 */
public class GifsListAdapter extends RecyclerView.Adapter<GifsListAdapter.GifsViewHolder> {
	private List<Gif> gifsList_;
	private GifClickedListener listener_;

	public GifsListAdapter(@NonNull List<Gif> gifsList) {
		this.gifsList_ = gifsList;
	}

	@Override
	public GifsListAdapter.GifsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.gifs_list_item, parent, false);
		return new GifsViewHolder(view);
	}

	@Override
	public void onBindViewHolder(GifsListAdapter.GifsViewHolder holder, int position) {
		holder.nameTextView.setText(gifsList_.get(position).getId());
	}

	@Override
	public int getItemCount() {
		return gifsList_.size();
	}

	public class GifsViewHolder extends RecyclerView.ViewHolder {
		TextView nameTextView;

		public GifsViewHolder(View itemView) {
			super(itemView);
			nameTextView = (TextView) itemView.findViewById(R.id.tv_gif_name);
			RxView.clicks(nameTextView).subscribe(new Action1<Object>() {
				@Override
				public void call(Object object) {
					if (listener_ != null) {
						listener_.onGifClicked(getAdapterPosition());
					}
				}
			});
		}
	}

	public interface GifClickedListener {
		void onGifClicked(int position);
	}

	private void setListener(GifClickedListener listener) {
		this.listener_ = listener;
	}

	/**
	 * Returns observable which emit clicked item position on clicks
	 */
	public Observable<Integer> getClickObservable() {
		return Observable.create(new Observable.OnSubscribe<Integer>() {
			@Override
			public void call(final Subscriber<? super Integer> subscriber) {
				GifClickedListener listener = new GifClickedListener() {
					@Override
					public void onGifClicked(int position) {
						if (subscriber.isUnsubscribed()) {
							setListener(null);
						} else {
							subscriber.onNext(position);
						}
					}
				};
				setListener(listener);
			}
		});
	}
}
