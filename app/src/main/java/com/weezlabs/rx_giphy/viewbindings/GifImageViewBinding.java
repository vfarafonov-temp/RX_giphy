package com.weezlabs.rx_giphy.viewbindings;

import org.robobinding.annotation.ViewBinding;
import org.robobinding.customviewbinding.CustomViewBinding;
import org.robobinding.viewattribute.property.OneWayPropertyViewAttribute;
import org.robobinding.viewbinding.BindingAttributeMappings;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

/**
 * Binding for "source" attribute for GifImageView
 */
@ViewBinding
public class GifImageViewBinding extends CustomViewBinding<GifImageView> {

	@Override
	public void mapBindingAttributes(BindingAttributeMappings<GifImageView> mappings) {
		mappings.mapOneWayProperty(GifSourceAttribute.class, "source");
	}

	public static class GifSourceAttribute implements OneWayPropertyViewAttribute<GifImageView, GifDrawable> {
		@Override
		public void updateView(GifImageView view, GifDrawable newValue) {
			view.setImageDrawable(newValue);
		}
	}
}


